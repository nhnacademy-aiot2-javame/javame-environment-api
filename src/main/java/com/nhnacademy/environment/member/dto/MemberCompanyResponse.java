package com.nhnacademy.environment.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * member-api 에서 받아오는 회사 도메인 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MemberCompanyResponse {
    private String companyDomain;
}
