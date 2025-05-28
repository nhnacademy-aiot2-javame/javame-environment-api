package com.nhnacademy.environment.config.annotation;

public class CompanyDomainContext {

    private static final ThreadLocal<String> domainHolder = new ThreadLocal<>();

    public static void set(String domain) {
        domainHolder.set(domain);
    }

    public static String get() {
        return domainHolder.get();
    }

    public static void clear() {
        domainHolder.remove();
    }
}
