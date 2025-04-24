package com.nhnacademy.environment.service;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.dto.SensorDataDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensorDataServiceTest {

    private QueryApi queryApi;
    private SensorDataService sensorDataService;

    @BeforeEach
    void setUp() {
        queryApi = mock(QueryApi.class);
        sensorDataService = new SensorDataService(queryApi, "test-bucket", "test-org");
    }

    @Test
    void testGetSonsorDataByOrigin() {

        // given
        FluxRecord mockRecord = mock(FluxRecord.class);
        when(mockRecord.getTime()).thenReturn(Instant.parse("2025-04-24T05:00:00Z"));
        when(mockRecord.getValue()).thenReturn(24.5);
        when(mockRecord.getField()).thenReturn("value");
        when(mockRecord.getValueByKey("_measurement")).thenReturn("temperature");
        when(mockRecord.getValueByKey("companyDomain")).thenReturn("nhnacademy");
        when(mockRecord.getValueByKey("building")).thenReturn("gyeongnam_campus");
        when(mockRecord.getValueByKey("place")).thenReturn("server_room");
        when(mockRecord.getValueByKey("deviceId")).thenReturn("device-01");
        when(mockRecord.getValueByKey("location")).thenReturn("입구");
        when(mockRecord.getValueByKey("origin")).thenReturn("sensor_data");

        FluxTable mockTable = mock(FluxTable.class);
        when(mockTable.getRecords()).thenReturn(Collections.singletonList(mockRecord));
        when(queryApi.query(anyString(), eq("test-org"))).thenReturn(Collections.singletonList(mockTable));

        Map<String, String> params = new HashMap<>();
        params.put("companyDomain", "nhnacademy");
        params.put("building", "gyeongnam_campus");

        // when
        Map<String, List<SensorDataDto>> result = sensorDataService.getSensorDataByOrigin("sensor_data", params, 60);

        // then
        assertThat(result).containsKey("temperature");
        assertThat(result.get("temperature")).hasSize(1);
        SensorDataDto dto = result.get("temperature").get(0);
        assertThat(dto.getValue()).isEqualTo(24.5);
        assertThat(dto.getTags().get("location")).isEqualTo("입구");
    }
}
