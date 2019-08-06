package com.wx.framework.annotation;

import java.lang.annotation.*;

/**
 * Created by IntelliJ IDEA.
 * User: wangxiang
 * Date: 2019/8/6
 * To change this template use File | Settings | File Templates.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyRequestBody {
    String value() default "";
}
