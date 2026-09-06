package com.luna.aggarly.common.expression.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface ExpressionFunctionLibrary {

    /**
     * Optional category or library name (e.g. "date", "string", "math", "entity").
     */
    String value() default "";

    /**
     * Optional namespace prefix to auto-prepend to function names in this library (e.g. "DateUtils").
     */
    String prefix() default "";
}
