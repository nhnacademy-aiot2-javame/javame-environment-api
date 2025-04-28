package com.nhnacademy.environment.service;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SensorAverageService {

    private final QueryApi queryApi;
    private final String bucket;
    private final String influxOrg;

    public SensorAverageService(QueryApi queryApi,
                                @Qualifier("influxBucket") String bucket,
                                @Qualifier("influxOrganization") String influxOrg) {
        this.queryApi = queryApi;
        this.bucket = bucket;
        this.influxOrg = influxOrg;
    }

    /**
     * 5분 단위 평균값 리스트 조회
     */
    public List<Double> get1HourAverageSensorValues(
            String origin,
            String measurement,
            Map<String, String> additionalFilters,
            int rangeMinutes
    ) {
        String flux = buildFluxQuery(origin, measurement, "value", additionalFilters, rangeMinutes, true);

        log.debug("1시간 단위 평균 Flux 쿼리: {}", flux);

        try {
            List<FluxTable> tables = queryApi.query(flux, influxOrg);

            List<Double> averages = tables.stream()
                    .flatMap(table -> table.getRecords().stream())
                    .map(record -> (Double) record.getValue())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (averages.isEmpty()) {
                log.error("▶ 1시간 단위 평균 조회 결과 없음 - measurement: {}", measurement);
            } else {
                log.debug("▶ 1시간 단위 평균 조회 성공 - measurement: {}, 데이터 건수: {}", measurement , averages.size());
            }
            return averages;
        } catch (Exception e) {
            log.error("▶ 1시간 단위 평균 조회 실패 - measurement: {}", measurement, e);
            return List.of();
        }
    }

    /**
     * 전체 평균값 단일 조회
     */
    public Double getAverageSensorValue(
            String origin,
            String measurement,
            Map<String, String> additionalFilters,
            int rangeMinutes
    ) {
        String flux = buildFluxQuery(origin, measurement, "value", additionalFilters, rangeMinutes, false);

        log.debug("전체 평균 Flux 쿼리: {}", flux);

        try {
            List<FluxTable> tables = queryApi.query(flux, influxOrg);

            return tables.stream()
                    .flatMap(table -> table.getRecords().stream())
                    .map(record -> (Double) record.getValue())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.error("▶ 전체 평균 조회 실패 - measurement: {}", measurement, e);
            return null;
        }
    }

    /**
     * Flux 쿼리 생성 공통 메서드
     */
    private String buildFluxQuery(String origin, String measurement, String field,
                                  Map<String, String> additionalFilters, int rangeMinutes, boolean aggregateWindow) {

        StringBuilder flux = new StringBuilder(
                String.format("from(bucket: \"%s\") |> range(start: -%dm)", bucket, rangeMinutes)
        );

        flux.append(String.format(" |> filter(fn: (r) => r[\"origin\"] == \"%s\")", origin));
        flux.append(String.format(" |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")", measurement));
        flux.append(String.format(" |> filter(fn: (r) => r[\"_field\"] == \"%s\")", field));

        if (additionalFilters != null) {
            additionalFilters.forEach((key, value) -> {
                if (!key.equals("origin") && !key.equals("measurement") && !key.equals("field")) {
                    flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", key, value));
                }
            });
        }

        if (aggregateWindow) {
            flux.append(" |> aggregateWindow(every: 1h, fn: mean, createEmpty: false)");
        } else {
            flux.append(" |> mean()");
        }

        return flux.toString();
    }
}
