// src/main/java/com/nhnacademy/environment/websocket/handler/EnvironmentWebSocketHandler.java
package com.nhnacademy.environment.websocket.handler;

import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import com.nhnacademy.environment.websocket.dto.WebSocketMessage;
import com.nhnacademy.environment.websocket.manager.WebSocketSessionManager;
import com.nhnacademy.environment.websocket.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Environment 데이터 실시간 전송을 위한 WebSocket Handler
 *
 * - WebSocket 연결/해제 관리
 * - 실시간 데이터 구독/해제 처리
 * - InfluxDB 데이터 주기적 전송
 * - 세션별 스케줄링 관리
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EnvironmentWebSocketHandler extends TextWebSocketHandler {

    private final TimeSeriesDataService timeSeriesDataService;
    private final TaskScheduler webSocketTaskScheduler;
    private final WebSocketSessionManager sessionManager;

    // ★★★ 세션별 동기화 객체 관리 (검색 결과 [1] 참고) ★★★
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * WebSocket 연결 성공 시 호출
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        String companyDomain = (String) session.getAttributes().get("companyDomain");
        String userEmail = (String) session.getAttributes().get("userEmail");

        // ★★★ 세션별 동기화 객체 생성 (검색 결과 [1] 참고) ★★★
        sessionLocks.put(sessionId, new Object());

        // SessionManager에 세션 등록
        sessionManager.addSession(session);

        log.info("WebSocket 연결 성공 - sessionId: {}, companyDomain: {}, userEmail: {}",
                sessionId, companyDomain, userEmail);

        // 연결 성공 메시지 전송
        WebSocketMessage connectionMsg = WebSocketMessage.connectionSuccess(companyDomain);
        sendMessageSafely(session, connectionMsg);

        log.debug("현재 활성 세션 수: {}", sessionManager.getTotalSessionCount());
    }

    /**
     * 클라이언트로부터 메시지 수신 시 호출
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String companyDomain = (String) session.getAttributes().get("companyDomain");

        log.debug("WebSocket 메시지 수신 - sessionId: {}, message: {}", sessionId, message.getPayload());

        try {
            // JSON 메시지 파싱
            Map<String, Object> request = JsonUtils.fromJson(message.getPayload(), Map.class);
            String action = (String) request.get("action");

            if (action == null) {
                sendErrorMessageSafely(session, "action 필드가 필요합니다.");
                return;
            }

            // 액션별 처리
            switch (action) {
                case "subscribe":
                    handleSubscribe(session, request, companyDomain);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session, request);
                    break;
                case "ping":
                    handlePing(session);
                    break;
                default:
                    sendErrorMessageSafely(session, "알 수 없는 액션입니다: " + action);
            }

        } catch (Exception e) {
            log.error("WebSocket 메시지 처리 중 오류 발생 - sessionId: {}", sessionId, e);
            sendErrorMessageSafely(session, "메시지 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 실시간 데이터 구독 처리
     */
    private void handleSubscribe(WebSocketSession session, Map<String, Object> request, String companyDomain) {
        String sessionId = session.getId();
        String measurement = (String) request.get("measurement");
        String gatewayId = (String) request.get("gatewayId");
        Integer intervalSeconds = (Integer) request.getOrDefault("interval", 10); // ★★★ 기본 10초 ★★★

        // 입력값 검증
        if (measurement == null || gatewayId == null) {
            sendErrorMessageSafely(session, "measurement와 gatewayId가 필요합니다.");
            return;
        }

        if (intervalSeconds < 5 || intervalSeconds > 60) {
            sendErrorMessageSafely(session, "interval은 5-60초 사이여야 합니다.");
            return;
        }

        log.info("실시간 구독 시작 - sessionId: {}, measurement: {}, gatewayId: {}, companyDomain: {}, interval: {}초",
                sessionId, measurement, gatewayId, companyDomain, intervalSeconds);

        // SessionManager에 토픽 구독 등록
        sessionManager.subscribeToTopic(sessionId, companyDomain, measurement, gatewayId);

        // 기존 스케줄 취소
        cancelExistingSchedule(sessionId);

        // ★★★ 새로운 실시간 데이터 전송 스케줄 시작 (검색 결과 [1] 참고) ★★★
        ScheduledFuture<?> task = webSocketTaskScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!session.isOpen()) {
                    log.debug("세션이 닫혀있어 스케줄 중단 - sessionId: {}", sessionId);
                    cancelExistingSchedule(sessionId);
                    return;
                }

                // InfluxDB에서 실시간 데이터 조회
                List<TimeSeriesDataDto> data = timeSeriesDataService.getRealtimeData(
                        companyDomain, measurement, gatewayId
                );

                log.info("전송 직전 데이터: {}", data);

                log.info("조회된 데이터 건수: {} - sessionId: {}", data.size(), sessionId);

                // WebSocket 메시지 생성
                WebSocketMessage realtimeMsg = WebSocketMessage.realtimeData(
                        companyDomain, measurement, gatewayId, data
                );

                // ★★★ 동기화된 직접 세션 전송 (검색 결과 [1] 참고) ★★★
                sendMessageSafely(session, realtimeMsg);

                log.info("실시간 데이터 전송 완료 - sessionId: {}, 데이터 건수: {}",
                        sessionId, data.size());

            } catch (Exception e) {
                log.error("실시간 데이터 전송 오류 - sessionId: {}", sessionId, e);
                sendErrorMessageSafely(session, "실시간 데이터 전송 중 오류: " + e.getMessage());
            }
        }, Duration.ofSeconds(intervalSeconds));

        scheduledTasks.put(sessionId, task);

        // 구독 성공 응답
        WebSocketMessage subscribeMsg = WebSocketMessage.subscribeSuccess(measurement, gatewayId, intervalSeconds);
        sendMessageSafely(session, subscribeMsg);

        log.info("실시간 구독 설정 완료 - sessionId: {}", sessionId);
    }

    /**
     * 실시간 데이터 구독 해제 처리
     */
    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> request) {
        String sessionId = session.getId();
        String companyDomain = (String) session.getAttributes().get("companyDomain");
        String measurement = (String) request.get("measurement");
        String gatewayId = (String) request.get("gatewayId");

        log.info("실시간 구독 해제 - sessionId: {}", sessionId);

        // SessionManager에서 토픽 구독 해제
        if (measurement != null && gatewayId != null) {
            sessionManager.unsubscribeFromTopic(sessionId, companyDomain, measurement, gatewayId);
        }

        // 스케줄 작업 취소
        cancelExistingSchedule(sessionId);

        // 구독 해제 성공 응답
        WebSocketMessage unsubscribeMsg = WebSocketMessage.builder()
                .type("unsubscribe")
                .status("success")
                .timestamp(System.currentTimeMillis())
                .build();
        sendMessageSafely(session, unsubscribeMsg);
    }

    /**
     * Ping 요청 처리 (연결 상태 확인)
     */
    private void handlePing(WebSocketSession session) {
        WebSocketMessage pongMsg = WebSocketMessage.builder()
                .type("pong")
                .status("success")
                .timestamp(System.currentTimeMillis())
                .build();
        sendMessageSafely(session, pongMsg);
    }

    /**
     * WebSocket 연결 종료 시 호출
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        String companyDomain = (String) session.getAttributes().get("companyDomain");

        log.info("WebSocket 연결 종료 - sessionId: {}, companyDomain: {}, status: {}",
                sessionId, companyDomain, status);

        // SessionManager에서 세션 제거 (구독도 자동으로 해제됨)
        sessionManager.removeSession(session);

        // 스케줄 작업 정리
        cancelExistingSchedule(sessionId);

        // ★★★ 동기화 객체 정리 (검색 결과 [1] 참고) ★★★
        sessionLocks.remove(sessionId);

        log.debug("현재 활성 세션 수: {}", sessionManager.getTotalSessionCount());
    }

    /**
     * WebSocket 전송 오류 시 호출
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        log.error("WebSocket 전송 오류 - sessionId: {}", sessionId, exception);

        // 오류 발생 시 세션 정리
        sessionManager.removeSession(session);
        cancelExistingSchedule(sessionId);
        sessionLocks.remove(sessionId);
    }

    /**
     * 부분 메시지 지원 여부
     */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    // ==================== 헬퍼 메소드들 ====================

    /**
     * ★★★ 동기화된 메시지 전송 메소드 (검색 결과 [1] 참고) ★★★
     */
    private void sendMessageSafely(WebSocketSession session, WebSocketMessage message) {
        String sessionId = session.getId();
        Object lock = sessionLocks.get(sessionId);

        if (lock == null) {
            log.warn("세션 동기화 객체를 찾을 수 없습니다 - sessionId: {}", sessionId);
            return;
        }

        synchronized (lock) {
            try {
                if (session.isOpen()) {
                    String jsonMessage = JsonUtils.toJson(message);
                    session.sendMessage(new TextMessage(jsonMessage));
                    log.debug("메시지 전송 성공 - sessionId: {}, type: {}", sessionId, message.getType());
                } else {
                    log.warn("세션이 닫혀있어 메시지 전송 실패 - sessionId: {}", sessionId);
                }
            } catch (Exception e) {
                log.error("WebSocket 메시지 전송 실패 - sessionId: {}", sessionId, e);
            }
        }
    }

    /**
     * ★★★ 동기화된 에러 메시지 전송 ★★★
     */
    private void sendErrorMessageSafely(WebSocketSession session, String errorMessage) {
        WebSocketMessage errorMsg = WebSocketMessage.error(errorMessage);
        sendMessageSafely(session, errorMsg);
    }

    /**
     * 기존 스케줄 취소
     */
    private void cancelExistingSchedule(String sessionId) {
        ScheduledFuture<?> existingTask = scheduledTasks.remove(sessionId);
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel(false);
            log.debug("기존 스케줄 취소 완료 - sessionId: {}", sessionId);
        }
    }
}
