package com.nhnacademy.environment.controller;

import com.nhnacademy.environment.dto.ResourceDataDto;
import com.nhnacademy.environment.service.ServerEnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 서버 환경 리소스를 조회하는 API 컨트롤러.
 * MySQL 에서 company_domain 을 받아와 데이터 출처를 파악
 */
@RestController
@RequestMapping("/environment/{company_domain}")
@RequiredArgsConstructor
public class EnvironmentController {

    private final ServerEnvironmentService serverEnvironmentService;

    /**
     * 센서 리소스 데이터를 범용적으로 조회하는 API.
     * 예: /api/environment/nhnacademy/data?measurement=cpu&_field=usage_idle&range=60&cpu=cpu-total
     *
     * @param companyDomain 회사 도메인 구분
     * @param measurement   InfluxDB 측정값 (예: cpu, mem)
     * @param field         InfluxDB 필드값 (예: usage_idle)
     * @param range  조회 범위 (분 단위, 기본값 60)
     * @param allParams     전체 쿼리 파라미터
     * @return 시간별 센서 데이터 리스트
     */
    @GetMapping("/data")
    public ResponseEntity<Map<String, List<ResourceDataDto>>> getEnvironmentData(
            @PathVariable("company_domain") String companyDomain,
            @RequestParam String measurement,
            @RequestParam(name = "_field") String field,
            @RequestParam(defaultValue = "60") int range,
            @RequestParam Map<String, String> allParams) {

        allParams.remove("measurement");
        allParams.remove("_field");
        allParams.remove("range");

        Map<String, List<ResourceDataDto>> result =
                serverEnvironmentService.getDynamicResourceData(measurement, field, allParams, range);

        return ResponseEntity.ok(result);
    }
}

