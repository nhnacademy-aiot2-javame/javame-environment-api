package com.nhnacademy.environment.member.client.fallback;

import com.nhnacademy.environment.member.client.MemberClient;
import com.nhnacademy.environment.member.dto.MemberCompanyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MemberClientFallback implements MemberClient {

    @Override
    public MemberCompanyResponse getCompanyDomainByEmail(String email) {
        log.warn("MEMBER-API fallback activated for email: {}", email);
        return new MemberCompanyResponse(""); // 비어있는 응답 또는 기본값
    }
}
