package com.nhnacademy.environment.controller;

import com.nhnacademy.environment.config.annotation.HasRole;
import com.nhnacademy.environment.timeseries.service.TimeSeriesAverageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 평균 시계열 데이터를 조회하는 REST API 컨트롤러입니다.
 * <p>
 * 특정 origin 및 measurement 에 대해 1시간 평균 데이터와 전체 평균 데이터를 제공합니다.
 */
@RestController
@RequestMapping("/environment/{companyDomain}")
@RequiredArgsConstructor
public class TimeSeriesAverageController {

    /**
     * 시계열 평균값 계산을 위한 서비스 클래스.
     */
    private final TimeSeriesAverageService timeSeriesAverageService;

    /**
     * origin, measurement, companyDomain, 기타 필터값들을 기준으로
     * 최근 1시간 평균 목록과 전체 평균값을 함께 반환합니다.
     *
     * @param companyDomain 회사 도메인 (PathVariable)
     * @param origin         측정 데이터 출처 (예: sensor_data, server_data)
     * @param measurement    InfluxDB 측정 항목 (예: temperature, cpu)
     * @param allParams      그 외 필터링용 태그 (location, building 등)
     * @param rangeMinutes   조회 범위 (기본: 60분)
     * @return 1시간 평균값 리스트와 전체 평균값을 포함한 Map
     */
//    @HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
    @GetMapping("/1h")
    public Map<String, Object> get1HourAverageWithTotalAverage(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam String measurement,
            @RequestParam(required = false) Map<String, String> allParams,
            @RequestParam(defaultValue = "60") int rangeMinutes
    ) {
        // 요청 필터 중 origin, measurement, field 등은 제거
        Map<String, String> filters = new HashMap<>(Optional.ofNullable(allParams).orElseGet(HashMap::new));
        filters.remove("origin");
        filters.remove("measurement");
        filters.remove("field");
        filters.remove("rangeMinutes");

        // 회사 도메인은 PathVariable 기준으로 강제 지정
        filters.put("companyDomain", companyDomain);

        // 1시간 단위 평균 리스트 및 전체 평균 계산
        List<Double> hourly = timeSeriesAverageService.get1HourAverageSensorValues(origin, measurement, filters, rangeMinutes);
        Double total = timeSeriesAverageService.getAverageSensorValue(origin, measurement, filters, rangeMinutes);

        // 결과 구성
        Map<String, Object> result = new HashMap<>();
        result.put("oneHourAverage", hourly);
        result.put("overallAverage", total);
        return result;
    }
}
