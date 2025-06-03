package com.nhnacademy.environment.timeseries.service;

import com.influxdb.client.QueryApi;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * InfluxDB 에서 시계열 데이터의 평균 값을 계산하는 서비스 클래스입니다.
 * 1시간, 24시간, 주별 평균을 지원합니다.
 */
@Slf4j
@Service
public class TimeSeriesAverageService {

    /** InfluxDB 쿼리 API 입니다. */
    private final QueryApi queryApi;

    /** InfluxDB 버킷 이름 입니다. */
    private final String bucket;

    /** InfluxDB 조직 이름 입니다. */
    private final String influxOrg;

    /**
     * 생성자 - QueryApi, 버킷 이름, 조직 이름 주입 합니다.
     */
    public TimeSeriesAverageService(QueryApi queryApi,
                                    @Qualifier("influxBucket") String bucket,
                                    @Qualifier("influxOrganization") String influxOrg) {
        this.queryApi = queryApi;
        this.bucket = bucket;
        this.influxOrg = influxOrg;
    }

    // ★★★ 시간 범위 열거형 (검색 결과 [2][4] 패턴 참고) ★★★
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

        public String getCode() { return code; }
        public int getMinutes() { return minutes; }
        public String getDisplayName() { return displayName; }

        public static TimeRange fromCode(String code) {
            for (TimeRange range : values()) {
                if (range.code.equals(code)) {
                    return range;
                }
            }
            return HOURLY; // 기본값
        }
    }

    // ★★★ 통합 평균 데이터 조회 메소드 (검색 결과 [3] aggregateWindow 패턴) ★★★
    public Map<String, Object> getAverageData(String origin, String measurement,
                                              Map<String, String> filters,
                                              TimeRange timeRange) {

        log.info("평균 데이터 조회 시작 - measurement: {}, timeRange: {}", measurement, timeRange.getDisplayName());

        try {
            // 현재 시간 기준 범위 계산
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusMinutes(timeRange.getMinutes());

            // 시간별 평균 리스트 조회
            List<Double> timeSeriesAverages = getTimeSeriesAverages(
                    origin, measurement, filters, startTime, endTime, timeRange
            );

            // 전체 평균값 조회
            Double overallAverage = getOverallAverage(
                    origin, measurement, filters, startTime, endTime
            );

            // 결과 구성
            Map<String, Object> result = Map.of(
                    "timeSeriesAverage", timeSeriesAverages,
                    "overallAverage", overallAverage != null ? overallAverage : 0.0,
                    "timeRange", timeRange.getCode(),
                    "displayName", timeRange.getDisplayName(),
                    "dataPoints", timeSeriesAverages.size(),
                    "startTime", startTime.toString(),
                    "endTime", endTime.toString(),
                    "hasData", overallAverage != null && overallAverage > 0
            );

            log.info("평균 데이터 조회 완료 - timeRange: {}, dataPoints: {}, overall: {}",
                    timeRange.getDisplayName(), timeSeriesAverages.size(), overallAverage);

            return result;

        } catch (Exception e) {
            log.error("평균 데이터 조회 실패 - measurement: {}, timeRange: {}", measurement, timeRange, e);

            // 오류 시 기본 응답
            return Map.of(
                    "timeSeriesAverage", List.of(),
                    "overallAverage", 0.0,
                    "timeRange", timeRange.getCode(),
                    "error", true,
                    "errorMessage", e.getMessage()
            );
        }
    }

    // ★★★ 시간별 평균 리스트 조회 (검색 결과 [3][4] aggregateWindow 패턴) ★★★
    public List<Double> getTimeSeriesAverages(String origin, String measurement,
                                              Map<String, String> filters,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              TimeRange timeRange) {

        String aggregateInterval = getAggregateInterval(timeRange);
        String flux = buildTimeSeriesFluxQuery(origin, measurement, "value", filters,
                startTime, endTime, aggregateInterval);

        log.info("시간별 평균 Flux 쿼리 ({}): {}", timeRange.getDisplayName(), flux);

        try {
            List<Double> results = queryApi.query(flux, influxOrg).stream()
                    .flatMap(t -> t.getRecords().stream())
                    .map(r -> (Double) r.getValue())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("시간별 평균 조회 결과 ({}): {}건", timeRange.getDisplayName(), results.size());
            return results;

        } catch (Exception e) {
            log.error("시간별 평균 조회 실패 - measurement: {}, timeRange: {}", measurement, timeRange, e);
            return List.of();
        }
    }

    // ★★★ 전체 평균값 조회 (검색 결과 [3] mean() 패턴) ★★★
    public Double getOverallAverage(String origin, String measurement,
                                    Map<String, String> filters,
                                    LocalDateTime startTime, LocalDateTime endTime) {

        String flux = buildOverallAverageFluxQuery(origin, measurement, "value", filters,
                startTime, endTime);

        log.info("전체 평균 Flux 쿼리: {}", flux);

        try {
            Double result = queryApi.query(flux, influxOrg).stream()
                    .flatMap(t -> t.getRecords().stream())
                    .map(r -> (Double) r.getValue())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            log.info("전체 평균 조회 결과: {}", result);
            return result;

        } catch (Exception e) {
            log.error("전체 평균 조회 실패 - measurement: {}", measurement, e);
            return null;
        }
    }

    // ★★★ 시간별 집계 간격 결정 (검색 결과 [2][4] 패턴) ★★★
    private String getAggregateInterval(TimeRange timeRange) {
        switch (timeRange) {
            case HOURLY:
                return "10m"; // 1시간을 6개 구간으로 나누어 10분 단위
            case DAILY:
                return "1h"; // 24시간을 24개 구간으로 나누어 1시간 단위
            case WEEKLY:
                return "6h"; // 1주를 28개 구간으로 나누어 6시간 단위
            default:
                return "1h";
        }
    }

    private String buildTimeSeriesFluxQuery(String origin, String measurement, String field,
                                            Map<String, String> filters,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            String aggregateInterval) {

        ZoneId zone = ZoneId.of("Asia/Seoul");
        String start = startTime.atZone(zone).toInstant().toString();
        String end = endTime.atZone(zone).toInstant().toString();

        StringBuilder flux = new StringBuilder(
                String.format("from(bucket: \"%s\")", bucket)
        );

        flux.append(String.format(" |> range(start: time(v: \"%s\"), stop: time(v: \"%s\"))", start, end))
                .append(String.format(" |> filter(fn: (r) => r[\"origin\"] == \"%s\")", origin))
                .append(String.format(" |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")", measurement))
                .append(String.format(" |> filter(fn: (r) => r[\"_field\"] == \"%s\")", field));

        // 필터 조건 추가
        if (filters != null) {
            filters.forEach((k, v) -> {
                if (!List.of("origin", "measurement", "field").contains(k)) {
                    flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", k, v));
                }
            });
        }

        // ★★★ aggregateWindow로 시간별 집계 (검색 결과 [3] 패턴) ★★★
        flux.append(String.format(" |> aggregateWindow(every: %s, fn: mean, createEmpty: false)", aggregateInterval));

        return flux.toString();
    }

    private String buildOverallAverageFluxQuery(String origin, String measurement, String field,
                                                Map<String, String> filters,
                                                LocalDateTime startTime, LocalDateTime endTime) {

        ZoneId zone = ZoneId.of("Asia/Seoul");
        String start = startTime.atZone(zone).toInstant().toString();
        String end = endTime.atZone(zone).toInstant().toString();

        StringBuilder flux = new StringBuilder(
                String.format("from(bucket: \"%s\")", bucket)
        );

        flux.append(String.format(" |> range(start: time(v: \"%s\"), stop: time(v: \"%s\"))", start, end))
                .append(String.format(" |> filter(fn: (r) => r[\"origin\"] == \"%s\")", origin))
                .append(String.format(" |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")", measurement))
                .append(String.format(" |> filter(fn: (r) => r[\"_field\"] == \"%s\")", field));

        // 필터 조건 추가
        if (filters != null) {
            filters.forEach((k, v) -> {
                if (!List.of("origin", "measurement", "field").contains(k)) {
                    flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", k, v));
                }
            });
        }

        // ★★★ 전체 평균 계산 (검색 결과 [3] mean() 패턴) ★★★
        flux.append(" |> mean()");

        return flux.toString();
    }

    // ★★★ 기존 메소드들 (하위 호환성 유지) ★★★

    /**
     * 최근 rangeMinutes 내의 데이터를 1시간 단위 평균값으로 집계하여 리스트로 반환합니다.
     */
    public List<Double> get1HourAverageSensorValues(String origin,
                                                    String measurement,
                                                    Map<String, String> filters,
                                                    int rangeMinutes) {

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(rangeMinutes);

        return getTimeSeriesAverages(origin, measurement, filters, startTime, endTime, TimeRange.HOURLY);
    }

    /**
     * 최근 rangeMinutes 기준으로 전체 평균값을 계산합니다.
     */
    public Double getAverageSensorValue(String origin,
                                        String measurement,
                                        Map<String, String> filters,
                                        int rangeMinutes) {

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(rangeMinutes);

        return getOverallAverage(origin, measurement, filters, startTime, endTime);
    }

    /**
     * 지정된 시간 범위의 전체 평균값을 계산합니다.
     */
    public Double getAverageSensorValue(String origin,
                                        String measurement,
                                        Map<String, String> filters,
                                        Integer rangeMinutes,
                                        LocalDateTime startTime,
                                        LocalDateTime endTime,
                                        boolean windowed) {

        if (rangeMinutes != null) {
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = end.minusMinutes(rangeMinutes);
            return getOverallAverage(origin, measurement, filters, start, end);
        } else if (startTime != null && endTime != null) {
            return getOverallAverage(origin, measurement, filters, startTime, endTime);
        } else {
            throw new IllegalArgumentException("rangeMinutes 또는 start/end 중 하나는 필수");
        }
    }

    /**
     * 고정된 시간 구간(start ~ end) 기준 평균값을 계산합니다.
     */
    public Double getFixedRangeAverageSensorValue(String origin, String measurement,
                                                  Map<String, String> filters,
                                                  LocalDateTime start, LocalDateTime end) {
        return getOverallAverage(origin, measurement, filters, start, end);
    }
}
