package com.nhnacademy.environment.websocket.interceptor;

import com.nhnacademy.environment.websocket.dto.MemberResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Value("${member.api.url:http://MEMBER-API}")
    private String memberApiUrl;

    @Value("${member.api.me.uri:/members/me}")
    private String memberMeUri;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private WebClient webClient;

    private final WebClient.Builder loadBalancedWebClientBuilder;

    public AuthHandshakeInterceptor(@LoadBalanced WebClient.Builder webClientBuilder) {
        this.loadBalancedWebClientBuilder = webClientBuilder;
    }

    @PostConstruct
    public void initWebClient() {
        this.webClient = loadBalancedWebClientBuilder
                .baseUrl(memberApiUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();

        log.info("=== AuthHandshakeInterceptor 초기화 완료 ===");
        log.info("memberApiUrl: {}", memberApiUrl);
        log.info("memberMeUri: {}", memberMeUri);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        log.debug("WebSocket handshake 시작 - URI: {}", request.getURI());

        try {
            // 1. JWT 토큰 추출
            String token = extractToken(request);
            if (token == null) {
                log.warn("WebSocket handshake 실패 - JWT 토큰이 없습니다");
                return false;
            }

            // 2. JWT 토큰 검증
            if (!validateToken(token)) {
                log.warn("WebSocket handshake 실패 - JWT 토큰이 유효하지 않습니다");
                return false;
            }

            // 3. JWT에서 사용자 정보 추출
            Claims claims = parseTokenClaims(token);
            String userEmail = claims.getSubject();
            String userRole = claims.get("role", String.class);

            if (userEmail == null) {
                log.warn("WebSocket handshake 실패 - 사용자 이메일을 추출할 수 없습니다");
                return false;
            }

            // 4. Member API 호출해서 companyDomain 조회 (필수)
            String companyDomain = getCompanyDomainFromMemberApi(userEmail, userRole);
            if (companyDomain == null) {
                log.error("Member API에서 companyDomain 조회 실패. 회사 소속이 없는 비정상적인 사용자로 판단하여 WebSocket 연결 거부. userEmail: {}", userEmail);
                return false;
            }

            // ★★★ 서버에서 .com 제거 로직 추가 ★★★
            String cleanCompanyDomain = companyDomain;
            if (companyDomain.endsWith(".com")) {
                cleanCompanyDomain = companyDomain.substring(0, companyDomain.length() - 4);
                log.info("companyDomain에서 .com 제거: {} -> {}", companyDomain, cleanCompanyDomain);
            }

            // 5. WebSocket 세션에 인증 정보 저장 (정리된 도메인 사용)
            attributes.put("companyDomain", cleanCompanyDomain); // ★★★ 정리된 도메인 저장 ★★★
            attributes.put("userEmail", userEmail);
            attributes.put("userRole", userRole);
            attributes.put("token", token);

            log.info("WebSocket handshake 성공 - userEmail: {}, role: {}, companyDomain: {} (원본: {})",
                    userEmail, userRole, cleanCompanyDomain, companyDomain);
            return true;

        }catch (Exception e) {
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
     * HTTP 요청에서 JWT 토큰 추출 (accessToken 파라미터 지원)
     */
    private String extractToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery();

        // Query parameter에서 토큰 추출
        if (query != null) {
            Map<String, String> queryParams = parseQueryString(query);

            // accessToken 우선, token은 fallback
            String token = queryParams.get("accessToken");
            if (token != null) {
                log.debug("accessToken 파라미터에서 토큰 추출 성공");
                return token;
            }

            token = queryParams.get("token");
            if (token != null) {
                log.debug("token 파라미터에서 토큰 추출 성공");
                return token;
            }
        }

        // Authorization 헤더에서 토큰 추출
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            log.debug("Authorization 헤더에서 토큰 추출 성공");
            return authHeader.substring(7);
        }

        log.debug("JWT 토큰을 찾을 수 없습니다");
        return null;
    }

    private Map<String, String> parseQueryString(String query) {
        return Arrays.stream(query.split("&"))
                .map(param -> param.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0],
                        parts -> {
                            try {
                                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                            } catch (Exception e) {
                                return parts[1]; // 디코딩 실패 시 원본 반환
                            }
                        }
                ));
    }


    /**
     * JWT 토큰 유효성 검증
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
     * JWT 토큰에서 Claims 추출
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
     */
    private String getCompanyDomainFromMemberApi(String userEmail, String userRole) {
        try {
            log.info("=== Member API 호출 시작 ===");
            log.info("URL: {}", memberApiUrl);
            log.info("userEmail: {}", userEmail);

            MemberResponse member = webClient.get()
                    .uri(memberMeUri)
                    .header("X-User-Email", userEmail)
                    .header("X-User-Role", userRole)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            response -> response.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("4xx 에러: {}", body))
                                    .then(Mono.error(new RuntimeException("Member API 4xx 에러"))))
                    .onStatus(HttpStatusCode::is5xxServerError,
                            response -> response.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("5xx 에러: {}", body))
                                    .then(Mono.error(new RuntimeException("Member API 5xx 에러"))))
                    .bodyToMono(MemberResponse.class)
                    .timeout(Duration.ofSeconds(5)) // 타임아웃 단축
                    .block();

            if (member != null && member.getCompanyDomain() != null && !member.getCompanyDomain().isBlank()) {
                log.info("companyDomain 조회 성공: {}", member.getCompanyDomain());
                return member.getCompanyDomain();
            }

            log.error("Member API 응답이 null이거나 companyDomain이 없음");
            return null;

        } catch (Exception e) {
            log.error("Member API 호출 실패: {}", e.getMessage());
            return null;
        }
    }
}
