package com.nhnacademy.environment.timeseries.service;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.timeseries.dto.ChartDataDto;
import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeSeriesDataServiceTest {

    @Mock
    QueryApi queryApi;

    @InjectMocks
    TimeSeriesDataService timeSeriesDataService;

    @BeforeEach
    void setUp() {
        timeSeriesDataService = new TimeSeriesDataService(queryApi, "test-bucket", "test-org");
    }

    @Test
    @DisplayName("getOriginList(): origin 목록 반환")
    void testGetOriginList() {
        when(queryApi.query(anyString(), anyString())).thenReturn(List.of());
        List<String> result = timeSeriesDataService.getOriginList("nhnacademy");

        assertThat(result).isNotNull(); // 내부 extract 메서드의 실제 호출 여부는 별도 테스트 필요
    }

    @Test
    @DisplayName("getChartData(): 라인 차트 데이터 반환")
    void testGetChartData() {
        FluxRecord record = mock(FluxRecord.class);
        when(record.getTime()).thenReturn(Instant.now());
        when(record.getValue()).thenReturn(12.34);

        FluxTable table = mock(FluxTable.class);
        when(table.getRecords()).thenReturn(List.of(record));
        when(queryApi.query(anyString(), anyString())).thenReturn(List.of(table));

        ChartDataDto result = timeSeriesDataService.getChartData("cpu", "usage", Map.of("origin", "server_data"), 60);
        assertThat(result.getLabels()).isNotEmpty();
        assertThat(result.getValues()).containsExactly(12.34);
    }

    @Test
    @DisplayName("getPieChartData(): 측정값 분포 반환")
    void testGetPieChartData() {
        FluxRecord record = mock(FluxRecord.class);
        when(record.getValueByKey("_measurement")).thenReturn("cpu");
        when(record.getValue()).thenReturn(10.0);

        FluxTable table = mock(FluxTable.class);
        when(table.getRecords()).thenReturn(List.of(record));
        when(queryApi.query(anyString(), anyString())).thenReturn(List.of(table));

        ChartDataDto result = timeSeriesDataService.getPieChartData(Map.of("origin", "server_data"));
        assertThat(result.getLabels()).contains("cpu");
        assertThat(result.getValues()).contains(10.0);
    }

    @Test
    @DisplayName("getTimeSeriesData(): 측정값 기준 그룹화된 Map 반환")
    void testGetTimeSeriesData() {
        FluxRecord record = mock(FluxRecord.class);
        when(record.getTime()).thenReturn(Instant.now());
        when(record.getValue()).thenReturn(1.23);
        when(record.getValueByKey("_measurement")).thenReturn("cpu");

        FluxTable table = mock(FluxTable.class);
        when(table.getRecords()).thenReturn(List.of(record));
        when(queryApi.query(anyString(), anyString())).thenReturn(List.of(table));

        Map<String, List<TimeSeriesDataDto>> result = timeSeriesDataService.getTimeSeriesData("server_data", Map.of("device_id", "abc123"), 60);
        assertThat(result).containsKey("cpu");
    }
}
