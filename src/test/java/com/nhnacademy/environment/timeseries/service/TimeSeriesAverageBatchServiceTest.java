package com.nhnacademy.environment.timeseries.service;

import com.nhnacademy.environment.timeseries.domain.AverageData;
import com.nhnacademy.environment.timeseries.repository.AverageDataRepository;
import com.nhnacademy.environment.timeseries.service.TimeSeriesAverageBatchService;
import com.nhnacademy.environment.timeseries.service.TimeSeriesAverageService;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeSeriesAverageBatchServiceTest {

    @Mock
    TimeSeriesAverageService averageService;

    @Mock
    TimeSeriesDataService timeSeriesDataService;

    @Mock
    AverageDataRepository averageDataRepository;

    @InjectMocks
    TimeSeriesAverageBatchService batchService;

    @BeforeEach
    void setup() {
        batchService = new TimeSeriesAverageBatchService(averageService, timeSeriesDataService, averageDataRepository);
    }

    @Test
    @DisplayName("saveDailyAverage(): 평균 저장이 성공적으로 수행되는지 검증")
    void testSaveDailyAverage() {
        // given
        when(timeSeriesDataService.getTagValues(eq("companyDomain"), any()))
                .thenReturn(List.of("nhnacademy"));
        when(timeSeriesDataService.getOriginList("nhnacademy"))
                .thenReturn(List.of("sensor_data"));
        when(timeSeriesDataService.getMeasurementList(anyMap()))
                .thenReturn(List.of("temperature"));
        when(averageService.getAverageSensorValue(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(23.45);

        // when
        batchService.saveDailyAverage();

        // then
        verify(averageDataRepository, atLeastOnce()).save(any(AverageData.class));
    }

    @Test
    @DisplayName("평균값이 null일 경우 저장하지 않음")
    void testSaveDailyAverage_doesNotSaveWhenAverageIsNull() {
        // given
        when(timeSeriesDataService.getTagValues(eq("companyDomain"), any()))
                .thenReturn(List.of("nhnacademy"));
        when(timeSeriesDataService.getOriginList("nhnacademy"))
                .thenReturn(List.of("sensor_data"));
        when(timeSeriesDataService.getMeasurementList(anyMap()))
                .thenReturn(List.of("temperature"));
        when(averageService.getAverageSensorValue(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(null);

        // when
        batchService.saveDailyAverage();

        // then
        verify(averageDataRepository, never()).save(any(AverageData.class));
    }


}
