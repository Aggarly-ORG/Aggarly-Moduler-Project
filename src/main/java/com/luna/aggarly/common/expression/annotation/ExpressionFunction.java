package com.luna.aggarly.common.expression.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExpressionFunction {

    /**
     * The primary name of the function (e.g. "date_add", "format_currency").
     * If blank, defaults to the Java method name.
     */
    String value() default "";

    /**
     * Alias or explicit name of the function.
     */
    String name() default "";

    /**
     * Human-readable description of what the function computes.
     */
    String description() default "";

    /**
     * Optional aliases for the function (e.g. ["DateUtils.today", "today"]).
     */
    String[] aliases() default {};
}
