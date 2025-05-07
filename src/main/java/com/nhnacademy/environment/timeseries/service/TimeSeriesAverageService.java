package com.nhnacademy.environment.timeseries.service;

import com.influxdb.client.QueryApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

    public Double getAverageSensorValue(String origin,
                                        String measurement,
                                        Map<String, String> filters,
                                        int rangeMinutes) {
        return getAverageSensorValue(origin, measurement, filters, rangeMinutes, null, null, false);
    }


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

    private String buildFluxQuery(String origin,
                                  String measurement,
                                  String field,
                                  Map<String, String> filters,
                                  Integer rangeMinutes,                  // 이거나
                                  LocalDateTime startTime,              // 이거나
                                  LocalDateTime endTime,
                                  boolean windowed) {

        String rangeClause;
        if (rangeMinutes != null) {
            rangeClause = String.format("range(start: -%dm)", rangeMinutes);
        } else if (startTime != null && endTime != null) {
            ZoneId zone = ZoneId.of("Asia/Seoul"); // 명확하게 KST 기준
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

        if (filters != null) {
            filters.forEach((k, v) -> {
                if (!List.of("origin", "measurement", "field").contains(k)) {
                    flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", k, v));
                }
            });
        }

        if (windowed) {
            flux.append(" |> aggregateWindow(every: 1h, fn: mean, createEmpty: false)");
        } else {
            flux.append(" |> mean()");
        }

        return flux.toString();
    }
}
