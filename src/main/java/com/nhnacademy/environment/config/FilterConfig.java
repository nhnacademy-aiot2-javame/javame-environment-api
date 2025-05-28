package com.nhnacademy.environment.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new TraceIdFilter());
        registrationBean.setOrder(1); // 필터 순서, 낮을수록 먼저 실행됨
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<com.nhnacademy.environment.config.filter.CompanyDomainContextCleanupFilter> companyDomainContextCleanupFilter() {
        FilterRegistrationBean<com.nhnacademy.environment.config.filter.CompanyDomainContextCleanupFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new com.nhnacademy.environment.config.filter.CompanyDomainContextCleanupFilter());
        registrationBean.setOrder(2); // TraceIdFilter 다음에 실행되도록 순서 지정
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }
}
