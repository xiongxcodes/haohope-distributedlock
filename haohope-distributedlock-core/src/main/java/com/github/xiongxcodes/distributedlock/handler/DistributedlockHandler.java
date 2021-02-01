package com.github.xiongxcodes.distributedlock.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.lang.NonNull;

import com.github.xiongxcodes.distributedlock.annotation.DistributedlockAnn;

import cn.hutool.core.convert.Convert;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
public class DistributedlockHandler {
    private RedissonClient redisson;

    public DistributedlockHandler(@NonNull RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Pointcut("@annotation(com.github.xiongxcodes.distributedlock.annotation.DistributedlockAnn)")
    public void distributedLock() {}

    /**
     * 切面环绕通知
     *
     * @param joinPoint
     * @return Object
     */
    @Around("distributedLock()")
    public Object around(ProceedingJoinPoint joinPoint) {
        log.info("进入RedisLock环绕通知...");
        Object obj = null;
        MethodSignature signature = (MethodSignature)joinPoint.getSignature();
        DistributedlockAnn apiDistributedLockAnn = signature.getMethod().getAnnotation(DistributedlockAnn.class);
        // 获取锁名称
        String lockName = getLockKey(joinPoint, apiDistributedLockAnn);
        if (StringUtils.isEmpty(lockName)) {
            return null;
        }
        // 获取超时时间
        int expireSeconds = apiDistributedLockAnn.expireSeconds();
        // 等待多久,n秒内获取不到锁，则直接返回
        int waitTime = apiDistributedLockAnn.waitTime();
        RLock rLock = redisson.getLock(lockName);
        Boolean success = false;
        try {
            success = rLock.tryLock(waitTime, expireSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            success = false;
        }
        if (success) {
            log.info("获取锁成功....");
            try {
                obj = joinPoint.proceed();
            } catch (Throwable throwable) {
                log.error("获取锁异常", throwable);
            } finally {
                // 释放锁
                rLock.unlock();
                log.info("成功释放锁...");
            }
        } else {
            log.error("获取锁失败", apiDistributedLockAnn.failMsg());
        }
        log.info("结束RedisLock环绕通知...");
        return obj;
    }

    @SneakyThrows
    private String getLockKey(ProceedingJoinPoint joinPoint, DistributedlockAnn distributedLock) {
        String lockKey = distributedLock.lockKey();
        if (StringUtils.isEmpty(lockKey)) {
            String[] attributeNames = distributedLock.attributeNames();
            // 目标方法内的所有参数值
            Object[] parameterValues = joinPoint.getArgs();
            // 数组为空代表锁整个方法
            if (ArrayUtils.isNotEmpty(attributeNames) || ArrayUtils.isNotEmpty(parameterValues)) {
                MethodSignature signature = (MethodSignature)joinPoint.getSignature();
                // 目标方法内的所有参数名
                String[] parameterNames = signature.getParameterNames();
                // 参数名称与参数值组成map
                Map<String, Object> parameterMap = new HashMap<>();
                for (int i = 0; i < parameterNames.length; i++) {
                    parameterMap.put(parameterNames[i], parameterValues[i]);
                }
                // 获取目标包名和类名
                String declaringTypeName = joinPoint.getSignature().getDeclaringTypeName();
                // 获取目标方法名
                String methodName = joinPoint.getSignature().getName();
                StringBuffer lockParamsBuffer = new StringBuffer();
                String[] attributes = null;
                String attribute = null;
                Object parameterValue = null;
                for (String attributeName : attributeNames) {
                    attributes = attributeName.split("\\.");
                    attribute = attributes[0];
                    if (!parameterMap.containsKey(attribute)) {
                        continue;
                    }
                    parameterValue = parameterMap.get(attribute);
                    if (attributes.length > 1) {
                        if (!(parameterValue instanceof List)) {
                            parameterValue = PropertyUtils.getProperty(Convert.convert(Map.class, parameterValue),
                                attributeName.substring(attribute.length() + 1));
                        } else {
                            parameterValue = PropertyUtils.getProperty(parameterValue,
                                attributeName.substring(attribute.length() + 1));
                        }
                    }
                    lockParamsBuffer.append("." + parameterValue);
                }
                lockKey = declaringTypeName + "." + methodName + lockParamsBuffer.toString();
            }
        }
        return lockKey;
    }
}
