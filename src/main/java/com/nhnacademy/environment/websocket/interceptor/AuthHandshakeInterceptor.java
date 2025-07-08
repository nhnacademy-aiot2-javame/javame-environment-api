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

/**
 * **웹소켓 연결 핸드셰이크(Handshake) 단계에서 인증 및 권한 부여를 처리하는 인터셉터입니다.**
 *
 * 이 클래스는 클라이언트가 웹소켓 연결을 시도할 때, 실제 웹소켓 통신이 시작되기 전에
 * HTTP 요청을 가로채서 JWT(JSON Web Token)를 검증하고, 사용자 정보를 추출하며,
 * 외부 Member API를 호출하여 사용자의 소속 회사 도메인(`companyDomain`)을 확인합니다.
 * 이 모든 검증이 성공해야만 웹소켓 연결을 허용하여 보안을 강화하고,
 * 이후 실시간 데이터 필터링에 필요한 사용자 컨텍스트 정보를 웹소켓 세션에 저장합니다.
 */
@Component // 이 클래스를 Spring 빈으로 등록하여 의존성 주입(DI)을 통해 다른 컴포넌트에서 사용할 수 있도록 합니다.
@Slf4j // Lombok 어노테이션: 로깅을 위한 Logger 객체를 자동으로 생성하여 디버깅 및 운영 모니터링에 활용합니다.
public class AuthHandshakeInterceptor implements HandshakeInterceptor { // Spring이 제공하는 HandshakeInterceptor 인터페이스를 구현합니다.

    /**
     * Member API의 기본 URL입니다. Spring의 `@Value` 어노테이션을 통해 `application.properties`
     * 또는 환경 변수에서 값을 주입받습니다. 기본값으로 `http://MEMBER-API`가 설정되어 있어,
     * Eureka와 같은 서비스 디스커버리 환경에서 서비스 이름으로 호출될 수 있도록 합니다.
     */
    @Value("${member.api.url:http://MEMBER-API}")
    private String memberApiUrl;

    /**
     * Member API에서 현재 로그인된 사용자 정보를 조회하는 URI입니다.
     * 기본값으로 `/members/me`가 설정되어 있습니다.
     */
    @Value("${member.api.me.uri:/members/me}")
    private String memberMeUri;

    /**
     * JWT 토큰을 서명하고 검증하는 데 사용되는 비밀 키입니다.
     * 이 키는 외부에 노출되지 않도록 안전하게 관리되어야 합니다.
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    // WebClient는 비동기 및 논블로킹 방식으로 HTTP 요청을 수행하는 Spring WebFlux의 핵심 컴포넌트입니다.
    private WebClient webClient;

    // LoadBalanced WebClient.Builder는 Eureka와 같은 서비스 디스커버리 환경에서
    // 서비스 이름을 통해 로드 밸런싱된 HTTP 호출을 가능하게 합니다.
    private final WebClient.Builder loadBalancedWebClientBuilder;

    /**
     * AuthHandshakeInterceptor의 생성자입니다.
     * Spring에 의해 `WebClient.Builder` 빈이 자동으로 주입됩니다.
     *
     * @param webClientBuilder 로드 밸런싱 기능이 활성화된 WebClient 빌더
     */
    public AuthHandshakeInterceptor(@LoadBalanced WebClient.Builder webClientBuilder) {
        this.loadBalancedWebClientBuilder = webClientBuilder;
    }

    /**
     * **인터셉터 객체가 생성된 후, WebClient를 초기화하는 메서드입니다.**
     *
     * `@PostConstruct` 어노테이션 덕분에 이 메서드는 빈의 모든 의존성 주입이 완료된 후에
     * 단 한 번만 실행됩니다.
     */
    @PostConstruct
    public void initWebClient() {
        // 주입받은 빌더를 사용하여 Member API 호출을 위한 WebClient 인스턴스를 생성합니다.
        this.webClient = loadBalancedWebClientBuilder
                .baseUrl(memberApiUrl) // Member API의 기본 URL을 설정합니다.
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 인메모리 버퍼 크기를 1MB로 설정하여 큰 응답도 처리할 수 있도록 합니다.
                .build(); // WebClient 인스턴스를 빌드합니다.

        log.info("=== AuthHandshakeInterceptor 초기화 완료 ===");
        log.info("memberApiUrl: {}", memberApiUrl);
        log.info("memberMeUri: {}", memberMeUri);
    }

    /**
     * **웹소켓 핸드셰이크(Handshake) 요청이 처리되기 전에 호출되는 핵심 메서드입니다.**
     *
     * 이 메서드는 클라이언트의 웹소켓 연결 요청을 가로채서 인증 및 권한 확인 로직을 수행합니다.
     * 여기서 `true`를 반환하면 웹소켓 연결이 계속 진행되고, `false`를 반환하면 연결이 거부됩니다.
     *
     * @param request   클라이언트로부터의 HTTP 요청 (웹소켓 핸드셰이크 요청)
     * @param response  클라이언트로 보낼 HTTP 응답
     * @param wsHandler 웹소켓 연결을 처리할 핸들러 (EnvironmentWebSocketHandler)
     * @param attributes 웹소켓 세션에 저장될 속성(attributes) 맵입니다. 여기에 저장된 정보는 웹소켓 연결이 수립된 후 핸들러에서 사용될 수 있습니다.
     * @return 웹소켓 연결을 계속 진행할지(true) 또는 거부할지(false) 여부
     * @throws Exception 처리 중 발생할 수 있는 예외
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        log.debug("WebSocket handshake 시작 - URI: {}", request.getURI()); // 핸드셰이크 시작을 알리는 디버그 로그

        try {
            // 1. JWT 토큰 추출: 클라이언트 요청(URL 쿼리 파라미터 또는 Authorization 헤더)에서 JWT 토큰을 추출합니다.
            String token = extractToken(request);
            if (token == null) {
                log.warn("WebSocket handshake 실패 - JWT 토큰이 없습니다"); // 토큰이 없으면 경고 로그를 남기고 연결을 거부합니다.
                return false; // 연결 거부
            }

            // 2. JWT 토큰 검증: 추출된 JWT 토큰의 유효성을 검증합니다 (서명, 만료 여부 등).
            if (!validateToken(token)) {
                log.warn("WebSocket handshake 실패 - JWT 토큰이 유효하지 않습니다"); // 유효하지 않은 토큰이면 경고 로그를 남기고 연결을 거부합니다.
                return false; // 연결 거부
            }

            // 3. JWT에서 사용자 정보 추출: 유효한 JWT 토큰에서 사용자 이메일(subject)과 역할(role)을 추출합니다.
            Claims claims = parseTokenClaims(token);
            String userEmail = claims.getSubject(); // JWT의 'sub' 클레임은 보통 사용자 식별자(이메일 등)를 나타냅니다.
            String userRole = claims.get("role", String.class); // JWT의 커스텀 클레임에서 사용자 역할을 추출합니다.

            if (userEmail == null) {
                log.warn("WebSocket handshake 실패 - 사용자 이메일을 추출할 수 없습니다"); // 이메일 정보가 없으면 경고 로그를 남기고 연결을 거부합니다.
                return false; // 연결 거부
            }

            // 4. Member API 호출하여 companyDomain 조회 (필수):
            // JWT 토큰에는 companyDomain 정보가 직접 포함되지 않으므로, 외부 Member API를 호출하여
            // 해당 사용자의 소속 회사 도메인 정보를 가져옵니다. 이는 실시간 데이터 필터링에 중요한 기준이 됩니다.
            String companyDomain = getCompanyDomainFromMemberApi(userEmail, userRole);
            if (companyDomain == null) {
                log.error("Member API에서 companyDomain 조회 실패. 회사 소속이 없는 비정상적인 사용자로 판단하여 WebSocket 연결 거부. userEmail: {}", userEmail); // companyDomain이 없으면 오류 로그를 남기고 연결을 거부합니다.
                return false; // 연결 거부
            }

            // 5. companyDomain 정규화:
            // companyDomain이 ".com"으로 끝나는 경우, 내부 시스템에서 사용될 때는 ".com"을 제거하여 정규화합니다.
            // 이는 데이터 일관성을 유지하고, InfluxDB 쿼리 등에서 활용될 때의 편의성을 높이기 위함입니다.
            String cleanCompanyDomain = companyDomain;
            if (companyDomain.endsWith(".com")) {
                cleanCompanyDomain = companyDomain.substring(0, companyDomain.length() - 4);
                log.info("companyDomain에서 .com 제거: {} -> {}", companyDomain, cleanCompanyDomain);
            }

            // 6. WebSocket 세션에 인증 및 사용자 컨텍스트 정보 저장:
            // 추출 및 정규화된 사용자 정보(회사 도메인, 이메일, 역할)를 웹소켓 세션의 'attributes' 맵에 저장합니다.
            // 이 정보들은 웹소켓 연결이 수립된 후 EnvironmentWebSocketHandler나 WebSocketSessionManager에서
            // 실시간 데이터를 조회하고 전송하는 데 사용됩니다.
            attributes.put("companyDomain", cleanCompanyDomain); // 정규화된 회사 도메인 저장
            attributes.put("userEmail", userEmail); // 사용자 이메일 저장
            attributes.put("userRole", userRole); // 사용자 역할 저장
            attributes.put("token", token); // 원본 JWT 토큰 저장 (필요시 재사용)

            log.info("WebSocket handshake 성공 - userEmail: {}, role: {}, companyDomain: {} (원본: {})",
                    userEmail, userRole, cleanCompanyDomain, companyDomain); // 핸드셰이크 성공 로그
            return true; // 모든 검증 통과: 웹소켓 연결 허용

        } catch (Exception e) {
            // 핸드셰이크 처리 중 예상치 못한 오류가 발생한 경우, 오류 로그를 남기고 연결을 거부합니다.
            log.error("WebSocket handshake 중 오류 발생", e);
            return false; // 연결 거부
        }
    }

    /**
     * **웹소켓 핸드셰이크(Handshake) 요청이 처리된 후에 호출되는 메서드입니다.**
     *
     * 이 메서드는 핸드셰이크 성공 또는 실패 여부와 관계없이 호출됩니다.
     * 주로 로그를 남기거나, 리소스를 정리하는 등의 후처리 작업을 수행할 수 있습니다.
     *
     * @param request   클라이언트로부터의 HTTP 요청
     * @param response  클라이언트로 보낸 HTTP 응답
     * @param wsHandler 웹소켓 연결을 처리한 핸들러
     * @param exception 핸드셰이크 처리 중 발생한 예외 (성공 시 null)
     */
    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        if (exception != null) {
            // 핸드셰이크 중 오류가 발생했다면 오류 로그를 남깁니다.
            log.error("WebSocket handshake 후 오류 발생", exception);
        } else {
            // 핸드셰이크가 성공적으로 완료되었음을 알리는 디버그 로그를 남깁니다.
            log.debug("WebSocket handshake 완료 - URI: {}", request.getURI());
        }
    }

    /**
     * **HTTP 요청에서 JWT 토큰을 추출하는 헬퍼 메서드입니다.**
     *
     * 토큰은 주로 두 가지 위치에서 찾습니다:
     * 1. URL 쿼리 파라미터 (예: `?accessToken=...` 또는 `?token=...`)
     * 2. HTTP `Authorization` 헤더 (예: `Authorization: Bearer ...`)
     *
     * @param request 클라이언트로부터의 HTTP 요청 객체
     * @return 추출된 JWT 토큰 문자열 (없으면 null 반환)
     */
    private String extractToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery(); // 요청 URI에서 쿼리 문자열을 가져옵니다.

        // 1. URL 쿼리 파라미터에서 토큰 추출 시도
        if (query != null) {
            // 쿼리 문자열을 파싱하여 Map<파라미터명, 값> 형태로 변환합니다.
            Map<String, String> queryParams = parseQueryString(query);

            // 'accessToken' 파라미터에서 토큰을 먼저 찾습니다.
            String token = queryParams.get("accessToken");
            if (token != null) {
                log.debug("accessToken 파라미터에서 토큰 추출 성공");
                return token;
            }

            // 'accessToken'이 없으면 'token' 파라미터에서 찾습니다 (fallback).
            token = queryParams.get("token");
            if (token != null) {
                log.debug("token 파라미터에서 토큰 추출 성공");
                return token;
            }
        }

        // 2. Authorization 헤더에서 토큰 추출 시도
        // 'Authorization: Bearer [토큰값]' 형식의 헤더에서 'Bearer ' 부분을 제외한 토큰값을 추출합니다.
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            log.debug("Authorization 헤더에서 토큰 추출 성공");
            return authHeader.substring(7); // "Bearer " (7글자) 이후의 문자열이 실제 토큰입니다.
        }

        // 어떤 위치에서도 토큰을 찾지 못한 경우
        log.debug("JWT 토큰을 찾을 수 없습니다");
        return null;
    }

    /**
     * **URL 쿼리 문자열(예: "param1=value1&param2=value2")을 파싱하여 Map 형태로 반환하는 헬퍼 메서드입니다.**
     *
     * URL 디코딩을 포함하여 안전하게 파라미터 값을 추출합니다.
     *
     * @param query 파싱할 쿼리 문자열
     * @return 파싱된 파라미터들을 담은 Map (키: 파라미터명, 값: 파라미터 값)
     */
    private Map<String, String> parseQueryString(String query) {
        // 쿼리 문자열을 '&' 기준으로 분리하고, 각 부분을 '=' 기준으로 다시 분리하여 Map으로 변환합니다.
        return Arrays.stream(query.split("&"))
                .map(param -> param.split("=", 2)) // 'key=value' 형태를 최대 2부분으로 분리 (값에 '='가 포함될 수 있으므로)
                .filter(parts -> parts.length == 2) // 'key=value' 형태만 필터링 (잘못된 형식 제외)
                .collect(Collectors.toMap(
                        parts -> parts[0], // Map의 키는 파라미터명
                        parts -> {
                            try {
                                // 파라미터 값을 UTF-8로 URL 디코딩합니다. (예: '%20' -> ' ')
                                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                            } catch (Exception e) {
                                // 디코딩 실패 시 원본 값을 반환하고 로그를 남길 수 있습니다.
                                log.warn("URL 디코딩 실패: {}", parts[1], e);
                                return parts[1];
                            }
                        }
                ));
    }

    /**
     * **JWT 토큰의 유효성을 검증하는 헬퍼 메서드입니다.**
     *
     * 토큰의 서명, 만료 여부 등을 확인하여 변조되거나 유효하지 않은 토큰을 걸러냅니다.
     *
     * @param token 검증할 JWT 토큰 문자열
     * @return 토큰이 유효하면 true, 그렇지 않으면 false
     */
    private boolean validateToken(String token) {
        try {
            // Base64로 인코딩된 비밀 키 문자열을 바이트 배열로 디코딩합니다.
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            // 디코딩된 바이트 배열로 HMAC SHA 키를 생성합니다.
            SecretKey key = Keys.hmacShaKeyFor(keyBytes);

            // Jwts.parser()를 사용하여 토큰 파서(Parser)를 빌드하고,
            // 생성된 비밀 키로 토큰의 서명을 검증하도록 설정합니다.
            Jwts.parser()
                    .verifyWith(key) // 서명 검증에 사용할 키를 지정합니다.
                    .build() // 파서 빌드
                    .parseSignedClaims(token); // 토큰을 파싱하고 클레임(Claims)을 추출합니다. 이 과정에서 서명 검증 실패, 만료 등 유효성 문제가 발생하면 예외가 발생합니다.

            log.debug("JWT 토큰 검증 성공");
            return true; // 예외 없이 여기까지 왔다면 토큰은 유효합니다.

        } catch (Exception e) {
            // 토큰 검증 실패 시 (예: 서명 불일치, 토큰 만료 등) 경고 로그를 남깁니다.
            log.warn("JWT 토큰 검증 실패: {}", e.getMessage());
            return false; // 토큰이 유효하지 않습니다.
        }
    }

    /**
     * **유효한 JWT 토큰에서 클레임(Claims) 정보를 추출하는 헬퍼 메서드입니다.**
     *
     * 클레임에는 토큰의 주체(Subject, 보통 사용자 ID/이메일), 역할(Role), 만료 시간 등
     * 다양한 사용자 및 토큰 관련 정보가 포함되어 있습니다.
     *
     * @param token 클레임을 파싱할 JWT 토큰 문자열
     * @return 파싱된 클레임(Claims) 객체
     * @throws RuntimeException 클레임 파싱 중 오류 발생 시 (예: 토큰 형식이 잘못된 경우)
     */
    private Claims parseTokenClaims(String token) {
        try {
            // validateToken 메서드와 동일하게 비밀 키를 생성합니다.
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            SecretKey key = Keys.hmacShaKeyFor(keyBytes);

            // Jwts.parser()를 사용하여 토큰 파서(Parser)를 빌드하고,
            // 생성된 비밀 키로 토큰의 서명을 검증하도록 설정합니다.
            Claims claims = Jwts.parser()
                    .verifyWith(key) // 서명 검증에 사용할 키를 지정합니다.
                    .build() // 파서 빌드
                    .parseSignedClaims(token) // 토큰을 파싱하고 서명된 클레임(Claims)을 추출합니다.
                    .getPayload(); // 클레임 페이로드(Payload)를 가져옵니다.

            // 추출된 클레임 정보(주체, 역할)를 디버그 로그로 남깁니다.
            log.debug("JWT Claims 파싱 성공 - sub: {}, role: {}",
                    claims.getSubject(), claims.get("role"));
            return claims; // 파싱된 클레임 객체를 반환합니다.

        } catch (Exception e) {
            // 클레임 파싱 중 오류가 발생하면 오류 로그를 남기고 RuntimeException을 발생시킵니다.
            log.error("JWT Claims 파싱 실패", e);
            throw new RuntimeException("JWT Claims 파싱 실패", e);
        }
    }

    /**
     * **외부 Member API를 호출하여 사용자의 소속 회사 도메인(`companyDomain`)을 조회하는 메서드입니다.**
     *
     * 이 메서드는 WebClient를 사용하여 비동기적으로 Member API에 HTTP GET 요청을 보냅니다.
     * 요청 헤더에 사용자 이메일과 역할을 포함하여 Member API가 해당 사용자 정보를 식별할 수 있도록 합니다.
     *
     * @param userEmail 조회할 사용자의 이메일
     * @param userRole  조회할 사용자의 역할
     * @return 조회된 `companyDomain` 문자열 (조회 실패 시 null 반환)
     */
    private String getCompanyDomainFromMemberApi(String userEmail, String userRole) {
        try {
            log.info("=== Member API 호출 시작 ===");
            log.info("URL: {}", memberApiUrl);
            log.info("userEmail: {}", userEmail);

            // WebClient를 사용하여 Member API에 GET 요청을 보냅니다.
            MemberResponse member = webClient.get()
                    .uri(memberMeUri) // 사용자 정보 조회 URI (예: /members/me)
                    .header("X-User-Email", userEmail) // 요청 헤더에 사용자 이메일 추가
                    .header("X-User-Role", userRole) // 요청 헤더에 사용자 역할 추가
                    .retrieve() // 응답을 가져옵니다.
                    // HTTP 상태 코드가 4xx(클라이언트 오류)인 경우 처리
                    .onStatus(HttpStatusCode::is4xxClientError,
                            response -> response.bodyToMono(String.class) // 응답 본문을 String으로 변환
                                    .doOnNext(body -> log.error("4xx 에러: {}", body)) // 오류 본문을 로그로 남김
                                    .then(Mono.error(new RuntimeException("Member API 4xx 에러")))) // RuntimeException 발생
                    // HTTP 상태 코드가 5xx(서버 오류)인 경우 처리
                    .onStatus(HttpStatusCode::is5xxServerError,
                            response -> response.bodyToMono(String.class) // 응답 본문을 String으로 변환
                                    .doOnNext(body -> log.error("5xx 에러: {}", body)) // 오류 본문을 로그로 남김
                                    .then(Mono.error(new RuntimeException("Member API 5xx 에러")))) // RuntimeException 발생
                    .bodyToMono(MemberResponse.class) // 응답 본문을 MemberResponse DTO로 변환합니다.
                    .timeout(Duration.ofSeconds(5)) // API 호출에 대한 타임아웃을 5초로 설정합니다. (응답이 늦어질 경우를 대비)
                    .block(); // 비동기 Mono<MemberResponse>를 블로킹하여 MemberResponse 객체를 즉시 반환합니다. (핸드셰이크 단계에서는 동기 처리가 필요)

            // Member API 응답이 유효하고 companyDomain이 비어있지 않은지 확인합니다.
            if (member != null && member.getCompanyDomain() != null && !member.getCompanyDomain().isBlank()) {
                log.info("companyDomain 조회 성공: {}", member.getCompanyDomain());
                return member.getCompanyDomain(); // 조회된 companyDomain을 반환합니다.
            }

            log.error("Member API 응답이 null이거나 companyDomain이 없음"); // 응답이 유효하지 않은 경우 오류 로그
            return null; // companyDomain을 찾지 못하면 null 반환

        } catch (Exception e) {
            // Member API 호출 중 예외(네트워크 오류, 타임아웃 등) 발생 시 오류 로그를 남깁니다.
            log.error("Member API 호출 실패: {}", e.getMessage());
            return null; // 호출 실패 시 null 반환
        }
    }
}