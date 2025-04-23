package com.nhnacademy.environment.service;

import com.influxdb.client.QueryApi;
import com.influxdb.client.domain.Query;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.dto.ResourceDataDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class ServerEnvironmentService {

    private final QueryApi queryApi;
    private final String bucket;
    private final String influxOrganization;

    public ServerEnvironmentService(QueryApi queryApi,
                                    @Qualifier("influxBucket") String bucket,
                                    @Qualifier("influxOrganization") String influxOrganization) {
        this.queryApi = queryApi;
        this.bucket = bucket;
        this.influxOrganization = influxOrganization;
    }

    public Map<String, List<ResourceDataDto>> getDynamicResourceData(String measurement,
                                                                     String field,
                                                                     Map<String, String> tags,
                                                                     int rangeMinutes) {

        StringBuilder query = new StringBuilder(String.format(
                "from(bucket: \"%s\") |> range(start: -%dm)", bucket, rangeMinutes));

        query.append(String.format(" |> filter(fn: (r) => r._measurement == \"%s\")", measurement));
        query.append(String.format(" |> filter(fn: (r) => r._field == \"%s\")", field));

        for (Map.Entry<String, String> tag : tags.entrySet()) {
            query.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", tag.getKey(), tag.getValue()));
        }

        query.append(" |> keep(columns: [\"_time\", \"_field\", \"_value\"");
        for (String tagKey : tags.keySet()) {
            query.append(", \"").append(tagKey).append("\"");
        }
        query.append("])\n  |> sort(columns:[\"_time\"])");

        if (log.isDebugEnabled()) {
            log.debug("Generated Flux Query: {}", query);
        }

        Map<String, List<ResourceDataDto>> resultMap = new HashMap<>();

        try {
            List<FluxTable> tables = queryApi.query(query.toString(), influxOrganization);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    String key = tags.keySet().stream()
                            .map(k -> k + "=" + record.getValueByKey(k))
                            .reduce((a, b) -> a + "," + b)
                            .orElse("default");

                    Object value = record.getValue();
                    if (value instanceof Number number) {
                        double numericValue = number.doubleValue();

                        ResourceDataDto dto = new ResourceDataDto(
                                Objects.requireNonNull(record.getTime()).toString(),
                                record.getField(),
                                numericValue
                        );

                        resultMap.computeIfAbsent(key, k -> new ArrayList<>()).add(dto);
                    } else {
                        log.warn("Non-numeric value encountered at time {}: {}", record.getTime(), value);
                    }
                }
            }
        } catch (Exception e) {
            log.error("InfluxDB dynamic query failed", e);
        }

        logCpuMetrics(resultMap);
        return resultMap;
    }

    private void logCpuMetrics(Map<String, List<ResourceDataDto>> resultMap) {
        resultMap.forEach((tagKey, dataList) -> {
            log.info("[CPU Metrics] Tag = {}", tagKey);

            dataList.stream()
                    .sorted(Comparator.comparing(ResourceDataDto::getTime))
                    .forEach(data -> {
                        switch (data.getField()) {
                            case "usage_idle" -> log.info("[usage_idle]    {} : {}%", data.getTime(), data.getValue());
                            case "usage_user" -> log.info("[usage_user]    {} : {}%", data.getTime(), data.getValue());
                            case "usage_system" -> log.info("[usage_system]  {} : {}%", data.getTime(), data.getValue());
                            case "usage_iowait" -> log.info("[usage_iowait]  {} : {}%", data.getTime(), data.getValue());
                            default -> log.info("[other_field]    {} : {}%", data.getTime(), data.getValue());
                        }
                    });
        });
    }
}
