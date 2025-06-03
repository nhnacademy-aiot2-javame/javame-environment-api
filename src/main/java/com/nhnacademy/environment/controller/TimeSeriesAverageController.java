package com.nhnacademy.environment.controller;

import com.nhnacademy.environment.config.annotation.HasRole;
import com.nhnacademy.environment.timeseries.service.TimeSeriesAverageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 1시간, 24시간, 주별 평균 데이터를 제공합니다.
 */
@RestController
@RequestMapping("/environment") // ★★★ 경로 수정 ★★★
@RequiredArgsConstructor
@Slf4j
public class TimeSeriesAverageController {

    private final TimeSeriesAverageService timeSeriesAverageService;

    /**
     * ★★★ 1시간 평균 데이터 (기존 호환성 유지) ★★★
     */
    @GetMapping("/{companyDomain}/1h") // ★★★ 경로 수정 ★★★
    public Map<String, Object> get1HourAverageWithTotalAverage(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam String measurement,
            @RequestParam(required = false) Map<String, String> allParams,
            @RequestParam(defaultValue = "60") int rangeMinutes
    ) {
        log.info("1시간 평균 데이터 요청 - companyDomain: {}, measurement: {}, rangeMinutes: {}",
                companyDomain, measurement, rangeMinutes);

        // 필터 정리
        Map<String, String> filters = new HashMap<>(Optional.ofNullable(allParams).orElseGet(HashMap::new));
        filters.remove("origin");
        filters.remove("measurement");
        filters.remove("field");
        filters.remove("rangeMinutes");
        filters.put("companyDomain", companyDomain);

        log.info("사용될 필터: {}", filters);

        // 1시간 단위 평균 리스트 및 전체 평균 계산
        List<Double> hourly = timeSeriesAverageService.get1HourAverageSensorValues(origin, measurement, filters, rangeMinutes);
        Double total = timeSeriesAverageService.getAverageSensorValue(origin, measurement, filters, rangeMinutes);

        log.info("1시간 평균 데이터 결과 - hourly: {}건, total: {}", hourly.size(), total);

        // 결과 구성
        Map<String, Object> result = new HashMap<>();
        result.put("oneHourAverage", hourly);
        result.put("overallAverage", total);
        return result;
    }

    /**
     * ★★★ 통합 평균 데이터 조회 (1h/24h/1w 지원) ★★★
     */
    @GetMapping("/{companyDomain}/average/{timeRange}") // ★★★ 경로 수정 ★★★
    public Map<String, Object> getAverageData(
            @PathVariable String companyDomain,
            @PathVariable String timeRange,
            @RequestParam String origin,
            @RequestParam String measurement,
            @RequestParam(required = false) Map<String, String> allParams
    ) {
        log.info("통합 평균 데이터 요청 - companyDomain: {}, measurement: {}, timeRange: {}",
                companyDomain, measurement, timeRange);

        try {
            // 필터 정리
            Map<String, String> filters = new HashMap<>(Optional.ofNullable(allParams).orElseGet(HashMap::new));
            filters.remove("origin");
            filters.remove("measurement");
            filters.remove("field");
            filters.put("companyDomain", companyDomain);

            log.info("사용될 필터: {}", filters);

            // TimeRange 변환
            TimeSeriesAverageService.TimeRange range = TimeSeriesAverageService.TimeRange.fromCode(timeRange);

            // 평균 데이터 조회
            Map<String, Object> result = timeSeriesAverageService.getAverageData(origin, measurement, filters, range);

            log.info("통합 평균 데이터 응답 - timeRange: {}, dataPoints: {}",
                    range.getDisplayName(), result.get("dataPoints"));

            return result;

        } catch (Exception e) {
            log.error("통합 평균 데이터 조회 실패", e);

            return Map.of(
                    "timeSeriesAverage", List.of(),
                    "overallAverage", 0.0,
                    "timeRange", timeRange,
                    "error", true,
                    "errorMessage", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            );
        }
    }

    /**
     * ★★★ 24시간 평균 데이터 ★★★
     */
    @GetMapping("/{companyDomain}/24h") // ★★★ 경로 수정 ★★★
    public Map<String, Object> get24HourAverageWithTotalAverage(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam String measurement,
            @RequestParam(required = false) Map<String, String> allParams
    ) {
        log.info("24시간 평균 데이터 요청 - companyDomain: {}, measurement: {}", companyDomain, measurement);

        // 필터 정리
        Map<String, String> filters = new HashMap<>(Optional.ofNullable(allParams).orElseGet(HashMap::new));
        filters.remove("origin");
        filters.remove("measurement");
        filters.remove("field");
        filters.put("companyDomain", companyDomain);

        log.info("사용될 필터: {}", filters);

        // 24시간 평균 데이터 조회
        Map<String, Object> result = timeSeriesAverageService.getAverageData(
                origin, measurement, filters, TimeSeriesAverageService.TimeRange.DAILY
        );

        log.info("24시간 평균 데이터 결과 - dataPoints: {}, overall: {}",
                result.get("dataPoints"), result.get("overallAverage"));

        return result;
    }

    /**
     * ★★★ 주별 평균 데이터 ★★★
     */
    @GetMapping("/{companyDomain}/1w") // ★★★ 경로 수정 ★★★
    public Map<String, Object> getWeeklyAverageWithTotalAverage(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam String measurement,
            @RequestParam(required = false) Map<String, String> allParams
    ) {
        log.info("주별 평균 데이터 요청 - companyDomain: {}, measurement: {}", companyDomain, measurement);

        // 필터 정리
        Map<String, String> filters = new HashMap<>(Optional.ofNullable(allParams).orElseGet(HashMap::new));
        filters.remove("origin");
        filters.remove("measurement");
        filters.remove("field");
        filters.put("companyDomain", companyDomain);

        log.info("사용될 필터: {}", filters);

        // 주별 평균 데이터 조회
        Map<String, Object> result = timeSeriesAverageService.getAverageData(
                origin, measurement, filters, TimeSeriesAverageService.TimeRange.WEEKLY
        );

        log.info("주별 평균 데이터 결과 - dataPoints: {}, overall: {}",
                result.get("dataPoints"), result.get("overallAverage"));

        return result;
    }

    /**
     * ★★★ 헬스체크 엔드포인트 ★★★
     */
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        return Map.of(
                "status", "UP",
                "timestamp", System.currentTimeMillis(),
                "service", "TimeSeriesAverageController",
                "supportedTimeRanges", List.of("1h", "24h", "1w")
        );
    }
}
