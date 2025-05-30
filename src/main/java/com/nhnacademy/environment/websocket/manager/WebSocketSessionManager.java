// src/main/java/com/nhnacademy/environment/websocket/manager/WebSocketSessionManager.java
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
 * WebSocket 세션 관리를 담당하는 매니저 클래스
 * 검색 결과 [2][5]에서 보듯이 세션 관리를 별도 클래스로 분리하여 관리
 *
 * - 활성 세션 관리
 * - 회사별 세션 그룹화
 * - 브로드캐스트 메시지 전송
 * - 세션 상태 모니터링
 */
@Component
@Slf4j
public class WebSocketSessionManager {

    /**
     * 모든 활성 WebSocket 세션 저장
     * Key: sessionId, Value: WebSocketSession
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 회사별 세션 그룹화
     * Key: companyDomain, Value: Set of sessionIds
     */
    private final Map<String, Set<String>> companySessionGroups = new ConcurrentHashMap<>();

    /**
     * 측정 항목별 구독 세션 관리
     * Key: "companyDomain:measurement:gatewayId", Value: Set of sessionIds
     */
    private final Map<String, Set<String>> topicSubscriptions = new ConcurrentHashMap<>();

    /**
     * 세션 추가 (검색 결과 [1][4] 참고)
     */
    public void addSession(WebSocketSession session) {
        String sessionId = session.getId();
        String companyDomain = (String) session.getAttributes().get("companyDomain");
        String userEmail = (String) session.getAttributes().get("userEmail");

        // 세션 저장
        sessions.put(sessionId, session);

        // 회사별 그룹에 추가
        if (companyDomain != null) {
            companySessionGroups.computeIfAbsent(companyDomain, k -> ConcurrentHashMap.newKeySet())
                    .add(sessionId);
        }

        log.info("세션 추가 완료 - sessionId: {}, companyDomain: {}, userEmail: {}, 총 세션 수: {}",
                sessionId, companyDomain, userEmail, sessions.size());
    }

    /**
     * 세션 제거 (검색 결과 [1][4] 참고)
     */
    public void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        String companyDomain = (String) session.getAttributes().get("companyDomain");

        // 세션 제거
        sessions.remove(sessionId);

        // 회사별 그룹에서 제거
        if (companyDomain != null) {
            Set<String> companySessions = companySessionGroups.get(companyDomain);
            if (companySessions != null) {
                companySessions.remove(sessionId);
                if (companySessions.isEmpty()) {
                    companySessionGroups.remove(companyDomain);
                }
            }
        }

        // 모든 구독에서 제거
        topicSubscriptions.values().forEach(subscribers -> subscribers.remove(sessionId));

        // 빈 구독 토픽 제거
        topicSubscriptions.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        log.info("세션 제거 완료 - sessionId: {}, companyDomain: {}, 총 세션 수: {}",
                sessionId, companyDomain, sessions.size());
    }

    /**
     * 토픽 구독 (검색 결과 [2][5] 참고)
     */
    public void subscribeToTopic(String sessionId, String companyDomain, String measurement, String gatewayId) {
        String topicKey = buildTopicKey(companyDomain, measurement, gatewayId);

        topicSubscriptions.computeIfAbsent(topicKey, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);

        log.info("토픽 구독 - sessionId: {}, topic: {}", sessionId, topicKey);
    }

    /**
     * 토픽 구독 해제 (검색 결과 [2][5] 참고)
     */
    public void unsubscribeFromTopic(String sessionId, String companyDomain, String measurement, String gatewayId) {
        String topicKey = buildTopicKey(companyDomain, measurement, gatewayId);

        Set<String> subscribers = topicSubscriptions.get(topicKey);
        if (subscribers != null) {
            subscribers.remove(sessionId);
            if (subscribers.isEmpty()) {
                topicSubscriptions.remove(topicKey);
            }
        }

        log.info("토픽 구독 해제 - sessionId: {}, topic: {}", sessionId, topicKey);
    }

    /**
     * 특정 세션에 메시지 전송 (검색 결과 [2][5] 참고)
     */
    public boolean sendMessageToSession(String sessionId, String message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                log.debug("메시지 전송 성공 - sessionId: {}", sessionId);
                return true;
            } catch (IOException e) {
                log.error("메시지 전송 실패 - sessionId: {}", sessionId, e);
                // 전송 실패한 세션은 제거
                removeSession(session);
            }
        }
        return false;
    }

    /**
     * 회사별 브로드캐스트 메시지 전송
     */
    public int sendMessageToCompany(String companyDomain, String message) {
        Set<String> companySessions = companySessionGroups.get(companyDomain);
        if (companySessions == null || companySessions.isEmpty()) {
            log.debug("회사별 브로드캐스트 - 활성 세션 없음: {}", companyDomain);
            return 0;
        }

        int successCount = 0;
        for (String sessionId : companySessions) {
            if (sendMessageToSession(sessionId, message)) {
                successCount++;
            }
        }

        log.info("회사별 브로드캐스트 완료 - companyDomain: {}, 대상: {}개, 성공: {}개",
                companyDomain, companySessions.size(), successCount);
        return successCount;
    }

    /**
     * 토픽별 브로드캐스트 메시지 전송 (검색 결과 [2][5] 참고)
     */
    public int sendMessageToTopic(String companyDomain, String measurement, String gatewayId, String message) {
        String topicKey = buildTopicKey(companyDomain, measurement, gatewayId);
        Set<String> subscribers = topicSubscriptions.get(topicKey);

        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("토픽 브로드캐스트 - 구독자 없음: {}", topicKey);
            return 0;
        }

        int successCount = 0;
        for (String sessionId : subscribers) {
            if (sendMessageToSession(sessionId, message)) {
                successCount++;
            }
        }

        log.info("토픽 브로드캐스트 완료 - topic: {}, 대상: {}개, 성공: {}개",
                topicKey, subscribers.size(), successCount);
        return successCount;
    }

    /**
     * 전체 브로드캐스트 메시지 전송
     */
    public int broadcastToAll(String message) {
        int successCount = 0;
        for (String sessionId : sessions.keySet()) {
            if (sendMessageToSession(sessionId, message)) {
                successCount++;
            }
        }

        log.info("전체 브로드캐스트 완료 - 대상: {}개, 성공: {}개", sessions.size(), successCount);
        return successCount;
    }

    /**
     * 세션 상태 조회 메소드들
     */
    public int getTotalSessionCount() {
        return sessions.size();
    }

    public int getCompanySessionCount(String companyDomain) {
        Set<String> companySessions = companySessionGroups.get(companyDomain);
        return companySessions != null ? companySessions.size() : 0;
    }

    public Set<String> getActiveCompanies() {
        return companySessionGroups.keySet();
    }

    public Set<String> getActiveTopics() {
        return topicSubscriptions.keySet();
    }

    public int getTopicSubscriberCount(String companyDomain, String measurement, String gatewayId) {
        String topicKey = buildTopicKey(companyDomain, measurement, gatewayId);
        Set<String> subscribers = topicSubscriptions.get(topicKey);
        return subscribers != null ? subscribers.size() : 0;
    }

    /**
     * 비활성 세션 정리 (헬스체크)
     */
    public int cleanupInactiveSessions() {
        int removedCount = 0;

        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (!session.isOpen()) {
                removeSession(session);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            log.info("비활성 세션 정리 완료 - 제거된 세션: {}개", removedCount);
        }

        return removedCount;
    }

    /**
     * 토픽 키 생성 헬퍼 메소드
     */
    private String buildTopicKey(String companyDomain, String measurement, String gatewayId) {
        return String.format("%s:%s:%s", companyDomain, measurement, gatewayId);
    }

    /**
     * 세션 상태 요약 정보 반환 (모니터링용)
     */
    public Map<String, Object> getSessionSummary() {
        return Map.of(
                "totalSessions", sessions.size(),
                "activeCompanies", companySessionGroups.size(),
                "activeTopics", topicSubscriptions.size(),
                "companySessionCounts", companySessionGroups.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().size()
                        ))
        );
    }
}
