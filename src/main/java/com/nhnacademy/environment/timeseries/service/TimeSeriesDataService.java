package com.nhnacademy.environment.timeseries.service;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.timeseries.dto.ChartDataDto;
import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto;
import com.nhnacademy.environment.util.InfluxUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class TimeSeriesDataService {

    private final QueryApi queryApi;
    private final String bucket;
    private final String influxOrg;

    public TimeSeriesDataService(QueryApi queryApi,
                                 @Qualifier("influxBucket") String bucket,
                                 @Qualifier("influxOrganization") String influxOrg) {
        this.queryApi = queryApi;
        this.bucket = bucket;
        this.influxOrg = influxOrg;
    }

    /**
     * origin 및 다양한 태그 필터를 기준으로 시계열 데이터를 조회하여 Map 형태로 반환합니다.
     * 측정값(measurement) 기준으로 그룹화됩니다.
     */
    public Map<String, List<TimeSeriesDataDto>> getTimeSeriesData(
            String origin,
            Map<String, String> allParams,
            int rangeMinutes
    ) {
        StringBuilder flux = new StringBuilder(
                String.format("from(bucket: \"%s\") |> range(start: -%dm)", bucket, rangeMinutes)
        );

        flux.append(String.format(" |> filter(fn: (r) => r[\"origin\"] == \"%s\")", origin));

        allParams.forEach((key, value) -> {
            if (!key.equals("origin") && value != null && !value.isBlank()) {
                flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", key, value));
            }
        });

        flux.append(" |> keep(columns: [\"_time\", \"_field\", \"_value\", \"_measurement\", \"location\"");
        allParams.keySet().forEach(k -> flux.append(", \"" + k + "\""));
        flux.append("]) |> sort(columns: [\"_time\"])\n");

        log.debug("[TimeSeries] Flux query = {}", flux);

        Map<String, List<TimeSeriesDataDto>> resultMap = new HashMap<>();

        try {
            List<FluxTable> tables = queryApi.query(flux.toString(), influxOrg);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Instant time = record.getTime();
                    double value = ((Number) record.getValue()).doubleValue();
                    String measurement = (String) record.getValueByKey("_measurement");
                    String location = InfluxUtil.getTagValue(record, "location");

                    Map<String, String> tags = new HashMap<>();
                    for (String tagKey : allParams.keySet()) {
                        tags.put(tagKey, InfluxUtil.getTagValue(record, tagKey));
                    }
                    tags.put("origin", origin);

                    TimeSeriesDataDto dto = new TimeSeriesDataDto(time, location, value, measurement, tags);
                    resultMap.computeIfAbsent(measurement, k -> new ArrayList<>()).add(dto);
                }
            }
        } catch (Exception e) {
            log.error("TimeSeriesData query 실패", e);
        }

        return resultMap;
    }

    /**
     * measurement 컬럼 기준으로 중복 제거된 값을 조회합니다.
     */
    public List<String> getMeasurementList(Map<String, String> filters) {
        StringBuilder flux = new StringBuilder(
                String.format("from(bucket: \"%s\") |> range(start: -24h)", bucket)
        );

        filters.forEach((key, value) ->
                flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", key, value))
        );

        flux.append(" |> keep(columns: [\"_measurement\"]) |> distinct(column: \"_measurement\")");

        return InfluxUtil.extractDistinctValues(queryApi, flux.toString(), influxOrg, "_measurement");
    }

    /**
     * 특정 태그 컬럼에 대해 중복 제거된 값을 조회합니다.
     */
    public List<String> getTagValues(String tag, Map<String, String> filters) {
        StringBuilder flux = new StringBuilder(
                String.format("from(bucket: \"%s\") |> range(start: -24h)", bucket)
        );

        filters.forEach((key, value) ->
                flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", key, value))
        );

        flux.append(String.format(" |> keep(columns: [\"%s\"]) |> distinct(column: \"%s\")", tag, tag));

        return InfluxUtil.extractDistinctValues(queryApi, flux.toString(), influxOrg, tag);
    }

    /**
     * 주어진 회사 도메인에 해당하는 origin 목록을 조회합니다.
     */
    public List<String> getOriginList(String companyDomain) {
        return getTagValues("origin", Map.of("companyDomain", companyDomain));
    }

    /**
     * 라인 차트용 시계열 데이터 생성 (측정값+필드 기준)
     */
    public ChartDataDto getChartData(String measurement, String field, Map<String, String> filters, int rangeMinutes) {
        StringBuilder flux = new StringBuilder(
                String.format("from(bucket: \"%s\") |> range(start: -%dm)", bucket, rangeMinutes)
        );

        flux.append(String.format(" |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")", measurement));
        flux.append(String.format(" |> filter(fn: (r) => r[\"_field\"] == \"%s\")", field));

        filters.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", key, value));
            }
        });

        flux.append(" |> sort(columns: [\"_time\"])");

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        try {
            List<FluxTable> tables = queryApi.query(flux.toString(), influxOrg);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Instant time = record.getTime();
                    String label = DateTimeFormatter.ofPattern("HH:mm")
                            .withZone(ZoneId.systemDefault())
                            .format(time);
                    Double value = ((Number) record.getValue()).doubleValue();

                    labels.add(label);
                    values.add(value);
                }
            }
        } catch (Exception e) {
            log.error("ChartData 쿼리 실패", e);
        }
        return new ChartDataDto(labels, values, measurement + "_" + field);
    }

    /**
     * 파이 차트용 측정값별 데이터 개수 집계
     */
    public ChartDataDto getPieChartData(Map<String, String> filters) {
        StringBuilder flux = new StringBuilder(
                String.format("from(bucket: \"%s\") |> range(start: -12h)", bucket)
        );

        filters.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", key, value));
            }
        });

        flux.append(" |> group(columns: [\"_measurement\"]) |> count()" +
                " |> keep(columns: [\"_measurement\", \"_value\"])");

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        try {
            List<FluxTable> tables = queryApi.query(flux.toString(), influxOrg);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    String measurement = (String) record.getValueByKey("_measurement");
                    Double count = ((Number) record.getValue()).doubleValue();

                    labels.add(measurement);
                    values.add(count);
                }
            }
        } catch (Exception e) {
            log.error("PieChart 쿼리 실패", e);
        }
        return new ChartDataDto(labels, values, "Measurement 분포");
    }
}
