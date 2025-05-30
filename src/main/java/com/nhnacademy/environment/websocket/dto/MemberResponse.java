// src/main/java/com/nhnacademy/environment/websocket/dto/MemberResponse.java
package com.nhnacademy.environment.websocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 정보 조회 응답 시 반환될 데이터를 담는 DTO 클래스입니다.
 * Gateway의 MemberResponse와 동일한 구조입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemberResponse {

    /**
     * 회원 식별 번호.
     */
    private Long memberNo;

    /**
     * 회원 이메일.
     */
    private String memberEmail;

    /**
     * 소속 회사 도메인.
     */
    private String companyDomain;

    /**
     * 역할 ID (예: "ROLE_USER").
     */
    private String roleId;
}
