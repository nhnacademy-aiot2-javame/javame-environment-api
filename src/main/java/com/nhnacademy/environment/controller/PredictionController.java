package com.nhnacademy.environment.controller;


import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.prediction.domain.LatestPrediction;
import com.nhnacademy.environment.prediction.dto.ResourcePredictionDto;
import com.nhnacademy.environment.prediction.service.ResourcePredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/environment/forecast")
@RequiredArgsConstructor
public class PredictionController {

    private final ResourcePredictionService predictionService;
    /**
     * cpu의 모든 예측 데이터를 조회
     */
    @GetMapping("/cpu")
    public ResponseEntity<ResourcePredictionDto> getCpuPrediction(
            @RequestParam String companyDomain,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "6") int hoursBack,
            @RequestParam(defaultValue = "6") int hoursForward) {

        ResourcePredictionDto data = predictionService.getCpuPredictionData(
                companyDomain, deviceId, hoursBack, hoursForward);
        return ResponseEntity.ok(data);
    }
    /**
     * 메모리의 모든 예측 데이터를 조회
     */
    @GetMapping("/memory")
    public ResponseEntity<ResourcePredictionDto> getMemoryPrediction(
            @RequestParam String companyDomain,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "6") int hoursBack,
            @RequestParam(defaultValue = "6") int hoursForward) {

        ResourcePredictionDto data = predictionService.getMemoryPredictionData(
                companyDomain, deviceId, hoursBack, hoursForward);
        return ResponseEntity.ok(data);
    }
    /**
     * 디스크의 모든 예측 데이터를 조회
     */
    @GetMapping("/disk")
    public ResponseEntity<ResourcePredictionDto> getDiskPrediction(
            @RequestParam String companyDomain,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "6") int hoursBack,
            @RequestParam(defaultValue = "6") int hoursForward) {

        ResourcePredictionDto data = predictionService.getDiskPredictionData(
                companyDomain, deviceId, hoursBack, hoursForward);
        return ResponseEntity.ok(data);
    }
    /**
     * 모든 데이터를 조회
     */
    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debugPredictionData(
            @RequestParam String companyDomain,
            @RequestParam String deviceId) {

        Map<String, Object> debugInfo = predictionService.getDebugInfo(companyDomain, deviceId);

        return ResponseEntity.ok(debugInfo);
    }
    /**
     * 모든 리소스의 예측 데이터를 한번에 조회
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, ResourcePredictionDto>> getAllPredictions(
            @RequestParam String companyDomain,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "12") int hoursBack,
            @RequestParam(defaultValue = "24") int hoursForward) {

        Map<String, ResourcePredictionDto> allData = new HashMap<>();

        allData.put("cpu", predictionService.getCpuPredictionData(
                companyDomain, deviceId, hoursBack, hoursForward));
        allData.put("memory", predictionService.getMemoryPredictionData(
                companyDomain, deviceId, hoursBack, hoursForward));
        allData.put("disk", predictionService.getDiskPredictionData(
                companyDomain, deviceId, hoursBack, hoursForward));

        return ResponseEntity.ok(allData);
    }

    /**
     * 예측 신뢰도 정보 조회
     */
    @GetMapping("/confidence")
    public ResponseEntity<Map<String, Object>> getPredictionConfidence(
            @RequestParam String companyDomain,
            @RequestParam String deviceId,
            @RequestParam String resourceType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime targetTime) {

        Double confidence = predictionService.getPredictionConfidence(
                companyDomain, deviceId, resourceType, targetTime);

        Map<String, Object> result = new HashMap<>();
        result.put("companyDomain", companyDomain);
        result.put("deviceId", deviceId);
        result.put("resourceType", resourceType);
        result.put("targetTime", targetTime);
        result.put("confidence", confidence != null ? confidence : 0.0);
        result.put("hasData", confidence != null);

        return ResponseEntity.ok(result);
    }
}