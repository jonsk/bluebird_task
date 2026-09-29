package com.bbtc.bluebird.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交（02 §1.4 R8，10005）。标注在写接口上，同用户+同 URI+同参数在窗口内重复 → 拒绝。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {

    /** 窗口秒数。 */
    int seconds() default 3;
}
