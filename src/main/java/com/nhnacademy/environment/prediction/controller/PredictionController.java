package com.nhnacademy.environment.prediction.controller;


import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.prediction.domain.LatestPrediction;
import com.nhnacademy.environment.prediction.dto.ResourcePredictionDto;
import com.nhnacademy.environment.prediction.service.ResourcePredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    // PredictionController.java에 추가

    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debugPredictionData(
            @RequestParam String companyDomain,
            @RequestParam String deviceId) {

        Map<String, Object> debugInfo = predictionService.getDebugInfo(companyDomain, deviceId);

        return ResponseEntity.ok(debugInfo);
    }
}