// src/main/java/com/nhnacademy/environment/websocket/interceptor/AuthHandshakeInterceptor.java
package com.nhnacademy.environment.websocket.interceptor;

import com.nhnacademy.environment.websocket.dto.MemberResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Map;

/**
 * WebSocket Handshake 시 JWT 토큰 검증 및 companyDomain 추출을 담당하는 Interceptor
 * 검색 결과 [1][3][4]에서 보듯이 WebSocket JWT 인증의 핵심 구성요소
 */
@Component
@Slf4j
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final String jwtSecret;
    private final WebClient webClient;
    private final String memberMeUri;

    public AuthHandshakeInterceptor(@Value("${jwt.secret}") String jwtSecret,
                                    @Value("${member.api.url}") String memberApiUrl,
                                    @Value("${member.api.me.uri:/members/me}") String memberMeUri,
                                    @LoadBalanced WebClient.Builder webClientBuilder) {
        this.jwtSecret = jwtSecret;
        this.memberMeUri = memberMeUri;
        this.webClient = webClientBuilder
                .baseUrl(memberApiUrl)  // "http://MEMBER-API" 사용
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();

        log.info("AuthHandshakeInterceptor 초기화 완료 - memberApiUrl: {}, memberMeUri: {}",
                memberApiUrl, memberMeUri);
    }

    /**
     * WebSocket Handshake 이전에 실행되는 메소드
     * 검색 결과 [1][4]에서 보듯이 JWT 토큰 검증 및 사용자 정보 추출
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        log.debug("WebSocket handshake 시작 - URI: {}", request.getURI());

        try {
            // 1. JWT 토큰 추출 (검색 결과 [4] 참고)
            String token = extractToken(request);
            if (token == null) {
                log.warn("WebSocket handshake 실패 - JWT 토큰이 없습니다");
                return false;
            }

            // 2. JWT 토큰 검증 (검색 결과 [2][5] 참고)
            if (!validateToken(token)) {
                log.warn("WebSocket handshake 실패 - JWT 토큰이 유효하지 않습니다");
                return false;
            }

            // 3. JWT에서 사용자 정보 추출 (검색 결과 [2][5] 참고)
            Claims claims = parseTokenClaims(token);
            String userEmail = claims.getSubject();
            String userRole = claims.get("role", String.class);

            if (userEmail == null) {
                log.warn("WebSocket handshake 실패 - 사용자 이메일을 추출할 수 없습니다");
                return false;
            }

            // 4. Member API 호출해서 companyDomain 조회 (검색 결과 [1][3] 참고)
            String companyDomain = getCompanyDomainFromMemberApi(userEmail, userRole);
            if (companyDomain == null) {
                log.warn("WebSocket handshake 실패 - companyDomain을 조회할 수 없습니다. userEmail: {}", userEmail);
                // ★★★ companyDomain 실패해도 연결 허용 (기본값 사용) ★★★
                companyDomain = extractCompanyDomainFromEmail(userEmail);
            }

            // 5. WebSocket 세션에 인증 정보 저장 (검색 결과 [1][3] 참고)
            attributes.put("companyDomain", companyDomain);
            attributes.put("userEmail", userEmail);
            attributes.put("userRole", userRole);
            attributes.put("token", token);

            log.info("WebSocket handshake 성공 - userEmail: {}, role: {}, companyDomain: {}",
                    userEmail, userRole, companyDomain);
            return true;

        } catch (Exception e) {
            log.error("WebSocket handshake 중 오류 발생", e);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        if (exception != null) {
            log.error("WebSocket handshake 후 오류 발생", exception);
        } else {
            log.debug("WebSocket handshake 완료 - URI: {}", request.getURI());
        }
    }

    /**
     * HTTP 요청에서 JWT 토큰 추출 (검색 결과 [4] 참고)
     * WebSocket에서는 query parameter 방식이 일반적
     */
    private String extractToken(ServerHttpRequest request) {
        // 1. Query parameter에서 토큰 추출 (우선순위 1)
        String query = request.getURI().getQuery();
        if (query != null && query.contains("token=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    String token = param.substring(6);
                    log.debug("Query parameter에서 토큰 추출 성공");
                    return token;
                }
            }
        }

        // 2. Authorization 헤더에서 토큰 추출 (우선순위 2)
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            log.debug("Authorization 헤더에서 토큰 추출 성공");
            return token;
        }

        // 3. Sec-WebSocket-Protocol 헤더에서 토큰 추출 (검색 결과 [4] 참고)
        String protocolHeader = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (protocolHeader != null && protocolHeader.startsWith("Bearer.")) {
            String token = protocolHeader.substring(7);
            log.debug("Sec-WebSocket-Protocol 헤더에서 토큰 추출 성공");
            return token;
        }

        log.debug("JWT 토큰을 찾을 수 없습니다");
        return null;
    }

    /**
     * JWT 토큰 유효성 검증 (검색 결과 [2][5] 참고)
     */
    private boolean validateToken(String token) {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            SecretKey key = Keys.hmacShaKeyFor(keyBytes);

            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);

            log.debug("JWT 토큰 검증 성공");
            return true;

        } catch (Exception e) {
            log.warn("JWT 토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * JWT 토큰에서 Claims 추출 (검색 결과 [2][5] 참고)
     */
    private Claims parseTokenClaims(String token) {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            SecretKey key = Keys.hmacShaKeyFor(keyBytes);

            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            log.debug("JWT Claims 파싱 성공 - sub: {}, role: {}",
                    claims.getSubject(), claims.get("role"));
            return claims;

        } catch (Exception e) {
            log.error("JWT Claims 파싱 실패", e);
            throw new RuntimeException("JWT Claims 파싱 실패", e);
        }
    }

    /**
     * Member API를 호출하여 사용자의 companyDomain 조회
     * 검색 결과 [1][3]에서 보듯이 WebClient를 사용한 비동기 호출
     */
    private String getCompanyDomainFromMemberApi(String userEmail, String userRole) {
        try {
            log.debug("Member API 호출 시작 - userEmail: {}, role: {}, uri: {}",
                    userEmail, userRole, memberMeUri);

            // ★★★ 필드의 webClient 사용 (로드밸런싱 적용) ★★★
            MemberResponse member = webClient.get()
                    .uri(memberMeUri)
                    .header("X-User-Email", userEmail)
                    .header("X-User-Role", userRole)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                log.warn("Member API 호출 실패 - 상태코드: {}, userEmail: {}",
                                        clientResponse.statusCode(), userEmail);
                                return Mono.empty();
                            })
                    .bodyToMono(MemberResponse.class)
                    .timeout(Duration.ofSeconds(3))
                    .onErrorReturn(null)
                    .block();

            if (member == null) {
                log.warn("Member API 응답이 null입니다. userEmail: {}", userEmail);
                return null;
            }

            String companyDomain = member.getCompanyDomain();

            if (companyDomain == null || companyDomain.isBlank()) {
                log.warn("Member API에서 companyDomain이 비어있습니다. userEmail: {}", userEmail);
                return null;
            }

            // .com 제거 (Gateway 필터와 동일한 로직)
            if (companyDomain.endsWith(".com")) {
                companyDomain = companyDomain.substring(0, companyDomain.length() - 4);
                log.debug("companyDomain에서 .com 제거: {}", companyDomain);
            }

            log.debug("Member API에서 companyDomain 조회 성공: {}", companyDomain);
            return companyDomain;

        } catch (Exception e) {
            log.warn("Member API 호출 실패, fallback 사용. userEmail: {}, role: {}",
                    userEmail, userRole, e);
            return null;
        }
    }

    /**
     * 이메일에서 companyDomain 추출 (fallback 방식)
     * 검색 결과 [1]에서 보듯이 WebSocket에서는 fallback 전략이 중요
     */
    private String extractCompanyDomainFromEmail(String userEmail) {
        try {
            if (userEmail != null && userEmail.contains("@")) {
                String emailDomain = userEmail.split("@")[1];

                // .com 제거
                if (emailDomain.endsWith(".com")) {
                    emailDomain = emailDomain.substring(0, emailDomain.length() - 4);
                }

                log.debug("이메일에서 companyDomain 추출: {} -> {}", userEmail, emailDomain);
                return emailDomain;
            }
        } catch (Exception e) {
            log.warn("이메일에서 companyDomain 추출 실패: {}", userEmail, e);
        }

        return "default";
    }
}
