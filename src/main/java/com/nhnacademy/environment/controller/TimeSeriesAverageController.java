package com.nhnacademy.environment.controller;

import com.nhnacademy.environment.timeseries.service.TimeSeriesAverageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 평균 시계열 데이터를 조회하는 REST API 컨트롤러
 * 게이트웨이 필터에서 companyDomain을 자동으로 처리합니다.
 */
@RestController
@RequestMapping("/environment")
@RequiredArgsConstructor
@Slf4j
public class TimeSeriesAverageController {

    private final TimeSeriesAverageService timeSeriesAverageService;

    /**
     * ★★★ 1시간 평균 데이터 (시간 범위 지원) ★★★
     */
    @GetMapping("/{companyDomain}/1h")
    public Map<String, Object> get1HourAverage(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam String measurement,
            @RequestParam(required = false) String startTime,  // ★★★ 추가 ★★★
            @RequestParam(required = false) String endTime,    // ★★★ 추가 ★★★
            @RequestParam(required = false) Map<String, String> allParams
    ) {
        log.info("1시간 평균 데이터 요청 - companyDomain: {}, measurement: {}, startTime: {}, endTime: {}",
                companyDomain, measurement, startTime, endTime);

        try {
            Map<String, String> filters = processFilters(allParams);

            // ★★★ 시간 범위 처리 ★★★
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);

            Map<String, Object> result = timeSeriesAverageService.getAverageDataWithTimeRange(
                    origin, measurement, filters, TimeSeriesAverageService.TimeRange.HOURLY,
                    startDateTime, endDateTime
            );

            Map<String, Object> response = new HashMap<>(result);
            response.put("companyDomain", companyDomain);

            log.info("1시간 평균 데이터 응답 - companyDomain: {}, dataPoints: {}, overall: {}",
                    companyDomain, response.get("dataPoints"), response.get("overallAverage"));

            return response;

        } catch (Exception e) {
            log.error("1시간 평균 데이터 조회 실패 - companyDomain: {}, measurement: {}", companyDomain, measurement, e);
            return createErrorResponse("1h", e.getMessage());
        }
    }

    /**
     * ★★★ 24시간 평균 데이터 (시간 범위 지원) ★★★
     */
    @GetMapping("/{companyDomain}/24h")
    public Map<String, Object> get24HourAverage(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam String measurement,
            @RequestParam(required = false) String startTime,  // ★★★ 추가 ★★★
            @RequestParam(required = false) String endTime,    // ★★★ 추가 ★★★
            @RequestParam(required = false) Map<String, String> allParams
    ) {
        log.info("24시간 평균 데이터 요청 - companyDomain: {}, measurement: {}, startTime: {}, endTime: {}",
                companyDomain, measurement, startTime, endTime);

        try {
            Map<String, String> filters = processFilters(allParams);

            // ★★★ 시간 범위 처리 ★★★
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);

            Map<String, Object> result = timeSeriesAverageService.getAverageDataWithTimeRange(
                    origin, measurement, filters, TimeSeriesAverageService.TimeRange.DAILY,
                    startDateTime, endDateTime
            );

            Map<String, Object> response = new HashMap<>(result);
            response.put("companyDomain", companyDomain);

            log.info("24시간 평균 데이터 응답 - companyDomain: {}, dataPoints: {}, overall: {}",
                    companyDomain, response.get("dataPoints"), response.get("overallAverage"));

            return response;

        } catch (Exception e) {
            log.error("24시간 평균 데이터 조회 실패 - companyDomain: {}, measurement: {}", companyDomain, measurement, e);
            return createErrorResponse("24h", e.getMessage());
        }
    }

    /**
     * ★★★ 1주 평균 데이터 (시간 범위 지원) ★★★
     */
    @GetMapping("/{companyDomain}/1w")
    public Map<String, Object> getWeeklyAverage(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam String measurement,
            @RequestParam(required = false) String startTime,  // ★★★ 추가 ★★★
            @RequestParam(required = false) String endTime,    // ★★★ 추가 ★★★
            @RequestParam(required = false) Map<String, String> allParams
    ) {
        log.info("1주 평균 데이터 요청 - companyDomain: {}, measurement: {}, startTime: {}, endTime: {}",
                companyDomain, measurement, startTime, endTime);

        try {
            Map<String, String> filters = processFilters(allParams);

            // ★★★ 시간 범위 처리 ★★★
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);

            Map<String, Object> result = timeSeriesAverageService.getAverageDataWithTimeRange(
                    origin, measurement, filters, TimeSeriesAverageService.TimeRange.WEEKLY,
                    startDateTime, endDateTime
            );

            Map<String, Object> response = new HashMap<>(result);
            response.put("companyDomain", companyDomain);

            log.info("1주 평균 데이터 응답 - companyDomain: {}, dataPoints: {}, overall: {}",
                    companyDomain, response.get("dataPoints"), response.get("overallAverage"));

            return response;

        } catch (Exception e) {
            log.error("1주 평균 데이터 조회 실패 - companyDomain: {}, measurement: {}", companyDomain, measurement, e);
            return createErrorResponse("1w", e.getMessage());
        }
    }

    /**
     * ★★★ 통합 평균 데이터 조회 (시간 범위 지원) ★★★
     */
    @GetMapping("/{companyDomain}/average/{timeRange}")
    public Map<String, Object> getAverageData(
            @PathVariable String companyDomain,
            @PathVariable String timeRange,
            @RequestParam String origin,
            @RequestParam String measurement,
            @RequestParam(required = false) String startTime,  // ★★★ 추가 ★★★
            @RequestParam(required = false) String endTime,    // ★★★ 추가 ★★★
            @RequestParam(required = false) Map<String, String> allParams
    ) {
        log.info("통합 평균 데이터 요청 - companyDomain: {}, measurement: {}, timeRange: {}, startTime: {}, endTime: {}",
                companyDomain, measurement, timeRange, startTime, endTime);

        try {
            Map<String, String> filters = processFilters(allParams);

            // ★★★ 시간 범위 처리 ★★★
            LocalDateTime startDateTime = parseDateTime(startTime);
            LocalDateTime endDateTime = parseDateTime(endTime);

            TimeSeriesAverageService.TimeRange range = TimeSeriesAverageService.TimeRange.fromCode(timeRange);

            Map<String, Object> result = timeSeriesAverageService.getAverageDataWithTimeRange(
                    origin, measurement, filters, range, startDateTime, endDateTime
            );

            Map<String, Object> response = new HashMap<>(result);
            response.put("companyDomain", companyDomain);

            log.info("통합 평균 데이터 응답 - companyDomain: {}, timeRange: {}, dataPoints: {}",
                    companyDomain, range.getDisplayName(), response.get("dataPoints"));

            return response;

        } catch (Exception e) {
            log.error("통합 평균 데이터 조회 실패 - companyDomain: {}, timeRange: {}", companyDomain, timeRange, e);
            return createErrorResponse(timeRange, e.getMessage());
        }
    }

    /**
     * ★★★ 서비스 목록 조회 엔드포인트 추가 ★★★
     */
    @GetMapping("/{companyDomain}/services")
    public Map<String, Object> getAvailableServices(
            @PathVariable String companyDomain,
            @RequestParam(defaultValue = "server_data") String origin,
            @RequestParam(defaultValue = "service_resource_data") String location
    ) {
        log.info("서비스 목록 API 호출 - companyDomain: {}, origin: {}, location: {}",
                companyDomain, origin, location);

        try {
            Map<String, Object> result = timeSeriesAverageService.getAvailableServices(
                    companyDomain, origin, location);

            if ((Boolean) result.getOrDefault("success", false)) {
                log.info("서비스 목록 조회 성공 - companyDomain: {}, 서비스 수: {}",
                        companyDomain, result.get("count"));
                return result;
            } else {
                log.warn("서비스 목록 조회 실패 - companyDomain: {}, 오류: {}",
                        companyDomain, result.get("errorMessage"));
                return result;
            }

        } catch (Exception e) {
            log.error("서비스 목록 API 오류 - companyDomain: {}", companyDomain, e);

            return Map.of(
                    "services", List.of(),
                    "count", 0,
                    "error", true,
                    "errorMessage", e.getMessage(),
                    "success", false,
                    "timestamp", System.currentTimeMillis()
            );
        }
    }


    /**
     * ★★★ 헬스체크 엔드포인트 ★★★
     */
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "TimeSeriesAverageController");
        response.put("supportedTimeRanges", List.of("1h", "24h", "1w"));
        response.put("gatewayFilter", "enabled");
        response.put("timeRangeSupport", "enabled"); // ★★★ 시간 범위 지원 표시 ★★★
        response.put("version", "1.0.0");

        return response;
    }

    // ★★★ 헬퍼 메소드들 ★★★

    /**
     * 필터 파라미터 정리 (companyDomain 제외)
     */
    private Map<String, String> processFilters(Map<String, String> allParams) {
        Map<String, String> filters = new HashMap<>(Optional.ofNullable(allParams).orElseGet(HashMap::new));

        // 불필요한 파라미터 제거
        filters.remove("origin");
        filters.remove("measurement");
        filters.remove("field");
        filters.remove("rangeMinutes");
        filters.remove("companyDomain");
        filters.remove("startTime");  // ★★★ 추가 ★★★
        filters.remove("endTime");    // ★★★ 추가 ★★★

        log.debug("처리된 필터: {}", filters);
        return filters;
    }

    /**
     * ★★★ 날짜/시간 문자열 파싱 ★★★
     */
    private LocalDateTime parseDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }

        try {
            // ISO 8601 형식 지원 (2025-06-06T21:06:50)
            return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e1) {
            try {
                // ISO 8601 with Z 형식 지원 (2025-06-06T21:06:50Z)
                return LocalDateTime.parse(dateTimeString.replace("Z", ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e2) {
                try {
                    // ISO 8601 with milliseconds 형식 지원 (2025-06-06T21:06:50.123)
                    return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (Exception e3) {
                    log.warn("날짜/시간 파싱 실패: {}, 기본값(null) 사용", dateTimeString);
                    return null;
                }
            }
        }
    }

    /**
     * 에러 응답 생성 (null 안전)
     */
    private Map<String, Object> createErrorResponse(String timeRange, String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("timeSeriesAverage", List.of());
        response.put("overallAverage", 0.0);
        response.put("timeRange", timeRange != null ? timeRange : "unknown");
        response.put("error", true);
        response.put("errorMessage", errorMessage != null ? errorMessage : "Unknown error");
        response.put("timestamp", System.currentTimeMillis());
        response.put("hasData", false);

        return response;
    }
}
