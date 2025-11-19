package com.example.urlshortener.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Times calls to service and repository methods so you can correlate
 * higher-level flow with SQL and cache activity via the MDC requestId.
 */
@Aspect
@Component
public class MethodTimingAspect {

    private static final Logger log = LoggerFactory.getLogger(MethodTimingAspect.class);

    @Around("execution(* com.example.urlshortener.service..*(..)) || execution(* com.example.urlshortener.repository..*(..))")
    public Object timeServiceAndRepository(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String signature = pjp.getSignature().toShortString();
        try {
            Object result = pjp.proceed();
            long dur = System.currentTimeMillis() - start;
            // Keep args and result out to avoid logging PII; method name and duration are sufficient.
            log.info("CALL {} durationMs={}", signature, dur);
            return result;
        } catch (Throwable t) {
            long dur = System.currentTimeMillis() - start;
            log.warn("CALL {} failed after {}ms: {}", signature, dur, t.toString());
            throw t;
        }
    }
}
