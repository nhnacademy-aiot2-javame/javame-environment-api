package com.nhnacademy.environment.timeseries.service;

import com.influxdb.client.QueryApi;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 통합 대시보드용 평균 데이터 서비스
 * 1시간, 24시간, 1주 평균 + 배치 처리용 메소드 지원
 */
@Slf4j
@Service
public class TimeSeriesAverageService {

    private final QueryApi queryApi;
    private final String bucket;
    private final String influxOrg;

    public TimeSeriesAverageService(QueryApi queryApi,
                                    @Qualifier("influxBucket") String bucket,
                                    @Qualifier("influxOrganization") String influxOrg) {
        this.queryApi = queryApi;
        this.bucket = bucket;
        this.influxOrg = influxOrg;
    }

    // ★★★ 시간 범위 열거형 (필수 3개만) ★★★
    public enum TimeRange {
        HOURLY("1h", 60, "1시간"),
        DAILY("24h", 1440, "24시간"),
        WEEKLY("1w", 10080, "1주");

        private final String code;
        private final int minutes;
        private final String displayName;

        TimeRange(String code, int minutes, String displayName) {
            this.code = code;
            this.minutes = minutes;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public int getMinutes() {
            return minutes;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static TimeRange fromCode(String code) {
            for (TimeRange range : values()) {
                if (range.code.equals(code)) {
                    return range;
                }
            }
            return HOURLY;
        }
    }

    // ★★★ 배치 서비스용 평균값 계산 메소드 (새로 추가) ★★★
    public Double getAverageSensorValue(String origin, String measurement,
                                        Map<String, String> filters,
                                        String field,
                                        LocalDateTime startTime, LocalDateTime endTime,
                                        boolean windowed) {

        log.debug("배치용 평균값 계산 - origin: {}, measurement: {}, start: {}, end: {}, windowed: {}",
                origin, measurement, startTime, endTime, windowed);

        try {
            String flux = buildBatchFluxQuery(origin, measurement, filters, field, startTime, endTime, windowed);

            log.debug("배치용 Flux 쿼리: {}", flux);

            return queryApi.query(flux, influxOrg).stream()
                    .flatMap(t -> t.getRecords().stream())
                    .map(r -> (Double) r.getValue())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            log.error("배치용 평균값 계산 실패 - origin: {}, measurement: {}", origin, measurement, e);
            return null;
        }
    }

    // ★★★ 배치용 Flux 쿼리 빌더 ★★★
    private String buildBatchFluxQuery(String origin, String measurement,
                                       Map<String, String> filters,
                                       String field,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       boolean windowed) {

        ZoneId zone = ZoneId.of("Asia/Seoul");
        String start = startTime.atZone(zone).toInstant().toString();
        String end = endTime.atZone(zone).toInstant().toString();

        StringBuilder flux = new StringBuilder()
                .append(String.format("from(bucket: \"%s\")", bucket))
                .append(String.format(" |> range(start: time(v: \"%s\"), stop: time(v: \"%s\"))", start, end))
                .append(String.format(" |> filter(fn: (r) => r[\"origin\"] == \"%s\")", origin))
                .append(String.format(" |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")", measurement));

        // field 필터 (기본값: "value")
        String targetField = field != null ? field : "value";
        flux.append(String.format(" |> filter(fn: (r) => r[\"_field\"] == \"%s\")", targetField));

        // 필터 조건 추가
        if (filters != null) {
            filters.forEach((k, v) -> {
                if (!List.of("origin", "measurement", "field").contains(k)) {
                    flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", k, v));
                }
            });
        }

        // 윈도우 평균 또는 전체 평균
        if (windowed) {
            // 슬라이딩 윈도우 평균 (1시간 단위)
            flux.append(" |> aggregateWindow(every: 1h, fn: mean, createEmpty: false)")
                    .append(" |> mean()");
        } else {
            // 전체 기간 평균
            flux.append(" |> mean()");
        }

        return flux.toString();
    }

    private String getAggregateInterval(TimeRange timeRange) {
        switch (timeRange) {
            case HOURLY:
                return "10m"; // 1시간을 6개 구간
            case DAILY:
                return "1h";  // 24시간을 24개 구간
            case WEEKLY:
                return "6h";  // 1주를 28개 구간
            default:
                return "1h";
        }
    }

    // ★★★ 시간 범위를 지원하는 평균 데이터 조회 (검색 결과 [3] Period Over Period 방식) ★★★
    public Map<String, Object> getAverageDataWithTimeRange(String origin, String measurement,
                                                           Map<String, String> filters,
                                                           TimeRange timeRange,
                                                           LocalDateTime startTime,
                                                           LocalDateTime endTime) {

        log.info("시간 범위 평균 데이터 조회 - measurement: {}, timeRange: {}, start: {}, end: {}",
                measurement, timeRange.getDisplayName(), startTime, endTime);

        try {
            Map<String, String> processedFilters = new HashMap<>(filters != null ? filters : Map.of());

            // ★★★ 검색 결과 [3] Period Over Period 방식: 정확한 시간 범위 설정 ★★★
            LocalDateTime actualEndTime = endTime != null ? endTime : LocalDateTime.now();
            LocalDateTime actualStartTime = startTime != null ? startTime :
                    actualEndTime.minusMinutes(timeRange.getMinutes());

            log.info("실제 시간 범위: {} ~ {}", actualStartTime, actualEndTime);

            // 시간별 평균 리스트 (정확한 시간 범위 적용)
            List<Double> timeSeriesAverages = getTimeSeriesAveragesWithTimeRange(
                    origin, measurement, processedFilters, actualStartTime, actualEndTime, timeRange
            );

            // 전체 평균값 (정확한 시간 범위 적용)
            Double overallAverage = getOverallAverageWithTimeRange(
                    origin, measurement, processedFilters, actualStartTime, actualEndTime
            );

            return Map.of(
                    "timeSeriesAverage", timeSeriesAverages,
                    "overallAverage", overallAverage != null ? overallAverage : 0.0,
                    "timeRange", timeRange.getCode(),
                    "displayName", timeRange.getDisplayName(),
                    "dataPoints", timeSeriesAverages.size(),
                    "hasData", overallAverage != null && overallAverage > 0,
                    "success", true,
                    "actualStartTime", actualStartTime.toString(),
                    "actualEndTime", actualEndTime.toString()
            );

        } catch (Exception e) {
            log.error("시간 범위 평균 데이터 조회 실패 - measurement: {}, timeRange: {}", measurement, timeRange, e);

            return Map.of(
                    "timeSeriesAverage", List.of(),
                    "overallAverage", 0.0,
                    "timeRange", timeRange.getCode(),
                    "error", true,
                    "success", false
            );
        }
    }

    // ★★★ 시간 범위를 적용한 시간별 평균 리스트 조회 ★★★
    private List<Double> getTimeSeriesAveragesWithTimeRange(String origin, String measurement,
                                                            Map<String, String> filters,
                                                            LocalDateTime startTime, LocalDateTime endTime,
                                                            TimeRange timeRange) {

        String aggregateInterval = getAggregateInterval(timeRange);
        String flux = buildFluxQueryWithTimeRange(origin, measurement, filters, startTime, endTime, aggregateInterval, true);

        log.debug("시간별 평균 Flux 쿼리: {}", flux);

        try {
            return queryApi.query(flux, influxOrg).stream()
                    .flatMap(t -> t.getRecords().stream())
                    .map(r -> (Double) r.getValue())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("시간 범위 시간별 평균 조회 실패 - measurement: {}", measurement, e);
            return List.of();
        }
    }

    // ★★★ 시간 범위를 적용한 전체 평균값 조회 ★★★
    private Double getOverallAverageWithTimeRange(String origin, String measurement,
                                                  Map<String, String> filters,
                                                  LocalDateTime startTime, LocalDateTime endTime) {

        String flux = buildFluxQueryWithTimeRange(origin, measurement, filters, startTime, endTime, null, false);

        log.debug("전체 평균 Flux 쿼리: {}", flux);

        try {
            return queryApi.query(flux, influxOrg).stream()
                    .flatMap(t -> t.getRecords().stream())
                    .map(r -> (Double) r.getValue())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            log.error("시간 범위 전체 평균 조회 실패 - measurement: {}", measurement, e);
            return null;
        }
    }

    // ★★★ 시간 범위를 적용한 Flux 쿼리 빌더 ★★★
    private String buildFluxQueryWithTimeRange(String origin, String measurement,
                                               Map<String, String> filters,
                                               LocalDateTime startTime, LocalDateTime endTime,
                                               String aggregateInterval, boolean isTimeSeries) {

        ZoneId zone = ZoneId.of("Asia/Seoul");
        String start = startTime.atZone(zone).toInstant().toString();
        String end = endTime.atZone(zone).toInstant().toString();

        StringBuilder flux = new StringBuilder()
                .append(String.format("from(bucket: \"%s\")", bucket))
                .append(String.format(" |> range(start: time(v: \"%s\"), stop: time(v: \"%s\"))", start, end))
                .append(String.format(" |> filter(fn: (r) => r[\"origin\"] == \"%s\")", origin))
                .append(String.format(" |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")", measurement))
                .append(" |> filter(fn: (r) => r[\"_field\"] == \"value\")");

        // 필터 조건 추가
        if (filters != null) {
            filters.forEach((k, v) -> {
                if (!List.of("origin", "measurement", "field").contains(k)) {
                    flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", k, v));
                }
            });
        }

        // 시간별 집계 또는 전체 평균
        if (isTimeSeries && aggregateInterval != null) {
            flux.append(String.format(" |> aggregateWindow(every: %s, fn: mean, createEmpty: false)", aggregateInterval));
        } else {
            flux.append(" |> mean()");
        }

        return flux.toString();
    }

    /**
     * 서비스 목록 조회 - 최소 버전 (색상 제거)
     */
    public Map<String, Object> getAvailableServices(String companyDomain, String origin, String location) {
        String processedDomain = companyDomain.endsWith(".com") ?
                companyDomain.substring(0, companyDomain.length() - 4) : companyDomain;

        log.info("서비스 목록 조회 - 원본 도메인: {}, 처리된 도메인: {}, origin: {}, location: {}",
                companyDomain, processedDomain, origin, location);

        try {
            String flux = String.format(
                    "from(bucket: \"%s\") " +
                            "|> range(start: -7d) " +
                            "|> filter(fn: (r) => r[\"companyDomain\"] == \"%s\") " + // ★★★ 처리된 도메인 사용 ★★★
                            "|> filter(fn: (r) => r[\"origin\"] == \"%s\") " +
                            "|> filter(fn: (r) => r[\"location\"] == \"%s\") " +
                            "|> group(columns: [\"gatewayId\"]) " +
                            "|> distinct(column: \"gatewayId\") " +
                            "|> group()",
                    bucket, processedDomain, origin, location // ★★★ 처리된 도메인 사용 ★★★
            );

            List<String> gatewayIds = queryApi.query(flux, influxOrg).stream()
                    .flatMap(t -> t.getRecords().stream())
                    .map(r -> (String) r.getValueByKey("gatewayId"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();

            return Map.of(
                    "services", gatewayIds,
                    "count", gatewayIds.size(),
                    "success", true
            );

        } catch (Exception e) {
            log.error("서비스 목록 조회 실패", e);
            return Map.of(
                    "services", List.of(),
                    "count", 0,
                    "error", true,
                    "success", false
            );
        }
    }
}