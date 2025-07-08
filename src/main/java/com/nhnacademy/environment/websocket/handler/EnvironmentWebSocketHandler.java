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
 * **실시간 환경 데이터를 클라이언트에게 전송하는 핵심 WebSocket 핸들러입니다.**
 *
 * 이 클래스는 클라이언트와의 웹소켓 연결의 생명주기를 관리하고,
 * 클라이언트로부터 수신되는 메시지(예: 데이터 구독 요청)를 처리하며,
 * InfluxDB와 같은 데이터 소스에서 실시간 데이터를 주기적으로 조회하여
 * 웹소켓을 통해 클라이언트에게 푸시(Push)하는 역할을 수행합니다.
 *
 * 주요 기능:
 * - WebSocket 연결 및 해제 관리 (세션 등록/제거)
 * - 클라이언트의 실시간 데이터 구독/해제 요청 처리
 * - 주기적인 InfluxDB 데이터 조회 및 클라이언트 전송
 * - 각 세션별로 독립적인 데이터 전송 스케줄 관리
 */
@Component // 이 클래스를 Spring 빈으로 등록하여 다른 컴포넌트에서 의존성 주입을 통해 사용할 수 있도록 합니다.
@Slf4j // Lombok 어노테이션: 로깅(Logging)을 위한 Logger 객체를 자동으로 생성해 줍니다.
@RequiredArgsConstructor // Lombok 어노테이션: final 필드들을 사용하여 생성자를 자동으로 생성합니다. (의존성 주입을 위해 사용)
public class EnvironmentWebSocketHandler extends TextWebSocketHandler { // 텍스트 기반의 웹소켓 메시지를 처리하기 위해 Spring의 TextWebSocketHandler를 상속받습니다.

    // TimeSeriesDataService: InfluxDB에서 시계열 데이터를 조회하고 가공하는 비즈니스 로직을 담당하는 서비스입니다.
    private final TimeSeriesDataService timeSeriesDataService;
    // TaskScheduler: 주기적인 작업을 예약하고 실행하는 Spring의 스케줄러입니다. 실시간 데이터 주기 전송에 사용됩니다.
    private final TaskScheduler webSocketTaskScheduler;
    // WebSocketSessionManager: 웹소켓 세션들을 중앙에서 관리하고 그룹화하는 역할을 하는 매니저 클래스입니다.
    private final WebSocketSessionManager sessionManager;

    /**
     * 각 웹소켓 세션별로 예약된 주기적인 데이터 전송 작업(스케줄)을 관리하는 맵입니다.
     * `Key`: 웹소켓 세션 ID (`sessionId`)
     * `Value`: 예약된 작업의 핸들러 (`ScheduledFuture<?>`) - 이 객체를 통해 스케줄 작업을 취소할 수 있습니다.
     * `ConcurrentHashMap`: 여러 스레드에서 동시에 접근해도 안전하도록(thread-safe) 설계된 맵입니다.
     */
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * **클라이언트와 웹소켓 연결이 성공적으로 수립된 직후 호출되는 메서드입니다.**
     *
     * 이 시점에서 웹소켓 세션의 초기 설정을 수행하고, 연결 성공 메시지를 클라이언트에게 보냅니다.
     *
     * @param session 현재 수립된 웹소켓 세션 객체입니다. 이 객체를 통해 클라이언트와 통신하고 세션 속성에 접근합니다.
     * @throws Exception 처리 중 발생할 수 있는 예외 (예: 메시지 전송 실패)
     */
    @Override // TextWebSocketHandler의 메서드를 오버라이드합니다.
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId(); // 현재 세션의 고유 ID를 가져옵니다.
        // 핸드셰이크 인터셉터(AuthHandshakeInterceptor)에서 저장한 companyDomain과 userEmail을 세션 속성에서 가져옵니다.
        String companyDomain = (String) session.getAttributes().get("companyDomain");
        String userEmail = (String) session.getAttributes().get("userEmail");

        // WebSocketSessionManager에 현재 세션을 등록합니다.
        // 이를 통해 모든 활성 세션을 중앙에서 관리하고, 나중에 특정 세션으로 메시지를 보내거나 세션 상태를 모니터링할 수 있습니다.
        sessionManager.addSession(session);

        // 연결 성공 정보를 로그로 남깁니다.
        log.info("WebSocket 연결 성공 - sessionId: {}, companyDomain: {}, userEmail: {}",
                sessionId, companyDomain, userEmail);

        // 클라이언트에게 웹소켓 연결이 성공했음을 알리는 메시지를 생성하여 전송합니다.
        // WebSocketMessage.connectionSuccess()는 미리 정의된 '연결 성공' 메시지 DTO를 생성합니다.
        WebSocketMessage connectionMsg = WebSocketMessage.connectionSuccess(companyDomain);
        sendMessage(session, connectionMsg); // sendMessage 헬퍼 메서드를 사용하여 메시지를 전송합니다.

        // 현재 활성 세션의 총 개수를 디버그 로그로 남겨 세션 관리 상태를 모니터링합니다.
        log.debug("현재 활성 세션 수: {}", sessionManager.getTotalSessionCount());
    }

    /**
     * **클라이언트로부터 텍스트 메시지(예: JSON)를 수신할 때마다 호출되는 메서드입니다.**
     *
     * 이 메서드는 수신된 메시지의 내용을 파싱하여 클라이언트의 요청(액션)을 식별하고,
     * 그에 따라 적절한 비즈니스 로직을 분기하여 처리합니다.
     *
     * @param session 현재 메시지를 보낸 클라이언트의 웹소켓 세션 객체입니다.
     * @param message 클라이언트로부터 수신된 텍스트 메시지 객체입니다. 메시지 내용은 {@code getPayload()}로 접근합니다.
     * @throws Exception 메시지 처리 중 발생할 수 있는 예외
     */
    @Override // TextWebSocketHandler의 메서드를 오버라이드합니다.
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId(); // 현재 세션의 고유 ID를 가져옵니다.
        // 세션 속성에서 companyDomain을 다시 가져옵니다.
        String companyDomain = (String) session.getAttributes().get("companyDomain");

        // 수신된 메시지의 내용을 디버그 로그로 남깁니다.
        log.debug("WebSocket 메시지 수신 - sessionId: {}, message: {}", sessionId, message.getPayload());

        try {
            // 수신된 JSON 메시지 페이로드(Payload)를 Map<String, Object> 형태로 파싱합니다.
            // JsonUtils.fromJson()은 JSON 문자열을 자바 객체로 변환하는 유틸리티 메서드입니다.
            Map<String, Object> request = JsonUtils.fromJson(message.getPayload(), Map.class);
            String action = (String) request.get("action"); // 메시지에서 'action' 필드의 값을 추출합니다.

            // 'action' 필드가 메시지에 포함되어 있지 않다면 오류 메시지를 클라이언트에 보냅니다.
            if (action == null) {
                sendErrorMessage(session, "action 필드가 필요합니다.");
                return; // 더 이상 처리하지 않고 메서드를 종료합니다.
            }

            // 'action' 값에 따라 다른 처리 로직을 수행합니다.
            switch (action) {
                case "subscribe": // 클라이언트가 실시간 데이터 구독을 요청한 경우
                    handleSubscribe(session, request, companyDomain); // 구독 처리 메서드 호출
                    break;
                case "unsubscribe": // 클라이언트가 실시간 데이터 구독 해제를 요청한 경우
                    handleUnsubscribe(session, request); // 구독 해제 처리 메서드 호출
                    break;
                case "ping": // 클라이언트가 연결 상태 확인(하트비트)을 요청한 경우
                    handlePing(session); // 핑 처리 메서드 호출 (pong으로 응답)
                    break;
                default: // 정의되지 않거나 알 수 없는 'action' 값이 수신된 경우
                    sendErrorMessage(session, "알 수 없는 액션입니다: " + action); // 오류 메시지 전송
            }

        } catch (Exception e) {
            // 메시지 파싱 또는 처리 중 예외가 발생한 경우 오류 로그를 남기고 클라이언트에 오류 메시지를 보냅니다.
            log.error("WebSocket 메시지 처리 중 오류 발생 - sessionId: {}", sessionId, e);
            sendErrorMessage(session, "메시지 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * **클라이언트의 실시간 데이터 구독 요청을 처리하는 메서드입니다.**
     *
     * 이 메서드는 클라이언트가 원하는 데이터(측정 항목, 게이트웨이 ID 등)와
     * 데이터 전송 주기(interval), 조회 범위(rangeMinutes)를 파싱하여,
     * 해당 세션에 대해 주기적인 데이터 조회 및 전송 스케줄을 예약합니다.
     *
     * @param session 현재 구독을 요청한 클라이언트의 웹소켓 세션.
     * @param request 클라이언트로부터 수신된 구독 요청 메시지(Map 형태).
     * @param companyDomain 해당 세션에 연결된 사용자의 회사 도메인.
     */
    private void handleSubscribe(WebSocketSession session, Map<String, Object> request, String companyDomain) {
        String sessionId = session.getId(); // 세션 ID
        String measurement = (String) request.get("measurement"); // 구독할 측정 항목 (예: "usage_idle", "temperature")
        String gatewayId = (String) request.get("gatewayId"); // 구독할 게이트웨이 ID (예: "cpu", "sensors")
        // 데이터 전송 주기(초 단위)를 요청에서 가져오거나, 기본값 10초를 사용합니다.
        Integer intervalSeconds = (Integer) request.getOrDefault("interval", 10);
        // 데이터 조회 범위(분 단위)를 요청에서 가져오거나, 기본값 3분을 사용합니다.
        Integer rangeMinutes = (Integer) request.getOrDefault("rangeMinutes", 3);

        // 이 세션에 대해 이전에 예약된 주기적인 데이터 전송 작업이 있다면 먼저 취소합니다.
        // 이는 클라이언트가 구독 파라미터를 변경하거나 재구독할 때 중복 스케줄링을 방지하기 위함입니다.
        cancelExistingSchedule(sessionId);

        // **실시간 데이터 전송 스케줄링 시작**:
        // webSocketTaskScheduler를 사용하여 지정된 intervalSeconds 주기로 작업을 반복 실행하도록 예약합니다.
        // ScheduledFuture<?> task: 예약된 작업에 대한 핸들러를 반환하며, 이를 통해 나중에 작업을 취소할 수 있습니다.
        ScheduledFuture<?> task = webSocketTaskScheduler.scheduleAtFixedRate(() -> {
            try {
                // 스케줄된 작업이 실행될 때, 현재 세션이 여전히 열려 있는지 확인합니다.
                // 세션이 닫혀있다면 더 이상 데이터를 보낼 필요가 없으므로 스케줄을 중단합니다.
                if (!session.isOpen()) {
                    log.warn("세션이 닫혀있어 스케줄 중단 - sessionId: {}", sessionId);
                    cancelExistingSchedule(sessionId); // 스케줄 작업을 취소 맵에서 제거합니다.
                    return; // 현재 실행을 중단합니다.
                }

                // TimeSeriesDataService를 호출하여 InfluxDB에서 실시간 데이터를 조회합니다.
                // companyDomain, measurement, gatewayId, 그리고 rangeMinutes를 필터로 사용하여
                // 클라이언트가 요청한 정확한 범위의 최신 데이터를 가져옵니다.
                List<TimeSeriesDataDto> data = timeSeriesDataService.getRealtimeData(
                        companyDomain, measurement, gatewayId, rangeMinutes
                );

                // 조회된 데이터 및 건수를 로그로 남깁니다.
                log.info("전송 직전 데이터: {}", data);
                log.info("조회된 데이터 건수: {} - sessionId: {}", data.size(), sessionId);

                // 조회된 데이터를 WebSocketMessage DTO로 래핑합니다.
                // type: "realtime"으로 설정하여 클라이언트가 실시간 데이터임을 인식하게 합니다.
                WebSocketMessage realtimeMsg = WebSocketMessage.realtimeData(
                        companyDomain, measurement, gatewayId, data
                );

                // WebSocketMessage DTO를 JSON 문자열로 변환합니다.
                String jsonMessage = JsonUtils.toJson(realtimeMsg);

                // 세션이 여전히 열려있는지 최종 확인 후, JSON 메시지를 클라이언트에게 전송합니다.
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                    log.info("실시간 데이터 직접 전송 완료 - sessionId: {}, 데이터 건수: {}",
                            sessionId, data.size());
                }

            } catch (Exception e) {
                // 실시간 데이터 조회 또는 전송 중 오류 발생 시 오류 로그를 남기고,
                // 클라이언트에게 오류 메시지를 보냅니다.
                log.error("실시간 데이터 전송 오류 - sessionId: {}", sessionId, e);
                sendErrorMessage(session, "실시간 데이터 전송 중 오류: " + e.getMessage());
                // 오류 발생 시 해당 세션의 스케줄을 취소하여 무한 반복을 방지합니다.
                cancelExistingSchedule(sessionId);
            }
        }, Duration.ofSeconds(intervalSeconds)); // 지정된 초(intervalSeconds)마다 이 작업을 반복 실행합니다.

        // 예약된 작업의 핸들러(ScheduledFuture)를 scheduledTasks 맵에 저장합니다.
        // 이는 나중에 이 작업을 취소할 때 사용됩니다.
        scheduledTasks.put(sessionId, task);

        // 클라이언트에게 구독 요청이 성공적으로 처리되었음을 알리는 응답 메시지를 보냅니다.
        WebSocketMessage subscribeMsg = WebSocketMessage.builder()
                .type("subscribe") // 메시지 타입: "subscribe"
                .status("success") // 상태: "success"
                .companyDomain(companyDomain)
                .measurement(measurement)
                .gatewayId(gatewayId)
                .timestamp(System.currentTimeMillis())
                .extra(Map.of("interval", intervalSeconds)) // 추가 정보로 전송 주기를 포함합니다.
                .build();

        sendMessage(session, subscribeMsg); // 구독 성공 메시지 전송

        // 구독 설정 완료 정보를 로그로 남깁니다.
        log.info("WebSocket subscribe: measurement={}, gatewayId={}, rangeMinutes={}", measurement, gatewayId, rangeMinutes);
        log.info("실시간 구독 설정 완료 (직접 전송 방식) - sessionId: {}", sessionId);
    }

    /**
     * **클라이언트의 실시간 데이터 구독 해제 요청을 처리하는 메서드입니다.**
     *
     * 클라이언트가 더 이상 특정 데이터 업데이트를 받고 싶지 않을 때 호출됩니다.
     * 이 메서드는 해당 세션의 주기적인 데이터 전송 스케줄을 취소하고,
     * 세션 관리자에게 구독이 해제되었음을 알립니다.
     *
     * @param session 현재 구독 해제를 요청한 클라이언트의 웹소켓 세션.
     * @param request 클라이언트로부터 수신된 구독 해제 요청 메시지(Map 형태).
     */
    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> request) {
        String sessionId = session.getId(); // 세션 ID
        String companyDomain = (String) session.getAttributes().get("companyDomain"); // 회사 도메인
        String measurement = (String) request.get("measurement"); // 구독 해제할 측정 항목
        String gatewayId = (String) request.get("gatewayId"); // 구독 해제할 게이트웨이 ID

        log.info("실시간 구독 해제 - sessionId: {}", sessionId);

        // WebSocketSessionManager에서 해당 세션의 토픽 구독을 해제합니다.
        // 이는 WebSocketSessionManager가 구독자 목록을 최신 상태로 유지하는 데 필요합니다.
        if (measurement != null && gatewayId != null) { // measurement와 gatewayId가 유효한 경우에만 해제 시도
            sessionManager.unsubscribeFromTopic(sessionId, companyDomain, measurement, gatewayId);
        }

        // 해당 세션에 대해 예약된 주기적인 데이터 전송 스케줄 작업을 취소합니다.
        // 이는 불필요한 서버 자원 소모를 막는 중요한 단계입니다.
        cancelExistingSchedule(sessionId);

        // 클라이언트에게 구독 해제 요청이 성공적으로 처리되었음을 알리는 응답 메시지를 보냅니다.
        WebSocketMessage unsubscribeMsg = WebSocketMessage.builder()
                .type("unsubscribe") // 메시지 타입: "unsubscribe"
                .status("success") // 상태: "success"
                .companyDomain(companyDomain) // 구독 해제 응답에도 companyDomain을 포함하여 클라이언트가 컨텍스트를 파악하도록 합니다.
                .timestamp(System.currentTimeMillis())
                .build();
        sendMessage(session, unsubscribeMsg); // 구독 해제 성공 메시지 전송
    }

    /**
     * **클라이언트의 Ping 요청을 처리하는 메서드입니다.**
     *
     * 클라이언트가 웹소켓 연결의 활성 상태를 확인하기 위해 주기적으로 'ping' 메시지를 보낼 때 호출됩니다.
     * 서버는 이에 대해 'pong' 메시지로 응답하여 연결이 살아있음을 알립니다.
     *
     * @param session Ping 요청을 보낸 클라이언트의 웹소켓 세션.
     */
    private void handlePing(WebSocketSession session) {
        // 'pong' 메시지 DTO를 생성합니다.
        WebSocketMessage pongMsg = WebSocketMessage.builder()
                .type("pong") // 메시지 타입: "pong"
                .status("success") // 상태: "success"
                .timestamp(System.currentTimeMillis())
                .build();
        sendMessage(session, pongMsg); // 'pong' 메시지 전송
    }

    /**
     * **클라이언트와의 WebSocket 연결이 종료될 때 호출되는 메서드입니다.**
     *
     * 이 메서드는 연결 종료의 원인(정상 종료, 오류 종료 등)과 관계없이 호출되며,
     * 연결이 끊어진 세션과 관련된 서버 자원들을 정리하는 중요한 역할을 합니다.
     *
     * @param session 종료된 웹소켓 세션 객체.
     * @param status  연결 종료의 상태 코드 및 이유를 포함하는 객체.
     * @throws Exception 처리 중 발생할 수 있는 예외.
     */
    @Override // TextWebSocketHandler의 메서드를 오버라이드합니다.
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId(); // 종료된 세션의 ID
        String companyDomain = (String) session.getAttributes().get("companyDomain"); // 세션에 저장된 회사 도메인

        // 연결 종료 정보를 로그로 남깁니다.
        log.info("WebSocket 연결 종료 - sessionId: {}, companyDomain: {}, status: {}",
                sessionId, companyDomain, status);

        // WebSocketSessionManager에서 해당 세션을 제거합니다.
        // 이 과정에서 WebSocketSessionManager는 해당 세션의 모든 토픽 구독도 자동으로 해제합니다.
        sessionManager.removeSession(session);

        // 해당 세션에 대해 예약된 모든 주기적인 데이터 전송 스케줄 작업을 취소합니다.
        // 이는 연결이 끊긴 세션에 대해 서버가 불필요한 자원(스레드, 메모리)을 계속 소모하는 것을 방지합니다.
        cancelExistingSchedule(sessionId);

        // 현재 활성 세션의 총 개수를 디버그 로그로 남겨 세션 관리 상태를 모니터링합니다.
        log.debug("현재 활성 세션 수: {}", sessionManager.getTotalSessionCount());
    }

    /**
     * **WebSocket 전송 중 오류가 발생할 때 호출되는 메서드입니다.**
     *
     * 네트워크 문제, 메시지 크기 제한 초과 등 웹소켓 통신 과정에서 예외가 발생했을 때 호출됩니다.
     * 오류 발생 시 해당 세션과 관련된 자원을 정리합니다.
     *
     * @param session 오류가 발생한 웹소켓 세션.
     * @param exception 발생한 예외 객체.
     * @throws Exception 처리 중 발생할 수 있는 예외.
     */
    @Override // TextWebSocketHandler의 메서드를 오버라이드합니다.
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId(); // 오류가 발생한 세션의 ID
        log.error("WebSocket 전송 오류 - sessionId: {}", sessionId, exception); // 오류 정보를 로그로 남깁니다.

        // 오류 발생 시 해당 세션을 WebSocketSessionManager에서 제거합니다.
        sessionManager.removeSession(session);
        // 해당 세션에 대해 예약된 모든 스케줄 작업을 취소합니다.
        cancelExistingSchedule(sessionId);
    }

    /**
     * **웹소켓 메시지에서 '부분 메시지(Partial Messages)'를 지원할지 여부를 반환합니다.**
     *
     * 부분 메시지는 하나의 큰 메시지를 여러 작은 조각으로 나누어 전송하는 방식입니다.
     * 이 프로젝트에서는 부분 메시지를 사용하지 않으므로 `false`를 반환합니다.
     *
     * @return 부분 메시지 지원 여부 (false: 지원 안함)
     */
    @Override // TextWebSocketHandler의 메서드를 오버라이드합니다.
    public boolean supportsPartialMessages() {
        return false; // 부분 메시지(Partial Messages)를 지원하지 않음을 명시합니다.
    }

    /**
     * **주어진 웹소켓 세션으로 {@link WebSocketMessage} DTO 객체를 전송하는 헬퍼 메서드입니다.**
     *
     * 메시지를 JSON 문자열로 변환하고, 세션이 열려있는지 확인한 후 전송합니다.
     * 전송 실패 시 오류를 로깅합니다.
     *
     * @param session 메시지를 전송할 웹소켓 세션.
     * @param message 전송할 {@link WebSocketMessage} 객체.
     */
    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            if (session.isOpen()) { // 세션이 여전히 열려있는지 확인합니다. (닫힌 세션에 메시지 전송 시 오류 발생 방지)
                String jsonMessage = JsonUtils.toJson(message); // WebSocketMessage 객체를 JSON 문자열로 변환합니다.
                session.sendMessage(new TextMessage(jsonMessage)); // JSON 문자열을 TextMessage로 감싸 세션에 전송합니다.
            }
        } catch (Exception e) {
            // 메시지 전송 중 오류가 발생하면 로그를 남깁니다. (예: 네트워크 문제, 세션이 갑자기 닫힘)
            log.error("WebSocket 메시지 전송 실패 - sessionId: {}", session.getId(), e);
        }
    }

    /**
     * **주어진 웹소켓 세션으로 에러 메시지를 전송하는 헬퍼 메서드입니다.**
     *
     * 클라이언트에게 발생한 오류를 명확하게 전달하기 위해 사용됩니다.
     *
     * @param session 에러 메시지를 전송할 웹소켓 세션.
     * @param errorMessage 클라이언트에게 전달할 오류 메시지 내용.
     */
    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        // WebSocketMessage.error() 팩토리 메서드를 사용하여 'error' 타입의 메시지 DTO를 생성합니다.
        WebSocketMessage errorMsg = WebSocketMessage.error(errorMessage);
        sendMessage(session, errorMsg); // 생성된 에러 메시지를 전송합니다.
    }

    /**
     * **특정 세션에 대해 현재 예약된 주기적인 스케줄 작업을 취소하고 맵에서 제거하는 헬퍼 메서드입니다.**
     *
     * 세션 연결 종료, 구독 해제, 또는 전송 오류 발생 시 호출되어
     * 불필요한 스케줄 작업이 계속 실행되는 것을 방지하고 자원을 해제합니다.
     *
     * @param sessionId 취소할 스케줄 작업과 관련된 세션의 ID.
     */
    private void cancelExistingSchedule(String sessionId) {
        // scheduledTasks 맵에서 해당 sessionId에 해당하는 예약된 작업(ScheduledFuture)을 가져오면서 제거합니다.
        ScheduledFuture<?> existingTask = scheduledTasks.remove(sessionId);
        // 만약 해당 세션에 예약된 작업이 존재하고, 아직 취소되지 않았다면
        if (existingTask != null && !existingTask.isCancelled()) {
            // 작업을 취소합니다. (true: 현재 실행 중인 경우에도 인터럽트하여 취소 시도, false: 실행 중이면 완료될 때까지 기다림)
            // 여기서는 false를 사용하여 현재 진행 중인 스케줄은 완료되도록 하고 다음 주기부터 실행되지 않도록 합니다.
            existingTask.cancel(false);
            log.debug("기존 스케줄 취소 완료 - sessionId: {}", sessionId); // 스케줄 취소 완료 로그
        }
    }
}