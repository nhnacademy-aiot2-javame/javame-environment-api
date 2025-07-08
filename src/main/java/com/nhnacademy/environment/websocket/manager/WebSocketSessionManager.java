package com.nhnacademy.environment.websocket.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * **웹소켓(WebSocket) 세션들을 중앙에서 효율적으로 관리하는 매니저 클래스입니다.**
 *
 * 이 클래스는 애플리케이션에 연결된 모든 활성 웹소켓 세션의 상태를 추적하고,
 * 세션들을 다양한 기준(예: 회사별, 특정 데이터 구독 토픽별)으로 그룹화하여 관리합니다.
 * 이를 통해 특정 클라이언트에게 메시지를 보내거나, 특정 그룹의 클라이언트들에게만
 * 메시지를 브로드캐스트(broadcast)하는 등의 기능을 효율적으로 수행할 수 있습니다.
 *
 * 주요 기능:
 * - **활성 세션 관리**: 현재 연결된 모든 웹소켓 세션을 추가하고 제거합니다.
 * - **회사별 세션 그룹화**: 각 세션을 소속 회사 도메인(companyDomain)별로 묶어 관리합니다.
 * - **측정 항목별 구독 세션 관리**: 클라이언트가 특정 데이터(토픽)를 구독할 때, 해당 토픽을 구독한 세션들을 관리합니다.
 * - **다양한 브로드캐스트 메시지 전송**: 개별 세션, 특정 회사 그룹, 특정 토픽 구독자 그룹, 또는 전체 세션에 메시지를 보낼 수 있습니다.
 * - **세션 상태 모니터링**: 현재 활성 세션 수, 회사/토픽별 세션 수 등 세션 상태에 대한 요약 정보를 제공합니다.
 *
 * Spring의 `@Component`로 등록되어 다른 서비스나 핸들러에서 주입받아 사용됩니다.
 */
@Component // 이 클래스를 Spring 빈으로 등록하여 애플리케이션 전역에서 관리하고 의존성 주입이 가능하게 합니다.
@Slf4j // Lombok 어노테이션: 로깅(Logging)을 위한 Logger 객체를 자동으로 생성해 줍니다.
public class WebSocketSessionManager {

    /**
     * **모든 활성 WebSocket 세션을 저장하는 맵입니다.**
     *
     * - **Key**: 웹소켓 세션의 고유 ID (`sessionId`)
     * - **Value**: 실제 `WebSocketSession` 객체
     * `ConcurrentHashMap`: 여러 스레드에서 동시에 세션 추가/제거/조회 작업을 수행해도 안전하도록(thread-safe) 설계된 맵입니다.
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * **회사 도메인(companyDomain)별로 웹소켓 세션들을 그룹화하는 맵입니다.**
     *
     * - **Key**: 회사 도메인 문자열 (예: "nhnacademy")
     * - **Value**: 해당 회사에 속하는 모든 `sessionId`의 {@link Set} (중복 없는 컬렉션)
     * `ConcurrentHashMap`: thread-safe 하며, 특정 회사에 속한 모든 세션에 메시지를 브로드캐스트할 때 효율적으로 사용됩니다.
     */
    private final Map<String, Set<String>> companySessionGroups = new ConcurrentHashMap<>();

    /**
     * **특정 측정 항목(measurement) 토픽을 구독하는 세션들을 관리하는 맵입니다.**
     *
     * - **Key**: 토픽을 식별하는 문자열 (예: "nhnacademy:cpu:usage_idle") - {@link #buildTopicKey} 메서드로 생성됩니다.
     * - **Value**: 해당 토픽을 구독하는 모든 `sessionId`의 {@link Set}
     * `ConcurrentHashMap`: thread-safe 하며, 특정 데이터가 업데이트될 때 해당 데이터를 구독하는 세션들에게만 메시지를 정밀하게 전송하는 데 사용됩니다.
     */
    private final Map<String, Set<String>> topicSubscriptions = new ConcurrentHashMap<>();

    /**
     * **새로운 WebSocket 세션을 매니저에 추가합니다.**
     *
     * 세션을 전체 활성 세션 목록에 등록하고, 세션의 `companyDomain` 정보를 기반으로
     * 회사별 그룹에도 추가하여 이후 메시지 전송 및 관리에 활용합니다.
     *
     * @param session 추가할 {@link WebSocketSession} 객체.
     */
    public void addSession(WebSocketSession session) {
        String sessionId = session.getId(); // 세션의 고유 ID를 가져옵니다.
        // 웹소켓 핸드셰이크 과정(AuthHandshakeInterceptor)에서 세션 속성에 저장된 companyDomain과 userEmail을 가져옵니다.
        String companyDomain = (String) session.getAttributes().get("companyDomain");
        String userEmail = (String) session.getAttributes().get("userEmail");

        // 1. 모든 활성 세션을 저장하는 'sessions' 맵에 세션을 추가합니다.
        sessions.put(sessionId, session);

        // 2. 회사 도메인 정보가 있는 경우에만 'companySessionGroups' 맵에 세션을 추가합니다.
        if (companyDomain != null) {
            // companyDomain에 해당하는 Set이 없으면 새로 생성하고, 있으면 기존 Set에 sessionId를 추가합니다.
            companySessionGroups.computeIfAbsent(companyDomain, k -> ConcurrentHashMap.newKeySet())
                    .add(sessionId);
        }

        // 세션 추가 완료 정보를 로그로 남겨 현재 세션 수 등을 모니터링합니다.
        log.info("세션 추가 완료 - sessionId: {}, companyDomain: {}, userEmail: {}, 총 세션 수: {}",
                sessionId, companyDomain, userEmail, sessions.size());
    }

    /**
     * **지정된 WebSocket 세션을 매니저에서 제거합니다.**
     *
     * 세션을 모든 활성 세션 목록과 회사별 그룹, 그리고 모든 구독 토픽 목록에서 제거하여
     * 자원을 정리하고 메모리 누수를 방지합니다. 이 메서드는 연결이 종료될 때 호출되어야 합니다.
     *
     * @param session 제거할 {@link WebSocketSession} 객체.
     */
    public void removeSession(WebSocketSession session) {
        String sessionId = session.getId(); // 제거할 세션의 ID를 가져옵니다.
        String companyDomain = (String) session.getAttributes().get("companyDomain"); // 세션 속성에서 companyDomain을 가져옵니다.

        // 1. 'sessions' 맵에서 해당 세션을 제거합니다.
        sessions.remove(sessionId);

        // 2. 'companySessionGroups' 맵에서 해당 세션을 제거합니다.
        if (companyDomain != null) {
            Set<String> companySessions = companySessionGroups.get(companyDomain);
            if (companySessions != null) {
                companySessions.remove(sessionId); // 해당 회사 그룹에서 sessionId를 제거합니다.
                // 만약 특정 회사의 모든 세션이 제거되어 해당 Set이 비게 되면, 맵에서 해당 회사 엔트리를 제거합니다.
                if (companySessions.isEmpty()) {
                    companySessionGroups.remove(companyDomain);
                }
            }
        }

        // 3. 'topicSubscriptions' 맵에 있는 모든 구독 토픽에서 해당 세션을 제거합니다.
        // 클라이언트가 어떤 토픽들을 구독했었는지 일일이 알 필요 없이, 모든 구독 목록을 순회하며 제거합니다.
        topicSubscriptions.values().forEach(subscribers -> subscribers.remove(sessionId));

        // 4. 세션 제거 후, 구독자(subscribers)가 없어서 비어버린 토픽 엔트리를 'topicSubscriptions' 맵에서 제거합니다.
        // 이는 불필요한 빈 맵 엔트리를 정리하여 메모리를 최적화합니다.
        topicSubscriptions.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // 세션 제거 완료 정보를 로그로 남깁니다.
        log.info("세션 제거 완료 - sessionId: {}, companyDomain: {}, 총 세션 수: {}",
                sessionId, companyDomain, sessions.size());
    }

    /**
     * **지정된 세션을 특정 데이터 토픽에 구독(Subscribe)시킵니다.**
     *
     * 클라이언트가 특정 측정 항목(measurement)의 실시간 업데이트를 받고 싶을 때 호출됩니다.
     * 토픽 키는 `companyDomain:measurement:gatewayId` 형태로 생성됩니다.
     *
     * @param sessionId 구독할 세션의 ID.
     * @param companyDomain 토픽과 관련된 회사 도메인.
     * @param measurement 토픽과 관련된 측정 항목 (예: "usage_idle").
     * @param gatewayId 토픽과 관련된 게이트웨이 ID (예: "cpu", "sensors").
     */
    public void subscribeToTopic(String sessionId, String companyDomain, String measurement, String gatewayId) {
        // 주어진 정보로 토픽의 고유 키를 생성합니다. (예: "nhnacademy:cpu:usage_idle")
        String topicKey = buildTopicKey(companyDomain, measurement, gatewayId);

        // 'topicSubscriptions' 맵에서 해당 topicKey에 해당하는 Set을 찾거나 새로 생성하고, sessionId를 추가합니다.
        topicSubscriptions.computeIfAbsent(topicKey, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);

        // 토픽 구독 완료 정보를 로그로 남깁니다.
        log.info("토픽 구독 - sessionId: {}, topic: {}", sessionId, topicKey);
    }

    /**
     * **지정된 세션을 특정 데이터 토픽에서 구독 해제(Unsubscribe)시킵니다.**
     *
     * 클라이언트가 더 이상 특정 데이터 업데이트를 받고 싶지 않을 때 호출됩니다.
     *
     * @param sessionId 구독 해제할 세션의 ID.
     * @param companyDomain 토픽과 관련된 회사 도메인.
     * @param measurement 토픽과 관련된 측정 항목.
     * @param gatewayId 토픽과 관련된 게이트웨이 ID.
     */
    public void unsubscribeFromTopic(String sessionId, String companyDomain, String measurement, String gatewayId) {
        // 구독 해제할 토픽의 고유 키를 생성합니다.
        String topicKey = buildTopicKey(companyDomain, measurement, gatewayId);

        // 'topicSubscriptions' 맵에서 해당 topicKey에 해당하는 구독자 Set을 가져옵니다.
        Set<String> subscribers = topicSubscriptions.get(topicKey);
        if (subscribers != null) {
            subscribers.remove(sessionId); // Set에서 sessionId를 제거합니다.
            // 만약 구독자 Set이 비게 되면, 맵에서 해당 토픽 엔트리를 제거합니다.
            if (subscribers.isEmpty()) {
                topicSubscriptions.remove(topicKey);
            }
        }

        // 토픽 구독 해제 완료 정보를 로그로 남깁니다.
        log.info("토픽 구독 해제 - sessionId: {}, topic: {}", sessionId, topicKey);
    }

    /**
     * **특정 WebSocket 세션에게 메시지를 전송합니다.**
     *
     * 세션이 유효하고 열려 있는 경우에만 메시지를 전송하며, 전송 실패 시 해당 세션을 정리합니다.
     * 이는 다른 브로드캐스트 메서드들이 내부적으로 사용하는 기본 전송 메서드입니다.
     *
     * @param sessionId 메시지를 전송할 세션의 ID.
     * @param message 전송할 텍스트 메시지 내용.
     * @return 메시지 전송 성공 여부 (true: 성공, false: 실패).
     */
    public boolean sendMessageToSession(String sessionId, String message) {
        // 'sessions' 맵에서 sessionId에 해당하는 WebSocketSession 객체를 가져옵니다.
        WebSocketSession session = sessions.get(sessionId);
        // 세션이 존재하고 (null이 아니고) 현재 열려있는 상태인지 확인합니다.
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message)); // 텍스트 메시지를 생성하여 세션에 전송합니다.
                log.debug("메시지 전송 성공 - sessionId: {}", sessionId);
                return true; // 전송 성공
            } catch (IOException e) {
                // 메시지 전송 중 IO 예외(예: 클라이언트 연결 끊김) 발생 시 오류 로그를 남깁니다.
                log.error("메시지 전송 실패 - sessionId: {}", sessionId, e);
                // 전송 실패한 세션은 더 이상 유효하지 않을 수 있으므로, 매니저에서 제거하여 정리합니다.
                removeSession(session);
            }
        }
        return false; // 세션이 없거나 닫혀있거나 전송 실패
    }

    /**
     * **특정 회사 도메인(`companyDomain`)에 속한 모든 활성 세션에게 메시지를 브로드캐스트합니다.**
     *
     * 이 메서드는 특정 회사 내부의 모든 사용자에게 알림이나 업데이트를 보낼 때 유용합니다.
     *
     * @param companyDomain 메시지를 보낼 회사의 도메인.
     * @param message 전송할 텍스트 메시지 내용.
     * @return 메시지 전송에 성공한 세션의 수.
     */
    public int sendMessageToCompany(String companyDomain, String message) {
        // 'companySessionGroups' 맵에서 해당 companyDomain에 속한 sessionId들의 Set을 가져옵니다.
        Set<String> companySessions = companySessionGroups.get(companyDomain);
        // 해당 회사에 활성 세션이 없거나 Set이 비어있으면 메시지 전송을 건너뜁니다.
        if (companySessions == null || companySessions.isEmpty()) {
            log.debug("회사별 브로드캐스트 - 활성 세션 없음: {}", companyDomain);
            return 0; // 전송 성공 0
        }

        int successCount = 0; // 메시지 전송에 성공한 세션의 수를 카운트합니다.
        // 해당 회사에 속한 모든 sessionId를 순회하며 각 세션에 메시지를 전송합니다.
        for (String sessionId : companySessions) {
            if (sendMessageToSession(sessionId, message)) { // sendMessageToSession 헬퍼 메서드를 호출합니다.
                successCount++; // 성공 시 카운트 증가
            }
        }

        // 회사별 브로드캐스트 완료 정보를 로그로 남깁니다.
        log.info("회사별 브로드캐스트 완료 - companyDomain: {}, 대상: {}개, 성공: {}개",
                companyDomain, companySessions.size(), successCount);
        return successCount; // 성공한 세션 수 반환
    }

    /**
     * **특정 데이터 토픽(topic)을 구독하는 모든 활성 세션에게 메시지를 브로드캐스트합니다.**
     *
     * 예를 들어, "CPU 사용률" 토픽을 구독하는 모든 사용자에게 최신 CPU 사용률 데이터를 보낼 때 사용됩니다.
     * 이는 가장 세밀한 단위의 메시지 분배 방식입니다.
     *
     * @param companyDomain 토픽과 관련된 회사 도메인.
     * @param measurement 토픽과 관련된 측정 항목.
     * @param gatewayId 토픽과 관련된 게이트웨이 ID.
     * @param message 전송할 텍스트 메시지 내용.
     * @return 메시지 전송에 성공한 세션의 수.
     */
    public int sendMessageToTopic(String companyDomain, String measurement, String gatewayId, String message) {
        // 토픽의 고유 키를 생성합니다.
        String topicKey = buildTopicKey(companyDomain, measurement, gatewayId);
        // 'topicSubscriptions' 맵에서 해당 topicKey에 해당하는 구독자 Set을 가져옵니다.
        Set<String> subscribers = topicSubscriptions.get(topicKey);

        // 해당 토픽에 구독자가 없거나 Set이 비어있으면 메시지 전송을 건너뜁니다.
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("토픽 브로드캐스트 - 구독자 없음: {}", topicKey);
            return 0; // 전송 성공 0
        }

        int successCount = 0; // 메시지 전송에 성공한 세션의 수를 카운트합니다.
        // 해당 토픽을 구독하는 모든 sessionId를 순회하며 각 세션에 메시지를 전송합니다.
        for (String sessionId : subscribers) {
            if (sendMessageToSession(sessionId, message)) { // sendMessageToSession 헬퍼 메서드를 호출합니다.
                successCount++; // 성공 시 카운트 증가
            }
        }

        // 토픽별 브로드캐스트 완료 정보를 로그로 남깁니다.
        log.info("토픽 브로드캐스트 완료 - topic: {}, 대상: {}개, 성공: {}개",
                topicKey, subscribers.size(), successCount);
        return successCount; // 성공한 세션 수 반환
    }

    /**
     * **현재 매니저가 관리하는 모든 활성 WebSocket 세션에게 메시지를 브로드캐스트합니다.**
     *
     * 이는 전체 서비스 공지사항이나 모든 사용자에게 전달되어야 하는 업데이트에 사용될 수 있습니다.
     *
     * @param message 전송할 텍스트 메시지 내용.
     * @return 메시지 전송에 성공한 세션의 수.
     */
    public int broadcastToAll(String message) {
        int successCount = 0; // 메시지 전송에 성공한 세션의 수를 카운트합니다.
        // 'sessions' 맵에 있는 모든 sessionId를 순회하며 각 세션에 메시지를 전송합니다.
        for (String sessionId : sessions.keySet()) {
            if (sendMessageToSession(sessionId, message)) { // sendMessageToSession 헬퍼 메서드를 호출합니다.
                successCount++; // 성공 시 카운트 증가
            }
        }

        // 전체 브로드캐스트 완료 정보를 로그로 남깁니다.
        log.info("전체 브로드캐스트 완료 - 대상: {}개, 성공: {}개", sessions.size(), successCount);
        return successCount; // 성공한 세션 수 반환
    }

    /**
     * **현재 매니저가 관리하는 총 활성 세션의 개수를 반환합니다.**
     *
     * @return 총 활성 세션의 개수.
     */
    public int getTotalSessionCount() {
        return sessions.size();
    }

    /**
     * **특정 회사 도메인(`companyDomain`)에 속한 활성 세션의 개수를 반환합니다.**
     *
     * @param companyDomain 개수를 조회할 회사 도메인.
     * @return 해당 회사에 속한 활성 세션의 개수. (세션이 없으면 0 반환)
     */
    public int getCompanySessionCount(String companyDomain) {
        Set<String> companySessions = companySessionGroups.get(companyDomain);
        return companySessions != null ? companySessions.size() : 0;
    }

    /**
     * **현재 활성 세션이 존재하는 모든 회사 도메인의 {@link Set}을 반환합니다.**
     *
     * @return 활성 세션이 있는 회사 도메인들의 집합.
     */
    public Set<String> getActiveCompanies() {
        return companySessionGroups.keySet();
    }

    /**
     * **현재 구독 중인 모든 토픽 키({@link #buildTopicKey}로 생성된 문자열)의 {@link Set}을 반환합니다.**
     *
     * @return 활성 토픽 키들의 집합.
     */
    public Set<String> getActiveTopics() {
        return topicSubscriptions.keySet();
    }

    /**
     * **특정 데이터 토픽을 구독하는 활성 세션의 개수를 반환합니다.**
     *
     * @param companyDomain 토픽과 관련된 회사 도메인.
     * @param measurement 토픽과 관련된 측정 항목.
     * @param gatewayId 토픽과 관련된 게이트웨이 ID.
     * @return 해당 토픽을 구독하는 세션의 개수. (구독자가 없으면 0 반환)
     */
    public int getTopicSubscriberCount(String companyDomain, String measurement, String gatewayId) {
        String topicKey = buildTopicKey(companyDomain, measurement, gatewayId);
        Set<String> subscribers = topicSubscriptions.get(topicKey);
        return subscribers != null ? subscribers.size() : 0;
    }

    /**
     * **비활성(닫힌) WebSocket 세션들을 매니저에서 주기적으로 정리하는 메서드입니다.**
     *
     * 네트워크 문제나 클라이언트의 예기치 않은 종료로 인해 서버 측에서는 여전히 '열려있음'으로 인식될 수 있는
     * 비활성 세션(즉, `session.isOpen()`이 `false`인 세션)을 찾아 제거합니다.
     * 이 메서드는 특정 스케줄(예: 1분마다)에 따라 주기적으로 호출될 수 있습니다.
     *
     * @return 정리(제거)된 세션의 개수.
     */
    public int cleanupInactiveSessions() {
        int removedCount = 0; // 제거된 세션의 수를 카운트합니다.

        // 'sessions' 맵에 있는 모든 엔트리(sessionId -> WebSocketSession)를 순회합니다.
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            // 세션이 열려있지 않은(닫힌) 상태라면
            if (!session.isOpen()) {
                removeSession(session); // 해당 세션을 매니저에서 완전히 제거합니다. (이 메서드 내에서 모든 맵에서 제거 로직 포함)
                removedCount++; // 제거된 세션 수 증가
            }
        }

        // 정리된 세션이 있다면 로그를 남깁니다.
        if (removedCount > 0) {
            log.info("비활성 세션 정리 완료 - 제거된 세션: {}개", removedCount);
        }

        return removedCount; // 제거된 세션 수 반환
    }

    /**
     * **주어진 companyDomain, measurement, gatewayId를 결합하여 토픽의 고유 키를 생성하는 헬퍼 메서드입니다.**
     *
     * 이 키는 {@link #topicSubscriptions} 맵에서 특정 데이터 스트림을 식별하는 데 사용됩니다.
     * 토픽 키의 형식은 "companyDomain:measurement:gatewayId" 입니다.
     *
     * @param companyDomain 회사 도메인.
     * @param measurement 측정 항목.
     * @param gatewayId 게이트웨이 ID.
     * @return 생성된 토픽 키 문자열.
     */
    private String buildTopicKey(String companyDomain, String measurement, String gatewayId) {
        return String.format("%s:%s:%s", companyDomain, measurement, gatewayId);
    }

    /**
     * **현재 웹소켓 세션 관리자의 상태에 대한 요약 정보를 {@link Map} 형태로 반환합니다.**
     *
     * 시스템 모니터링이나 대시보드 표시를 위해 사용될 수 있는 메트릭을 제공합니다.
     *
     * @return 총 세션 수, 활성 회사 수, 활성 토픽 수, 그리고 회사별 세션 개수 등을 포함하는 맵.
     */
    public Map<String, Object> getSessionSummary() {
        return Map.of(
                "totalSessions", sessions.size(), // 현재 총 활성 세션 수
                "activeCompanies", companySessionGroups.size(), // 활성 세션을 가진 고유 회사 도메인 수
                "activeTopics", topicSubscriptions.size(), // 현재 구독 중인 고유 토픽 수
                "companySessionCounts", companySessionGroups.entrySet().stream() // 각 회사별 세션 개수 맵
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, // 회사 도메인을 키로 사용
                                entry -> entry.getValue().size() // 해당 회사의 세션 Set 크기를 값으로 사용
                        ))
        );
    }
}