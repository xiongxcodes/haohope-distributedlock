package com.github.xiongxcodes.distributedlock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DistributedlockAnn {
    /**
     * *锁的参数的属性名
     */
    String[] attributeNames() default {};

    /**
     * *分布式锁名称
     *
     * @return String
     */
    String lockKey() default "";

    /**
     * *锁超时时间（单位：秒） 如果超过还没有解锁的话,就强制解锁
     *
     * @return int
     */
    int expireSeconds() default 10;

    /**
     * *等待多久（单位：秒）-1 则表示一直等待
     *
     * @return int
     */
    int waitTime() default 5;

    /**
     * *未取到锁时提示信息
     *
     * @return
     */
    String failMsg() default "获取锁失败，请稍后重试";
}
