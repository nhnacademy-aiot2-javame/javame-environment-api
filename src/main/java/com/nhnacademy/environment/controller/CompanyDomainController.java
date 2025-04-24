package com.nhnacademy.environment.controller;

import com.nhnacademy.environment.service.SensorDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * companyDomain을 포함한 하위 필터 항목들을 조회할 수 있는 기반을 제공하는 컨트롤러입니다.
 */
@RestController
@RequestMapping("/environment")
public class CompanyDomainController {

    private final SensorDataService sensorDataService;

    public CompanyDomainController(SensorDataService sensorDataService) {
        this.sensorDataService = sensorDataService;
    }

    @GetMapping("/sensor-companyDomains")
    public ResponseEntity<List<String>> getAllCompanyDomains(
            @RequestParam(defaultValue = "sensor_data") String origin
    ) {
        return ResponseEntity.ok(sensorDataService.getTagValues("companyDomain", Map.of("origin", origin)));
    }
}
