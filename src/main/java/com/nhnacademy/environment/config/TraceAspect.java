package com.nhnacademy.environment.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

@Slf4j
@Aspect
@Component
public class TraceAspect {

    /**
     * 로그 파일을 분류하는 기준을 정해줍니다.
     */
    Marker traceMarker = MarkerFactory.getMarker("TRACE_ID_LOG");

    @Around("execution(* com.nhnacademy.environment..*(..))")
    public Object logMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        long start = System.currentTimeMillis();

        log.info(traceMarker, "[ENTER] {}.{}()", className, methodName);
        Object result = joinPoint.proceed();
        long end = System.currentTimeMillis();

        log.info(traceMarker, "[EXIT] {}.{}() => {}ms", className, methodName, (end - start));
        return result;
    }
}
