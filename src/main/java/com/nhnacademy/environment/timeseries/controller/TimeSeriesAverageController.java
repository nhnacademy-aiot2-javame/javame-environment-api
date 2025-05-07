package com.nhnacademy.environment.timeseries.controller;

import com.nhnacademy.environment.timeseries.service.TimeSeriesAverageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/environment/{companyDomain}")
@RequiredArgsConstructor
public class TimeSeriesAverageController {

    private final TimeSeriesAverageService timeSeriesAverageService;

    @GetMapping("/1h")
    public Map<String, Object> get1HourAverageWithTotalAverage(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam String measurement,
            @RequestParam(required = false) Map<String, String> allParams,
            @RequestParam(defaultValue = "60") int rangeMinutes
    ) {
        Map<String, String> filters = new HashMap<>(Optional.ofNullable(allParams).orElseGet(HashMap::new));
        filters.remove("origin");
        filters.remove("measurement");
        filters.remove("field");
        filters.remove("rangeMinutes");
        filters.put("companyDomain", companyDomain);

        List<Double> hourly = timeSeriesAverageService.get1HourAverageSensorValues(origin, measurement, filters, rangeMinutes);
        Double total = timeSeriesAverageService.getAverageSensorValue(origin, measurement, filters, rangeMinutes);

        Map<String, Object> result = new HashMap<>();
        result.put("oneHourAverage", hourly);
        result.put("overallAverage", total);
        return result;
    }
}
