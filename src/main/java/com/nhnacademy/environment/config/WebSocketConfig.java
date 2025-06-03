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
 * WebSocket 설정을 위한 Configuration 클래스
 *
 * - WebSocket 핸들러 등록
 * - Handshake Interceptor 설정
 * - CORS 설정
 * - Task Scheduler 설정
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketConfigurer {

    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    @Lazy
    private final EnvironmentWebSocketHandler environmentWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("WebSocket 핸들러 등록 시작");

        registry.addHandler(environmentWebSocketHandler, "/ws/environment")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");

        log.info("WebSocket 핸들러 등록 완료");
    }
}
