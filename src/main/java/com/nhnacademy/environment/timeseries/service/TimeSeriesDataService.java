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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * InfluxDB 로부터 시계열 데이터를 조회 및 가공하는 서비스입니다.
 * 시계열 차트 및 드롭다운 필터 값, 측정값 리스트 등을 제공합니다.
 */
@Slf4j
@Service
public class TimeSeriesDataService {

    /** InfluxDB 쿼리 API 입니다. */
    private final QueryApi queryApi;

    /** InfluxDB 버킷 이름 입니다. */
    private final String bucket;

    /** InfluxDB 조직 이름 입니다. */
    private final String influxOrg;

    /**
     * 생성자 - 필수 설정 값들 주입 합니다.
     *
     * @param queryApi InfluxDB 쿼리 API
     * @param bucket InfluxDB 버킷 이름
     * @param influxOrg InfluxDB 조직 이름
     */
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
     *
     * @param origin 데이터 출처(origin 태그)
     * @param allParams 태그 필터 조건
     * @param rangeMinutes 조회 범위 (분)
     * @return 측정값 기준 그룹화된 시계열 데이터 맵
     */
    public Map<String, List<TimeSeriesDataDto>> getTimeSeriesData(String origin,
                                                                  Map<String, String> allParams,
                                                                  int rangeMinutes) {
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
        flux.append("]) |> sort(columns: [\"_time\"])");

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
     * 측정 항목(_measurement) 기준으로 중복 제거된 값을 조회합니다.
     * <p>
     * 중복 제거란, 동일한 측정 항목이 여러 시점에 기록되어 있어도 한 번만 반환된다는 의미입니다.
     * origin, location, companyDomain 등의 조건을 기반으로 필터링한 후,
     * 해당 조건을 만족하는 시계열 데이터에서 고유한 _measurement 이름만 추출합니다.
     *
     * @param filters origin, companyDomain, location 등 InfluxDB tag 필터
     * @return 중복 제거된 _measurement 목록 (측정 항목 리스트)
     */
    public List<String> getMeasurementList(Map<String, String> filters) {
        String origin = filters.get("origin");
        String companyDomain = filters.get("companyDomain");
        String location = filters.get("location");

        StringBuilder flux = new StringBuilder();
        flux.append("from(bucket: \"").append(bucket).append("\")")
                .append(" |> range(start: -1h)")
                .append(" |> filter(fn: (r) => r.origin == \"").append(origin).append("\"")
                .append(" and r.companyDomain == \"").append(companyDomain).append("\"");

        if (location != null) {
            flux.append(" and r.location == \"").append(location).append("\"");
        }

        flux.append(")")
                .append(" |> keep(columns: [\"_measurement\"])")
                .append(" |> distinct(column: \"_measurement\")");

        log.info("[Flux Measurement Query] : {}", flux.toString());

        List<FluxTable> tables = queryApi.query(flux.toString(), influxOrg);
        List<String> result = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                result.add((String) record.getValue());
            }
        }
        return result;
    }

    /**
     * 특정 태그 컬럼에 대해 중복 제거된 값을 조회합니다.
     *
     * @param tag 조회할 태그명
     * @param filters 필터 조건
     * @return 해당 태그의 고유값 리스트
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
     *
     * @param companyDomain 회사 도메인
     * @return origin 리스트
     */
    public List<String> getOriginList(String companyDomain) {
        return getTagValues("origin", Map.of("companyDomain", companyDomain));
    }

    /**
     * 라인 차트용 시계열 데이터를 조회합니다. (측정값 + 필드 기준)
     *
     * @param measurement 측정값
     * @param field 필드 이름
     * @param filters 필터 조건
     * @param rangeMinutes 시간 범위(분)
     * @return ChartDataDto 객체
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
     * 파이 차트용 측정값별 데이터 개수 집계를 반환합니다.
     *
     * @param filters 필터 조건
     * @return ChartDataDto 객체
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

    /**
     * 주어진 필터 조건에 맞는 가장 최근의 시계열 데이터 하나를 조회합니다.
     * InfluxDB에서 시간 역순으로 정렬 후 첫 번째 데이터를 가져옵니다.
     *
     * @param filters 필터 조건 (origin, location, _measurement, _field 등 포함 가능)
     * @return 가장 최근의 TimeSeriesDataDto, 없으면 null
     */
    public TimeSeriesDataDto getLatestTimeSeriesData(Map<String, String> filters) {
        StringBuilder flux = new StringBuilder(
                // 적절한 조회 범위 설정 (예: 최근 7일, 너무 짧으면 데이터가 없을 수 있음)
                String.format("from(bucket: \"%s\") |> range(start: -7d)", bucket)
        );

        // 필수 필터 (_measurement, origin, location 등) 적용
        // 예시: filters 맵에 있는 모든 키-값을 필터로 적용
        filters.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                // _measurement, _field는 InfluxDB의 시스템 컬럼이므로 r._measurement, r._field로 접근
                // 나머지 태그들은 r["tagName"] 형태로 접근
                if (key.equals("_measurement") || key.equals("_field")) {
                    flux.append(String.format(" |> filter(fn: (r) => r.%s == \"%s\")", key, value));
                } else {
                    flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", key, value));
                }
            }
        });

        // 최신 데이터를 가져오기 위해 시간 역순 정렬 후 1개만 선택
        flux.append(" |> sort(columns: [\"_time\"], desc: true)");
        flux.append(" |> limit(n:1)");
        // 필요한 컬럼만 유지 (선택 사항, 성능에 영향 줄 수 있음)
        // flux.append(" |> keep(columns: [\"_time\", \"_value\", \"_measurement\", \"location\", \"origin\", ...])");


        log.debug("[LatestData] Flux query = {}", flux.toString());

        List<TimeSeriesDataDto> results = new ArrayList<>();
        try {
            List<FluxTable> tables = queryApi.query(flux.toString(), influxOrg);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Instant time = record.getTime();
                    // _value의 실제 타입에 따라 캐스팅 필요
                    Object rawValue = record.getValue();
                    double value = 0.0;
                    if (rawValue instanceof Number) {
                        value = ((Number) rawValue).doubleValue();
                    } else if (rawValue != null) {
                        // 다른 타입일 경우 처리 (예: String.valueOf(rawValue) 후 Double.parseDouble)
                        log.warn("Unexpected value type for _value: " + rawValue.getClass().getName());
                    }

                    String measurement = InfluxUtil.getTagValue(record, "_measurement"); // getValueByKey 사용 가능
                    String location = InfluxUtil.getTagValue(record, "location");
                    String origin = InfluxUtil.getTagValue(record, "origin");

                    // 모든 필터 태그 값을 가져오려면 filters.keySet()을 순회하며 InfluxUtil.getTagValue 사용
                    Map<String, String> recordTags = new HashMap<>();
                    recordTags.put("origin", origin);
                    recordTags.put("location", location);
                    // 필요한 다른 태그들도 추가

                    results.add(new TimeSeriesDataDto(time, location, value, measurement, recordTags));
                }
            }
        } catch (Exception e) {
            log.error("LatestData query 실패", e);
            // 실패 시 null 또는 빈 Optional 반환 등 처리
            return null;
        }

        return results.isEmpty() ? null : results.get(0);
    }
}
