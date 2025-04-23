package com.nhnacademy.environment.service;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.dto.ResourceDataDto;
import com.nhnacademy.environment.dto.SensorDataDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SensorDataService {

    private final QueryApi queryApi;

    private final String bucket;

    private final String influxOrg;

    public SensorDataService(QueryApi queryApi,
                             @Qualifier("influxBucket") String bucket,
                             @Qualifier("influxOrganization") String influxOrg)
    {
        this.queryApi = queryApi;
        this.bucket = bucket;
        this.influxOrg = influxOrg;
    }

    /**
     * 센서 데이터를 조회하여 Map<tagKey, List<ResourceDataDto>> 형태로 반환
     */
    public Map<String, List<ResourceDataDto>> getSensorData(
            String measurement,
            Map<String, String> tags,
            int rangeMinutes
    ) {
        StringBuilder flux = new StringBuilder(
                String.format("from(bucket: \"%s\") |> range(start: -%dm)", bucket, rangeMinutes)
        );
        flux.append(String.format(" |> filter(fn: (r) => r._measurement == \"%s\")", measurement));
        tags.forEach((k, v) ->
                flux.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", k, v))
        );
        flux.append(" |> keep(columns: [\"_time\", \"_field\", \"_value\"");
        tags.keySet().forEach(k -> flux.append(",\"").append(k).append("\""));
        flux.append("]) |> sort(columns:[\"_time\"])");

        log.debug("Generated Flux query: {}", flux);

        Map<String, List<ResourceDataDto>> resultMap = new HashMap<>();

        try {
            List<FluxTable> tables = queryApi.query(flux.toString(), influxOrg);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    String tagKey = tags.keySet().stream()
                            .map(k -> k + "=" + record.getValueByKey(k))
                            .collect(Collectors.joining(","));

                    Object value = record.getValue();
                    if (value instanceof Number number) {
                        double numericValue = number.doubleValue();

                        ResourceDataDto dto = new ResourceDataDto(
                                Objects.requireNonNull(record.getTime()).toString(),
                                record.getField(),
                                numericValue
                        );

                        resultMap
                                .computeIfAbsent(tagKey.isEmpty() ? "default" : tagKey, k -> new ArrayList<>())
                                .add(dto);
                    } else {
                        log.warn("Non-numeric value encountered at time {}: {}", record.getTime(), value);
                    }
                }
            }
        } catch (Exception e) {
            log.error("InfluxDB sensor-data query failed", e);
        }

        logSensorMetrics(resultMap);
        return resultMap;
    }

    private void logSensorMetrics(Map<String, List<ResourceDataDto>> resultMap) {
        resultMap.forEach((tagKey, dataList) -> {
            log.info("▶ Sensor Data for Tag: {}", tagKey);

            dataList.stream()
                    .sorted(Comparator.comparing(ResourceDataDto::getTime))
                    .forEach(data ->
                            log.info("[{}] {} : {}", data.getTime(), data.getField(), data.getValue()));
        });
    }
}
