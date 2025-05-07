package com.nhnacademy.environment.member.service;

import com.nhnacademy.environment.member.client.MemberClient;
import com.nhnacademy.environment.member.dto.MemberCompanyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * JWT에 포함된 이메일을 기반으로 회사 도메인(companyDomain)을 조회하는 서비스
 */
@Service
@RequiredArgsConstructor
public class CompanyDomainService {

    private final MemberClient memberClient;

    public String findCompanyDomainByEmail(String email) {
        return memberClient.getCompanyDomainByEmail(email).getCompanyDomain();
    }

    public void validateCompanyDomainAccess(String email, String tokenCompanyDomain) {
        String actualDomain = findCompanyDomainByEmail(email);
        if (!actualDomain.equals(tokenCompanyDomain)) {
            throw new SecurityException("접근 권한 없음");
        }
    }
}
