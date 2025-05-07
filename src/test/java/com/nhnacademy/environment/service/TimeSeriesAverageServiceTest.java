package com.nhnacademy.environment.service;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.timeseries.service.TimeSeriesAverageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
     * `TimeSeriesAverageService`의 `getAverageSensorValue` 메서드가
     * InfluxDB에서 모킹된 쿼리 결과를 기반으로 올바른 평균 센서 값을 계산하는지 테스트합니다.
     *
     * 이 테스트는 다음을 포함합니다:
     * 1. 사전 정의된 값을 가진 모킹된 `FluxRecord` 객체 생성
     * 2. 위에서 생성한 `FluxRecord`를 포함하는 모킹된 `FluxTable` 객체 생성
     * 3. `QueryApi`가 쿼리 시 해당 `FluxTable`을 반환하도록 모킹 설정
     * 4. `getAverageSensorValue` 메서드를 사용해 평균 센서 값을 가져오기
     * 위 과정을 통해 메서드가 모킹된 데이터를 올바르게 처리하고
     * 예상한 평균값을 반환하는지 검증합니다.
     */
    @Test
    void testGetAverageSensorValue_returnsCorrectAverage() {
        // given
        FluxRecord fluxRecord = mock(FluxRecord.class);
        when(fluxRecord.getValue()).thenReturn(25.0);

        FluxTable table = mock(FluxTable.class);
        when(table.getRecords()).thenReturn(List.of(fluxRecord));
        when(queryApi.query(anyString(), anyString())).thenReturn(List.of(table));

        //when
        Double result = averageService.getAverageSensorValue(
                "origin", "temperature", Map.of(), 60
        );

        //then
        assertThat(result).isEqualTo(25.0);
    }

    @Test
    void testGetAverageSensorValue_returnsNull_whenNoData() {
        when(queryApi.query(anyString(), anyString())).thenReturn(List.of()); // empty result

        Double result = averageService.getAverageSensorValue(
                "origin", "temperature", Map.of(), 60
        );

        assertThat(result).isNull();
    }


}
