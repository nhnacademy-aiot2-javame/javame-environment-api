package com.nhnacademy.environment.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * **클라이언트와 서버 간 웹소켓 메시지 통신을 위한 표준 데이터 전송 객체(DTO)입니다.**
 *
 * 이 클래스는 웹소켓을 통해 주고받는 모든 메시지의 형식을 정의하여,
 * 클라이언트와 서버가 메시지를 명확하고 일관된 방식으로 파싱하고 처리할 수 있도록 합니다.
 * 다양한 메시지 타입(예: 연결 상태, 실시간 데이터, 오류, 구독/구독 해제)을 지원하며,
 * 각 메시지 유형에 필요한 정보들을 포함합니다.
 *
 * Lombok 어노테이션을 활용하여 보일러플레이트 코드를 줄이고,
 * Jackson 어노테이션을 통해 JSON 직렬화/역직렬화 시 필드 이름을 명시합니다.
 */
@Data // Lombok 어노테이션: @Getter, @Setter, @EqualsAndHashCode, @ToString을 자동으로 생성합니다.
@Builder // Lombok 어노테이션: 빌더 패턴을 사용하여 객체를 유연하게 생성할 수 있도록 합니다.
@NoArgsConstructor // Lombok 어노테이션: 기본 생성자를 자동으로 생성합니다. (Jackson 라이브러리에서 필요)
@AllArgsConstructor // Lombok 어노테이션: 모든 필드를 인자로 받는 생성자를 자동으로 생성합니다.
public class WebSocketMessage {

    /**
     * **메시지의 유형을 나타내는 문자열입니다.**
     *
     * 클라이언트와 서버는 이 {@code type} 필드를 통해 메시지의 목적을 식별하고,
     * 그에 맞는 로직을 수행합니다.
     * 예시: "connection" (연결 상태), "realtime" (실시간 데이터), "error" (오류),
     * "subscribe" (구독 요청/응답), "unsubscribe" (구독 해제 요청/응답)
     */
    @JsonProperty("type") // JSON 직렬화/역직렬화 시 이 필드의 JSON 키 이름을 "type"으로 명시합니다.
    private String type;

    /**
     * **메시지의 현재 상태를 나타내는 문자열입니다.**
     *
     * 주로 응답 메시지에서 작업의 성공/실패 여부나 연결 상태를 표현하는 데 사용됩니다.
     * 예시: "success", "error", "connected"
     */
    @JsonProperty("status")
    private String status;

    /**
     * **메시지와 관련된 회사 도메인입니다.**
     *
     * 주로 특정 회사에 대한 데이터나 알림을 전송할 때 사용되어,
     * 클라이언트가 어떤 회사의 정보인지 식별할 수 있도록 합니다.
     */
    @JsonProperty("companyDomain")
    private String companyDomain;

    /**
     * **측정 항목(Measurement)의 이름입니다.**
     *
     * 예를 들어, "CPU", "memory", "temperature" 등 센서나 리소스의 종류를 나타냅니다.
     * 실시간 데이터 메시지에서 어떤 종류의 데이터인지 식별하는 데 사용됩니다.
     */
    @JsonProperty("measurement")
    private String measurement;

    /**
     * **데이터가 속한 게이트웨이(Gateway)의 고유 식별자입니다.**
     *
     * 예를 들어, "cpu", "mem", "sensors" 등 특정 장치나 서비스 그룹을 나타냅니다.
     * `measurement`와 함께 특정 데이터 스트림을 구분하는 데 사용됩니다.
     */
    @JsonProperty("gatewayId")
    private String gatewayId;

    /**
     * **메시지에 포함된 실제 데이터 페이로드(Payload)입니다.**
     *
     * 이 필드는 다양한 형태의 데이터를 담을 수 있으므로 {@code Object} 타입으로 선언됩니다.
     * 예를 들어, 실시간 데이터 메시지에서는 {@code List<TimeSeriesDataDto>} 형태의 시계열 데이터 배열이 될 수 있습니다.
     */
    @JsonProperty("data")
    private Object data;

    /**
     * **메시지가 생성된 시점의 타임스탬프(Epoch Milliseconds)입니다.**
     *
     * 메시지의 시간 순서를 파악하거나, 클라이언트와 서버 간의 시간 동기화에 활용될 수 있습니다.
     * `System.currentTimeMillis()`를 통해 생성됩니다.
     */
    @JsonProperty("timestamp")
    private Long timestamp;

    /**
     * **오류 메시지 내용을 담는 필드입니다.**
     *
     * 주로 `type`이 "error"인 메시지에서 발생한 문제에 대한 상세 설명을 클라이언트에게 전달합니다.
     */
    @JsonProperty("message")
    private String message;

    /**
     * **메시지에 포함될 수 있는 추가적인 동적 정보들을 담는 맵입니다.**
     *
     * 미리 정의되지 않은 유연한 데이터를 필요에 따라 추가하여 전송할 수 있습니다.
     * 예를 들어, 구독 성공 메시지에서는 `interval` 정보가 여기에 포함될 수 있습니다.
     */
    @JsonProperty("extra")
    private Map<String, Object> extra;

    /**
     * **웹소켓 연결 성공 메시지를 생성하는 정적 팩토리 메서드입니다.**
     *
     * 클라이언트가 웹소켓 연결을 성공적으로 수립했을 때, 서버가 클라이언트에게
     * 연결 상태를 알리기 위해 사용됩니다.
     *
     * @param companyDomain 연결된 사용자의 회사 도메인.
     * @return {@link WebSocketMessage} 타입의 연결 성공 메시지 객체.
     */
    public static WebSocketMessage connectionSuccess(String companyDomain) {
        return WebSocketMessage.builder()
                .type("connection") // 메시지 유형을 "connection"으로 설정
                .status("connected") // 연결 상태를 "connected"로 설정
                .companyDomain(companyDomain) // 연결된 회사의 도메인 포함
                .timestamp(System.currentTimeMillis()) // 현재 시간으로 타임스탬프 설정
                .build(); // 빌더 패턴으로 객체 생성
    }

    /**
     * **실시간 데이터 전송 메시지를 생성하는 정적 팩토리 메서드입니다.**
     *
     * 주기적으로 조회된 최신 환경 데이터를 클라이언트에게 푸시할 때 사용됩니다.
     *
     * @param companyDomain 데이터가 속한 회사 도메인.
     * @param measurement 전송할 데이터의 측정 항목.
     * @param gatewayId 전송할 데이터의 게이트웨이 ID.
     * @param data 실제 시계열 데이터 (예: {@code List<TimeSeriesDataDto>}).
     * @return {@link WebSocketMessage} 타입의 실시간 데이터 메시지 객체.
     */
    public static WebSocketMessage realtimeData(String companyDomain, String measurement,
                                                String gatewayId, Object data) {
        return WebSocketMessage.builder()
                .type("realtime") // 메시지 유형을 "realtime"으로 설정
                .companyDomain(companyDomain) // 데이터가 속한 회사 도메인 포함
                .measurement(measurement) // 측정 항목 포함
                .gatewayId(gatewayId) // 게이트웨이 ID 포함
                .data(data) // 실제 데이터 페이로드 포함
                .timestamp(System.currentTimeMillis()) // 현재 시간으로 타임스탬프 설정
                .build(); // 빌더 패턴으로 객체 생성
    }

    /**
     * **오류 메시지를 생성하는 정적 팩토리 메서드입니다.**
     *
     * 웹소켓 통신 중 발생한 문제나 서버 측 오류를 클라이언트에게 알릴 때 사용됩니다.
     *
     * @param message 클라이언트에게 전달할 오류 메시지 내용.
     * @return {@link WebSocketMessage} 타입의 오류 메시지 객체.
     */
    public static WebSocketMessage error(String message) {
        return WebSocketMessage.builder()
                .type("error") // 메시지 유형을 "error"로 설정
                .status("error") // 상태를 "error"로 설정
                .message(message) // 오류 메시지 내용 포함
                .timestamp(System.currentTimeMillis()) // 현재 시간으로 타임스탬프 설정
                .build(); // 빌더 패턴으로 객체 생성
    }
}