package com.nhnacademy.environment.service;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.dto.ResourceDataDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
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

    public List<ResourceDataDto> getResourceData(String measurement, String field, Map<String, String> tags, int minutes) {
        StringBuilder query = new StringBuilder(
                String.format("from(bucket: \"%s\") |> range(start: -%dm) |> filter(fn: (r) => r._measurement == \"%s\")",
                        bucket, minutes, measurement)
        );

        query.append(String.format(" |> filter(fn: (r) => r._field == \"%s\")", field));

        for (Map.Entry<String, String> tag : tags.entrySet()) {
            query.append(String.format(" |> filter(fn: (r) => r.%s == \"%s\")", tag.getKey(), tag.getValue()));
        }

        query.append(" |> keep(columns: [\"_time\", \"_field\", \"_value\"])");

        List<ResourceDataDto> result = new ArrayList<>();

        try {
            List<FluxTable> tables = queryApi.query(query.toString(), influxOrganization);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    result.add(new ResourceDataDto(
                            record.getTime().toString(),
                            record.getField(),
                            ((Number) record.getValue()).doubleValue()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("InfluxDB query failed", e);
        }

        return result;
    }
}

