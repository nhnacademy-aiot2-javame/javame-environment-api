package com.nhnacademy.environment.timeseries.service;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.timeseries.service.TimeSeriesAverageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 단위 테스트: TimeSeriesAverageService
 * - InfluxDB Flux 쿼리를 기반으로 평균 센서 데이터를 조회하는 기능을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class TimeSeriesAverageServiceTest {

    @Mock
    QueryApi queryApi;

    @InjectMocks
    TimeSeriesAverageService averageService;

    @BeforeEach
    void setUp() {
        averageService = new TimeSeriesAverageService(queryApi, "data", "my-org");
    }

    /**
     * 주어진 시간 범위 내 평균값이 존재할 경우 정상적으로 반환되는지 테스트합니다.
     */
//    @Test
//    @DisplayName("getAverageSensorValue(rangeMinutes): 정상적인 경우 평균값 반환")
//    void testGetAverageSensorValue_returnsCorrectAverage() {
//        FluxRecord fluxRecord = mock(FluxRecord.class);
//        when(fluxRecord.getValue()).thenReturn(25.0);
//
//        FluxTable table = mock(FluxTable.class);
//        when(table.getRecords()).thenReturn(List.of(fluxRecord));
//        when(queryApi.query(anyString(), anyString())).thenReturn(List.of(table));
//
//        Double result = averageService.getAverageSensorValue(
//                "origin", "temperature", Map.of(), 60
//        );
//
//        assertThat(result).isEqualTo(25.0);
//    }
//
//    /**
//     * 데이터가 존재하지 않을 경우 null이 반환되는지 테스트합니다.
//     */
//    @Test
//    @DisplayName("getAverageSensorValue(rangeMinutes): 데이터 없을 시 null 반환")
//    void testGetAverageSensorValue_returnsNull_whenNoData() {
//        when(queryApi.query(anyString(), anyString())).thenReturn(List.of());
//
//        Double result = averageService.getAverageSensorValue(
//                "origin", "temperature", Map.of(), 60
//        );
//
//        assertThat(result).isNull();
//    }

//    /**
//     * 시작/종료 시간이 고정된 경우의 평균값 조회 기능을 검증합니다.
//     */
//    @Test
//    @DisplayName("getFixedRangeAverageSensorValue: 고정 시간 범위 평균값 반환")
//    void testGetFixedRangeAverageSensorValue() {
//        FluxRecord record = mock(FluxRecord.class);
//        when(record.getValue()).thenReturn(12.34);
//
//        FluxTable table = mock(FluxTable.class);
//        when(table.getRecords()).thenReturn(List.of(record));
//        when(queryApi.query(anyString(), anyString())).thenReturn(List.of(table));
//
//        LocalDateTime start = LocalDateTime.now().minusHours(1);
//        LocalDateTime end = LocalDateTime.now();
//
//        Double result = averageService.getFixedRangeAverageSensorValue("origin", "cpu", Map.of(), start, end);
//
//        assertThat(result).isEqualTo(12.34);
//    }

//    /**
//     * 1시간 단위 집계 쿼리를 통한 평균 리스트 반환 기능 테스트
//     */
//    @Test
//    @DisplayName("get1HourAverageSensorValues: 평균 리스트 반환")
//    void testGet1HourAverageSensorValues() {
//        FluxRecord r1 = mock(FluxRecord.class);
//        when(r1.getValue()).thenReturn(10.0);
//        FluxRecord r2 = mock(FluxRecord.class);
//        when(r2.getValue()).thenReturn(20.0);
//
//        FluxTable table = mock(FluxTable.class);
//        when(table.getRecords()).thenReturn(List.of(r1, r2));
//        when(queryApi.query(anyString(), anyString())).thenReturn(List.of(table));
//
//        List<Double> result = averageService.get1HourAverageSensorValues("origin", "cpu", Map.of(), 60);
//
//        assertThat(result).containsExactly(10.0, 20.0);
//    }
}
