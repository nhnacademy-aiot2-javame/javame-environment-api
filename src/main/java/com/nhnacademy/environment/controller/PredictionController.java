package com.nhnacademy.environment.controller;

import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.config.annotation.CompanyDomainContext;
import com.nhnacademy.environment.config.annotation.NormalizeCompanyDomain;
import com.nhnacademy.environment.prediction.domain.LatestPrediction;
import com.nhnacademy.environment.prediction.dto.ResourcePredictionDto;
import com.nhnacademy.environment.prediction.service.ResourcePredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/environment/{companyDomain}/forecast")  // URL 경로에 companyDomain 포함
@RequiredArgsConstructor
public class PredictionController {

    private final ResourcePredictionService predictionService;

    /**
     * cpu의 모든 예측 데이터를 조회
     */
    @NormalizeCompanyDomain
    @GetMapping("/cpu")
    public ResponseEntity<ResourcePredictionDto> getCpuPrediction(
            @PathVariable String companyDomain,  // 경로 변수로 받음
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "6") int hoursBack,
            @RequestParam(defaultValue = "6") int hoursForward) {

        // CompanyDomainContext에서 실제 도메인 가져오기
        String actualCompanyDomain = CompanyDomainContext.get();
        log.info("CPU 예측 조회 - companyDomain: {}, deviceId: {}", actualCompanyDomain, deviceId);

        ResourcePredictionDto data = predictionService.getCpuPredictionData(
                actualCompanyDomain, deviceId, hoursBack, hoursForward);
        return ResponseEntity.ok(data);
    }

    /**
     * 메모리의 모든 예측 데이터를 조회
     */
    @NormalizeCompanyDomain
    @GetMapping("/memory")
    public ResponseEntity<ResourcePredictionDto> getMemoryPrediction(
            @PathVariable String companyDomain,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "6") int hoursBack,
            @RequestParam(defaultValue = "6") int hoursForward) {

        String actualCompanyDomain = CompanyDomainContext.get();
        log.info("메모리 예측 조회 - companyDomain: {}, deviceId: {}", actualCompanyDomain, deviceId);

        ResourcePredictionDto data = predictionService.getMemoryPredictionData(
                actualCompanyDomain, deviceId, hoursBack, hoursForward);
        return ResponseEntity.ok(data);
    }

    /**
     * 디스크의 모든 예측 데이터를 조회
     */
    @NormalizeCompanyDomain
    @GetMapping("/disk")
    public ResponseEntity<ResourcePredictionDto> getDiskPrediction(
            @PathVariable String companyDomain,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "6") int hoursBack,
            @RequestParam(defaultValue = "6") int hoursForward) {

        String actualCompanyDomain = CompanyDomainContext.get();
        log.info("디스크 예측 조회 - companyDomain: {}, deviceId: {}", actualCompanyDomain, deviceId);

        ResourcePredictionDto data = predictionService.getDiskPredictionData(
                actualCompanyDomain, deviceId, hoursBack, hoursForward);
        return ResponseEntity.ok(data);
    }

    /**
     * 디버그 정보 조회
     */
    @NormalizeCompanyDomain
    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debugPredictionData(
            @PathVariable String companyDomain,
            @RequestParam String deviceId) {

        String actualCompanyDomain = CompanyDomainContext.get();
        log.info("디버그 정보 조회 - companyDomain: {}, deviceId: {}", actualCompanyDomain, deviceId);

        Map<String, Object> debugInfo = predictionService.getDebugInfo(actualCompanyDomain, deviceId);

        return ResponseEntity.ok(debugInfo);
    }

    /**
     * 모든 리소스의 예측 데이터를 한번에 조회
     */
    @NormalizeCompanyDomain
    @GetMapping("/all")
    public ResponseEntity<Map<String, ResourcePredictionDto>> getAllPredictions(
            @PathVariable String companyDomain,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "12") int hoursBack,
            @RequestParam(defaultValue = "24") int hoursForward) {

        String actualCompanyDomain = CompanyDomainContext.get();
        log.info("전체 예측 조회 - companyDomain: {}, deviceId: {}", actualCompanyDomain, deviceId);

        Map<String, ResourcePredictionDto> allData = new HashMap<>();

        allData.put("cpu", predictionService.getCpuPredictionData(
                actualCompanyDomain, deviceId, hoursBack, hoursForward));
        allData.put("memory", predictionService.getMemoryPredictionData(
                actualCompanyDomain, deviceId, hoursBack, hoursForward));
        allData.put("disk", predictionService.getDiskPredictionData(
                actualCompanyDomain, deviceId, hoursBack, hoursForward));

        return ResponseEntity.ok(allData);
    }

    /**
     * 예측 신뢰도 정보 조회
     */
    @NormalizeCompanyDomain
    @GetMapping("/confidence")
    public ResponseEntity<Map<String, Object>> getPredictionConfidence(
            @PathVariable String companyDomain,
            @RequestParam String deviceId,
            @RequestParam String resourceType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime targetTime) {

        String actualCompanyDomain = CompanyDomainContext.get();

        Double confidence = predictionService.getPredictionConfidence(
                actualCompanyDomain, deviceId, resourceType, targetTime);

        Map<String, Object> result = new HashMap<>();
        result.put("companyDomain", actualCompanyDomain);
        result.put("deviceId", deviceId);
        result.put("resourceType", resourceType);
        result.put("targetTime", targetTime);
        result.put("confidence", confidence != null ? confidence : 0.0);
        result.put("hasData", confidence != null);

        return ResponseEntity.ok(result);
    }
}