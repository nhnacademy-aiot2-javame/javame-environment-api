package com.nhnacademy.environment.controller;

import com.nhnacademy.environment.service.SensorAverageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/environment/{companyDomain}")
@RequiredArgsConstructor
public class SensorAverageController {

    private final SensorAverageService sensorAverageService;

    @GetMapping("/1h")
    public Map<String, Object> get1HourAverageWithTotalAverage(
            @PathVariable("companyDomain") String companyDomain,
            @RequestParam String origin,
            @RequestParam String measurement,
            @RequestParam(required = false) Map<String, String> allParams,
            @RequestParam(defaultValue = "60") int rangeMinutes
    ) {
        if (allParams == null) {
            allParams = new HashMap<>();
        }

        // origin, measurement, field, rangeMinutes 빼고 남은 것만 추가 필터로
        Map<String, String> additionalFilters = new HashMap<>(allParams);
        additionalFilters.remove("origin");
        additionalFilters.remove("measurement");
        additionalFilters.remove("field");
        additionalFilters.remove("rangeMinutes");

        // 그리고 companyDomain을 명시적으로 넣기
        additionalFilters.put("companyDomain", companyDomain);

        List<Double> oneHourAverages = sensorAverageService.get1HourAverageSensorValues(origin, measurement, additionalFilters, rangeMinutes);
        Double overallAverage = sensorAverageService.getAverageSensorValue(origin, measurement, additionalFilters, rangeMinutes);

        Map<String, Object> result = new HashMap<>();
        result.put("oneHourAverage", oneHourAverages);
        result.put("overallAverage", overallAverage);

        return result;
    }
}
