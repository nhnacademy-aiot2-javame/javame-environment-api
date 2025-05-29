package com.nhnacademy.environment.config.annotation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;

//@Aspect
//@Component
//@Slf4j
//@RequiredArgsConstructor
//public class NormalizeCompanyDomainAspect {
//
//    /**
//     * companyDomain 이 "xxx.com" 형식이면 "xxx"로 정제하여 ThreadLocal 에 저장.
//     * 이후 서비스 계층 등에서 CompanyDomainContext.get() 으로 사용 가능.
//     * 호출 메서드와 파라미터 로그도 남긴다.
//     * @param joinPoint 해당 어노테이션을
//     * @param companyDomain 사용자의 회사 도메인
//     */
//    @Before("@annotation(com.nhnacademy.environment.config.annotation.NormalizeCompanyDomain) && args(companyDomain,..)")
//    public void normalizeCompanyDomain(JoinPoint joinPoint, NormalizeCompanyDomain companyDomain) {
//
//        if (companyDomain == null || companyDomain.value().isBlank()) {
//            log.warn("요청 경로의 companyDomain이 null 또는 빈 문자열입니다. 호출 위치: {}.{}",
//                    joinPoint.getSignature().getDeclaringTypeName(),
//                    joinPoint.getSignature().getName());
//            return;
//        }
//
//        String normalized = companyDomain.value().contains(".")
//                ? companyDomain.value().split("\\.")[0]
//                : companyDomain.value();
//
//        log.error("dddd : {}", normalized);
//
//        CompanyDomainContext.set(normalized);
//
//        log.debug("정제된 companyDomain = {}, 호출 클래스 = {}, 메서드 = {}, 파라미터 = {}",
//                normalized,
//                joinPoint.getSignature().getDeclaringTypeName(),
//                joinPoint.getSignature().getName(),
//                Arrays.toString(joinPoint.getArgs()));
//    }
//}

@Aspect
@Component
public class NormalizeCompanyDomainAspect {

    @Around("@annotation(NormalizeCompanyDomain)")
    public Object cleanCompanyDomain(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String str && isDomainWithTld(str)) {
                args[i] = extractDomainName(str);
            }
        }

        return joinPoint.proceed(args);
    }

    private boolean isDomainWithTld(String domain) {
        return domain.matches("^[a-zA-Z0-9.-]+\\.(com|co\\.kr|go\\.kr|net|org|io|ai|dev)$");
    }

    private String extractDomainName(String fullDomain) {
        return fullDomain.replaceFirst("(\\.(co|go)?\\.kr|\\.com|\\.net|\\.org|\\.io|\\.dev|\\.ai)$", "");
    }
}