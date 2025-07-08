// src/main/java/com/nhnacademy/environment/config/WebSocketConfig.java
package com.nhnacademy.environment.config;

import com.nhnacademy.environment.websocket.handler.EnvironmentWebSocketHandler;
import com.nhnacademy.environment.websocket.interceptor.AuthHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * **애플리케이션의 웹소켓 통신을 설정하는 핵심 Configuration 클래스입니다.**
 *
 * 이 클래스는 Spring Framework에게 웹소켓 서버를 구동하고,
 * 어떤 경로로 웹소켓 연결 요청을 받아들일지, 그리고 그 요청들을 어떤 핸들러(Handler)가 처리할지,
 * 어떤 보안 검증(Interceptor)을 거칠지 등을 명시적으로 정의합니다.
 *
 * 주요 역할은 다음과 같습니다:
 * - **웹소켓 기능 활성화**: Spring 애플리케이션 내에서 웹소켓 기능을 사용할 수 있도록 설정합니다.
 * - **웹소켓 핸들러 등록**: 특정 URL 경로와 웹소켓 핸들러를 연결하여 클라이언트 요청을 처리하게 합니다.
 * - **핸드셰이크 인터셉터 설정**: 웹소켓 연결 수립 초기 단계(핸드셰이크)에서 인증 및 추가 로직을 적용합니다.
 * - **CORS 설정**: 웹소켓 연결에 대한 Cross-Origin Resource Sharing (CORS) 정책을 정의합니다.
 * - **Task Scheduler 설정 (간접적)**: 비록 직접적인 `@Bean` 정의는 아니지만, 웹소켓 핸들러에서 사용될 `TaskScheduler`와 같은 다른 빈들을 통해 웹소켓 관련 비동기 작업을 지원합니다.
 */
@Configuration // 이 클래스가 Spring의 설정(Configuration) 파일임을 나타냅니다. Spring 컨테이너가 이 클래스를 읽어들여 빈들을 등록하고 웹소켓 인프라를 구성합니다.
@EnableWebSocket // Spring에게 이 애플리케이션에서 웹소켓 기능을 활성화하라고 지시합니다. 이 어노테이션이 없으면 웹소켓 서버가 구동되지 않습니다.
@RequiredArgsConstructor // Lombok 어노테이션: final 필드에 대한 생성자를 자동으로 생성해 주입(Dependency Injection)을 용이하게 합니다.
@Slf4j // Lombok 어노테이션: 로깅을 위한 Logger 객체를 자동으로 생성해 줍니다. (예: log.info(), log.debug())
public class WebSocketConfig implements WebSocketConfigurer { // WebSocketConfigurer 인터페이스를 구현하여 웹소켓 설정을 커스터마이징합니다.

    // AuthHandshakeInterceptor는 웹소켓 연결 수립 전 인증/권한 부여 로직을 처리하는 인터셉터입니다.
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    // EnvironmentWebSocketHandler는 실제 웹소켓 메시지 통신을 처리하는 핵심 핸들러입니다.
    @Lazy // 이 빈(EnvironmentWebSocketHandler)이 애플리케이션 시작 시 바로 생성되지 않고, 처음 사용될 때(즉, 웹소켓 연결 요청이 들어올 때) 지연(Lazy) 초기화되도록 설정합니다.
    private final EnvironmentWebSocketHandler environmentWebSocketHandler;

    /**
     * **웹소켓 핸들러와 해당 핸들러가 처리할 URL 경로를 등록하는 핵심 메서드입니다.**
     *
     * 이 메서드는 Spring Framework에 의해 자동으로 호출되며, 웹소켓 서비스의 진입점을 정의합니다.
     *
     * @param registry 웹소켓 핸들러를 등록하고 관련 설정을 구성할 수 있는 객체입니다.
     */
    @Override // WebSocketConfigurer 인터페이스의 추상 메서드를 오버라이드합니다.
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("WebSocket 핸들러 등록 시작"); // 웹소켓 핸들러 등록 과정의 시작을 알리는 로그를 남깁니다.

        // 클라이언트가 "/ws/environment" 경로로 웹소켓 연결을 요청할 때, environmentWebSocketHandler가 이를 처리하도록 등록합니다.
        registry.addHandler(environmentWebSocketHandler, "/ws/environment")
                // 웹소켓 핸드셰이크(Handshake, 연결 수립 초기 단계)가 시작되기 전에 authHandshakeInterceptor를 적용합니다.
                // 이는 웹소켓 연결 전 인증, 권한 확인 등의 사전 검증 로직을 수행하게 하여 보안을 강화합니다.
                .addInterceptors(authHandshakeInterceptor)
                // 모든 도메인("*")으로부터의 웹소켓 연결 요청을 허용합니다.
                // 이는 개발 환경에서 CORS(Cross-Origin Resource Sharing) 문제를 방지하는 데 유용하지만,
                // 실제 운영 환경에서는 보안을 위해 특정 허용 도메인(예: "https://yourdomain.com")을 명시하는 것이 일반적입니다.
                .setAllowedOrigins("*");

        log.info("WebSocket 핸들러 등록 완료"); // 웹소켓 핸들러 등록 과정의 완료를 알리는 로그를 남깁니다.
    }

    // TaskScheduler, InfluxDBClient 등 웹소켓 통신에 필요한 다른 빈들은 별도의 Configuration 클래스(예: TaskSchedulerConfig, InfluxDBConfig)에서 정의될 수 있습니다.
    // 이는 각 컴포넌트의 책임을 분리하고 코드의 모듈성을 높이는 좋은 설계 방식입니다.
}