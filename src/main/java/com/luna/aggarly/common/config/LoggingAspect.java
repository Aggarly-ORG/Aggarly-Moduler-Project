// common/config/LoggingAspect.java
package com.luna.aggarly.common.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("within(com.luna.aggarly..controller..*) || within(com.luna.aggarly..service..*)")
    public void applicationLayer() {}

    @Around("applicationLayer()")
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        long start = System.currentTimeMillis();

        log.info("Entering {}.{} args={}", className, methodName, joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            log.info("Exiting {}.{} duration={}ms", className, methodName, duration);
            return result;
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            log.error("Exception in {}.{} duration={}ms error={}", className, methodName, duration, ex.getMessage());
            throw ex;
        }
    }
}