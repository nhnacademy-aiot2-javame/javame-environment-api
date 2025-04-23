package com.nhnacademy.environment.controller;

import com.nhnacademy.environment.dto.ResourceDataDto;
import com.nhnacademy.environment.dto.SensorDataDto;
import com.nhnacademy.environment.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 다양한 센서 데이터를 유동적으로 조회하는 API 컨트롤러.
 */
@RestController
@RequestMapping("/environment/{companyDomain}")
@RequiredArgsConstructor
public class SensorDataController {

    private final SensorDataService sensorDataService;

    /**
     * 센서 데이터를 조회 (동적 필드 & 태그 지원)
     * 예: /environment/nhnacademy/sensor-data?measurement=co2&range=60&device=device1&location=lab
     *
     * @param companyDomain 회사 도메인 (현재는 로직상 사용 안함, 경로에만 포함됨)
     * @param measurement 측정 항목 (예: co2, temp 등)
     * @param range 조회 범위 (분 단위, 기본값: 60)
     * @param allParams measurement, range를 제외한 나머지는 모두 태그 필터로 간주
     * @return 태그별 센서 데이터 목록
     */
    @GetMapping("/sensor-data")
    public ResponseEntity<Map<String, List<ResourceDataDto>>> getSensorData(
            @PathVariable("companyDomain") String companyDomain,
            @RequestParam String measurement,
            @RequestParam(defaultValue = "60") int range,
            @RequestParam Map<String, String> allParams) {

        allParams.remove("measurement");
        allParams.remove("range");

        Map<String, List<ResourceDataDto>> result =
                sensorDataService.getSensorData(measurement, allParams, range);

        return ResponseEntity.ok(result);
    }
}
