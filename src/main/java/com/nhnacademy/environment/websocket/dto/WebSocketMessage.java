// src/main/java/com/nhnacademy/environment/websocket/dto/WebSocketMessage.java
package com.nhnacademy.environment.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * WebSocket 메시지 전송을 위한 DTO 클래스.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {

    /**
     * 메시지 타입 (connection, realtime, error, subscribe, unsubscribe)
     */
    @JsonProperty("type")
    private String type;

    /**
     * 메시지 상태 (success, error, connected 등)
     */
    @JsonProperty("status")
    private String status;

    /**
     * 회사 도메인
     */
    @JsonProperty("companyDomain")
    private String companyDomain;

    /**
     * 측정 항목 (CPU, memory 등)
     */
    @JsonProperty("measurement")
    private String measurement;

    /**
     * 게이트웨이 ID
     */
    @JsonProperty("gatewayId")
    private String gatewayId;

    /**
     * 실제 데이터 (시계열 데이터 배열)
     */
    @JsonProperty("data")
    private Object data;

    /**
     * 타임스탬프
     */
    @JsonProperty("timestamp")
    private Long timestamp;

    /**
     * 에러 메시지
     */
    @JsonProperty("message")
    private String message;

    /**
     * 추가 정보 (동적 필드)
     */
    @JsonProperty("extra")
    private Map<String, Object> extra;

    /**
     * 연결 성공 메시지 생성
     */
    public static WebSocketMessage connectionSuccess(String companyDomain) {
        return WebSocketMessage.builder()
                .type("connection")
                .status("connected")
                .companyDomain(companyDomain)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 실시간 데이터 메시지 생성
     */
    public static WebSocketMessage realtimeData(String companyDomain, String measurement,
                                                String gatewayId, Object data) {
        return WebSocketMessage.builder()
                .type("realtime")
                .companyDomain(companyDomain)
                .measurement(measurement)
                .gatewayId(gatewayId)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 구독 성공 메시지 생성
     */
    public static WebSocketMessage subscribeSuccess(String measurement, String gatewayId, int interval) {
        return WebSocketMessage.builder()
                .type("subscribe")
                .status("success")
                .measurement(measurement)
                .gatewayId(gatewayId)
                .extra(Map.of("interval", interval))
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 에러 메시지 생성
     */
    public static WebSocketMessage error(String message) {
        return WebSocketMessage.builder()
                .type("error")
                .status("error")
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
