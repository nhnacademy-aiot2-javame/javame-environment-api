package com.nhnacademy.environment.member.client;

import com.nhnacademy.environment.member.client.fallback.MemberClientFallback;
import com.nhnacademy.environment.member.dto.MemberCompanyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * member-api에 있는 회사 도메인 조회용 FeignClient
 */
@FeignClient(name = "MEMBER-API",
        path = "/members",
        fallback = MemberClientFallback.class
)
public interface MemberClient {

    /**
     * 이메일을 기반으로 회사 도메인을 조회합니다.
     * @param email 이메일 주소
     * @return 회사 도메인 DTO
     */
    @GetMapping("/company-domain")
    MemberCompanyResponse getCompanyDomainByEmail(@RequestParam("email") String email);
}
