package com.nhnacademy.environment.config;

public class DotUtil {

    public static String removeDot(String companyDomain) {
        if (companyDomain == null || companyDomain.isBlank()) {
            return null;
        }

        // 정규식: 도메인 확장자 패턴 제거
        return companyDomain.replaceFirst("(\\.(co|go)?\\.kr|\\.com|\\.net|\\.org|\\.io|\\.dev|\\.ai)$", "");
    }

}
