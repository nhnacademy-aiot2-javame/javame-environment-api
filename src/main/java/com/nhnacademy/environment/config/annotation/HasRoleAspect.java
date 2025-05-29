package com.nhnacademy.environment.config.annotation;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;


/**
 *  Request Header 에서 X-USER-ROLE을 꺼내 권한이 있는지 확인하는 Aspect 입니다.
 *  Controller 에서 사용합니다.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class HasRoleAspect {

    /**
     * d.
     */
    private final HttpServletRequest request;

    @Before("@annotation(hasRole)")
    public void checkRole(HasRole hasRole) {
        String userRole = request.getHeader("X-USER-ROLE");

        if (userRole == null || Arrays.stream(hasRole.value()).noneMatch(userRole::equals)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }
    }
}
