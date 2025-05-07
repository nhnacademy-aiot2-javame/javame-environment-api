package com.nhnacademy.environment.interceptor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtCompanyDomainInterceptor implements HandlerInterceptor {

    private final String jwtSecret = "secret-key"; // 임시 시크릿 (실제로는 환경변수 처리)

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return true; // 비로그인 요청도 허용 가능
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = Jwts.parser()
                    .setSigningKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                    .parseClaimsJws(token)
                    .getBody();

            String companyDomain = claims.get("companyDomain", String.class);

            // 컨트롤러 단에서 꺼내 쓰기 쉽게 request에 저장
            request.setAttribute("companyDomain", companyDomain);

        } catch (Exception e) {
            log.warn("JWT 파싱 실패: {}", e.getMessage());
            // 필수 토큰이 아닌 경우 continue, 아니면 return false
        }

        return true;
    }
}
