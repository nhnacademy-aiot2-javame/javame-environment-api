package com.nhnacademy.environment.config;

import com.nhnacademy.environment.interceptor.JwtCompanyDomainInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtCompanyDomainInterceptor jwtCompanyDomainInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtCompanyDomainInterceptor)
                .addPathPatterns("/environment/**"); // 필요 경로 지정
    }
}
