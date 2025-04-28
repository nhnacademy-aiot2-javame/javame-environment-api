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
            @RequestParam(required = false) Map<String, String> additionalFilters,
            @RequestParam(defaultValue = "60") int rangeMinutes
    ) {
        if (additionalFilters == null) {
            additionalFilters = new HashMap<>();
        }
        additionalFilters.put("companyDomain", companyDomain); // 추가 필터에 companyDomain 강제 삽입

        List<Double> fiveMinAverages = sensorAverageService.get1HourAverageSensorValues(origin, measurement, additionalFilters, rangeMinutes);
        Double overallAverage = sensorAverageService.getAverageSensorValue(origin, measurement, additionalFilters, rangeMinutes);

        Map<String, Object> result = new HashMap<>();
        result.put("fiveMinAverages", fiveMinAverages);
        result.put("overallAverage", overallAverage);

        return result;
    }
}
