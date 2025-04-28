package com.nhnacademy.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.dto.SensorDataDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SensorDataService {

    private final QueryApi queryApi;
    private final String bucket;
    private final String influxOrg;

    public SensorDataService(QueryApi queryApi, @Qualifier("influxBucket") String bucket, @Qualifier("influxOrganization") String influxOrg) {
        this.queryApi = queryApi;
        this.bucket = bucket;
        this.influxOrg = influxOrg;
    }

    public Map<String, List<SensorDataDto>> getSensorDataByOrigin(
            String origin,
            Map<String, String> allParams,
            int rangeMinutes
    ) {
        StringBuilder flux = new StringBuilder(
                String.format("from(bucket: \"%s\") |> range(start: -%dm)", bucket, rangeMinutes)
        );

        flux.append(String.format(" |> filter(fn: (r) => r[\"origin\"] == \"%s\")", origin));

        // 선택적 필터 키
        List<String> filterKeys = Arrays.asList(
                "companyDomain", "building", "place", "deviceId", "location"
        );

        for (String key : filterKeys) {
            if (allParams.containsKey(key)) {
                String value = allParams.get(key);
                flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", key, value));
            }
        }

        flux.append(" |> keep(columns: [\"_time\", \"_field\", \"_value\", \"_measurement\", " +
                "\"companyDomain\", \"building\", \"place\", \"deviceId\", \"location\", \"origin\"]) ");
        flux.append("|> sort(columns: [\"_time\"]) ");

        log.debug("Generated Flux query: {}", flux);

        Map<String, List<SensorDataDto>> result = new HashMap<>();

        try {
            List<FluxTable> tables = queryApi.query(flux.toString(), influxOrg);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Instant time = record.getTime();
                    double value = ((Number) record.getValue()).doubleValue();
                    String field = record.getField();
                    String measurement = (String) record.getValueByKey("_measurement");

                    Map<String, String> tags = new HashMap<>();
                    tags.put("companyDomain", getTagValue(record, "companyDomain"));
                    tags.put("building", getTagValue(record, "building"));
                    tags.put("place", getTagValue(record, "place"));
                    tags.put("deviceId", getTagValue(record, "deviceId"));
                    tags.put("location", getTagValue(record, "location"));
                    tags.put("origin", getTagValue(record, "origin"));

                    SensorDataDto dto = new SensorDataDto(time, field, value, measurement, tags);

                    result.computeIfAbsent(measurement, k -> new ArrayList<>()).add(dto);
                }
            }
        } catch (Exception e) {
            log.error("InfluxDB sensor-data query failed", e);
        }

        logSensorData(result);
        return result;
    }

    private String getTagValue(FluxRecord record, String key) {
        Object value = record.getValueByKey(key);
        return value != null ? value.toString() : "";
    }

    private void logSensorData(Map<String, List<SensorDataDto>> resultMap) {
        resultMap.forEach((measurement, dataList) -> {
            log.info("▶ Measurement: {} ({}건)", measurement, dataList.size());
            dataList.stream()
                    .sorted(Comparator.comparing(SensorDataDto::getTime))
                    .forEach(data -> {
                        String origin = data.getTags().getOrDefault("origin", "unknown");
                        String place = data.getTags().getOrDefault("place", "unknown");
                        log.info("[{}] {} {}: {}", data.getTime(), origin, place, data.getValue());
                    });
        });

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            String jsonOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultMap);
            log.info("전체 응답 Map 데이터:\n{}", jsonOutput);
        } catch (Exception e) {
            log.error("JSON 직렬화 실패", e);

        }
    }

    public List<String> getTagValues(String column, Map<String, String> filters) {
        StringBuilder flux = new StringBuilder(
                String.format("from(bucket: \"%s\") |> range(start: -24h)", bucket)
        );

        filters.forEach((key, value) ->
                flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", key, value)));
        flux.append(String.format("|> keep(columns: [\"%s\"]) |> distinct(column: \"%s\")", column, column));

        log.debug("{} 쿼리 : {}", column, flux);

        try{
            return queryApi.query(flux.toString(), influxOrg).stream()
                    .flatMap(table -> table.getRecords().stream())
                    .map(record -> (String) record.getValueByKey(column))
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("{} 리스트 조회 실패", column, e);
            return Collections.emptyList();
        }
    }

    public List<String> getMeasurementList() {
        String flux = String.format(
                "from(bucket: \"%s\") |> range(start: -24h) " +
                        "|> keep(columns: [\"_measurement\"]) |> distinct(column: \"_measurement\")",
                bucket
        );

        log.debug("measurement 쿼리: {}", flux);

        try {
            return queryApi.query(flux, influxOrg).stream()
                    .flatMap(table -> table.getRecords().stream())
                    .map(record -> (String) record.getValueByKey("_measurement"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Measurement 리스트 조회 실패", e);
            return Collections.emptyList();
        }
    }

    public List<String> getLocationList(String origin, String companyDomain) {
        return getTagValues("location", Map.of("origin", origin, "companyDomain", companyDomain));
    }

    public List<String> getCompanyDomainList(String origin) {
        return getTagValues("companyDomain", Map.of("origin", origin));
    }

    public List<String> getBuildingList(String origin, String companyDomain) {
        return getTagValues("building", Map.of("origin", origin, "companyDomain", companyDomain));
    }

    public List<String> getPlaceList(String origin, String companyDomain) {
        return getTagValues("place", Map.of("origin", origin, "companyDomain", companyDomain));
    }

    public List<String> getDeviceIdList(String origin, String companyDomain) {
        return getTagValues("deviceId", Map.of("origin", origin, "companyDomain", companyDomain));
    }

    public List<String> getOriginList(String companyDomain) {
        return getTagValues("origin", Map.of("companyDomain", companyDomain));
    }

}
