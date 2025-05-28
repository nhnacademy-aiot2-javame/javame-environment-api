package com.nhnacademy.environment.config.filter;

import com.nhnacademy.environment.config.annotation.CompanyDomainContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청이 끝나면 CompanyDomainContext 를 반드시 초기화한다.
 * 스프링 웹 요청은 요청마다 새로운 스레드를 할당해서 처리합니다.
 * 따라서 이전 요청 값이 그대로 남아 있을 위험이 있기 때문에 요청 처리가 끝나면 반드시 remove 를 해 줍니다.
 */
public class CompanyDomainContextCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response); // 요청 흐름 계속 진행
        } finally {
            CompanyDomainContext.clear(); // 요청 종료 시점에서 무조건 정리
        }
    }
}
