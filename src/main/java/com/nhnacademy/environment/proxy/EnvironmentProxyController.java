package com.nhnacademy.environment.proxy;

import com.nhnacademy.environment.config.annotation.HasRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RestController
@RequestMapping("/proxy/environment")
public class EnvironmentProxyController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String INTERNAL_API_BASE = "http://s2.java21.net:10279/api/v1/environment";

    /**
     * origin 목록 조회 프록시 메서드.
     * 프론트엔드는 이 메서드로 HTTPS 요청을 보내고,
     * 내부에서는 HTTP 주소로 gateway 에 중계 요청을 보낸다.
     *
     * @param companyDomain 회사 도메인
     * @return origin 목록 JSON
     */
    @GetMapping("/{companyDomain}/origins")
    @HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
    public ResponseEntity<String> proxyGetOrigins(@PathVariable String companyDomain) {
        String targetUrl = INTERNAL_API_BASE + "/" + companyDomain + "/origins";
        log.debug("[Proxy] Fetching origins from: {}", targetUrl);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(targetUrl, String.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            log.error("[Proxy] Failed to fetch origins from {}", targetUrl, e);
            return ResponseEntity.internalServerError().body("Failed to fetch origins from backend");
        }
    }
}
