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
 * 다양한 필터 및 시간 조건에 따라 평균값 또는 시간대별 평균값 목록을 조회할 수 있습니다.
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
     *
     * @param queryApi InfluxDB 쿼리 API
     * @param bucket InfluxDB 버킷 이름
     * @param influxOrg InfluxDB 조직 이름
     */
    public TimeSeriesAverageService(QueryApi queryApi,
                                    @Qualifier("influxBucket") String bucket,
                                    @Qualifier("influxOrganization") String influxOrg) {
        this.queryApi = queryApi;
        this.bucket = bucket;
        this.influxOrg = influxOrg;
    }

    /**
     * 최근 rangeMinutes 내의 데이터를 1시간 단위 평균값으로 집계하여 리스트로 반환합니다.
     *
     * @param origin 데이터 출처(origin 태그)
     * @param measurement 측정값 이름(_measurement)
     * @param filters 추가적인 태그 필터 조건
     * @param rangeMinutes 조회할 시간 범위(분)
     * @return 1시간 단위 평균값 리스트
     */
    public List<Double> get1HourAverageSensorValues(String origin,
                                                    String measurement,
                                                    Map<String, String> filters,
                                                    int rangeMinutes) {

        String flux = buildFluxQuery(origin, measurement, "value", filters,
                rangeMinutes, null, null, true);
        log.debug("1시간 평균 Flux: {}", flux);

        try {
            return queryApi.query(flux, influxOrg).stream()
                    .flatMap(t -> t.getRecords().stream())
                    .map(r -> (Double) r.getValue())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("평균 조회 실패 - {}", measurement, e);
            return List.of();
        }
    }

    /**
     * 지정된 시간 범위(start~end) 또는 최근 rangeMinutes 기준의 전체 평균값을 계산합니다.
     *
     * @param origin 데이터 출처
     * @param measurement 측정값 이름
     * @param filters 태그 필터 조건
     * @param rangeMinutes 조회 범위(분) (start/end 사용 시 null)
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param windowed 시간대별 집계를 사용할지 여부
     * @return 평균값(Double), 없으면 null
     */
    public Double getAverageSensorValue(String origin,
                                        String measurement,
                                        Map<String, String> filters,
                                        Integer rangeMinutes,
                                        LocalDateTime startTime,
                                        LocalDateTime endTime,
                                        boolean windowed) {
        String flux = buildFluxQuery(origin, measurement, "value", filters, rangeMinutes, startTime, endTime, windowed);
        log.debug("Flux 쿼리: {}", flux);

        try {
            return queryApi.query(flux, influxOrg).stream()
                    .flatMap(t -> t.getRecords().stream())
                    .map(r -> (Double) r.getValue())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.error("평균 조회 실패 - {}", measurement, e);
            return null;
        }
    }

    /**
     * 최근 rangeMinutes 기준으로 전체 평균값을 계산합니다.
     *
     * @param origin 데이터 출처
     * @param measurement 측정값 이름
     * @param filters 태그 필터 조건
     * @param rangeMinutes 조회 범위(분)
     * @return 평균값(Double), 없으면 null
     */
    public Double getAverageSensorValue(String origin,
                                        String measurement,
                                        Map<String, String> filters,
                                        int rangeMinutes) {
        return getAverageSensorValue(origin, measurement, filters, rangeMinutes, null, null, false);
    }

    /**
     * 고정된 시간 구간(start ~ end) 기준 평균값을 계산합니다.
     *
     * @param origin 데이터 출처
     * @param measurement 측정값 이름
     * @param filters 태그 필터 조건
     * @param start 시작 시간
     * @param end 종료 시간
     * @return 평균값(Double), 없으면 null
     */
    public Double getFixedRangeAverageSensorValue(String origin, String measurement,
                                                  Map<String, String> filters,
                                                  LocalDateTime start, LocalDateTime end) {
        String flux = buildFluxQuery(origin, measurement, "value", filters, null, start, end, false);
        log.debug("하루 고정 Flux: {}", flux);

        try {
            return queryApi.query(flux, influxOrg).stream()
                    .flatMap(t -> t.getRecords().stream())
                    .map(r -> (Double) r.getValue())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.error("고정 범위 평균 조회 실패 - {}", measurement, e);
            return null;
        }
    }

    /**
     * Flux 쿼리문을 생성합니다. rangeMinutes 또는 startTime ~ endTime 중 하나를 사용합니다.
     *
     * @param origin 데이터 출처
     * @param measurement 측정값 이름
     * @param field 필드 이름 (예: value)
     * @param filters 필터 조건
     * @param rangeMinutes 조회 범위(분)
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param windowed aggregateWindow 사용 여부
     * @return Flux 쿼리문 문자열
     */
    private String buildFluxQuery(String origin,
                                  String measurement,
                                  String field,
                                  Map<String, String> filters,
                                  Integer rangeMinutes,
                                  LocalDateTime startTime,
                                  LocalDateTime endTime,
                                  boolean windowed) {

        String rangeClause;
        if (rangeMinutes != null) {
            rangeClause = String.format("range(start: -%dm)", rangeMinutes);
        } else if (startTime != null && endTime != null) {
            ZoneId zone = ZoneId.of("Asia/Seoul");
            DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

            String start = startTime.atZone(zone).toInstant().toString();
            String end = endTime.atZone(zone).toInstant().toString();

            rangeClause = String.format("range(start: time(v: \"%s\"), stop: time(v: \"%s\"))", start, end);
        } else {
            throw new IllegalArgumentException("rangeMinutes 또는 start/end 중 하나는 필수");
        }

        StringBuilder flux = new StringBuilder(String.format("from(bucket: \"%s\") |> %s", bucket, rangeClause));
        flux.append(String.format(" |> filter(fn: (r) => r[\"origin\"] == \"%s\")", origin));
        flux.append(String.format(" |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")", measurement));
        flux.append(String.format(" |> filter(fn: (r) => r[\"_field\"] == \"%s\")", field));

        // ★★★ 핵심 수정: companyDomain 필터 처리 개선 ★★★
        if (filters != null) {
            filters.forEach((k, v) -> {
                if (!List.of("origin", "measurement", "field").contains(k)) {
                    // ★★★ 변수값을 제대로 사용하도록 수정 ★★★
                    flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", k, v));
                    log.debug("필터 추가: {}={}", k, v); // 디버깅용 로그
                }
            });
        }

        if (windowed) {
            flux.append(" |> aggregateWindow(every: 1h, fn: mean, createEmpty: false)");
        } else {
            flux.append(" |> mean()");
        }

        // ★★★ 최종 쿼리 로그 출력 ★★★
        log.info("생성된 Flux 쿼리: {}", flux.toString());

        return flux.toString();
    }
}
