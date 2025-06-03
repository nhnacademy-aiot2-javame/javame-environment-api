package com.nhnacademy.environment.timeseries.controller;

import com.nhnacademy.environment.controller.TimeSeriesDataController;
import com.nhnacademy.environment.timeseries.dto.ChartDataDto;
import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TimeSeriesDataController.class)
@Import(TimeSeriesDataControllerTest.MockConfig.class)
class TimeSeriesDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final TimeSeriesDataService mockService = Mockito.mock(TimeSeriesDataService.class);

    @TestConfiguration
    static class MockConfig {
        @Bean
        public TimeSeriesDataService timeSeriesDataService() {
            return mockService;
        }
    }

    @BeforeEach
    void setUp() {
        Mockito.when(mockService.getOriginList("nhnacademy.com"))
                .thenReturn(List.of("sensor_data"));

        Mockito.when(mockService.getTagValues(eq("location"), anyMap()))
                .thenReturn(List.of("A101"));

        Mockito.when(mockService.getMeasurementList(anyMap()))
                .thenReturn(List.of("temperature"));

        Mockito.when(mockService.getTimeSeriesData(eq("sensor_data"), anyMap(), eq(180)))
                .thenReturn(Map.of("temperature", List.of(new TimeSeriesDataDto())));

        Mockito.when(mockService.getChartData(eq("temperature"), eq("value"), anyMap(), eq(60)))
                .thenReturn(new ChartDataDto());

        Mockito.when(mockService.getPieChartData(anyMap()))
                .thenReturn(new ChartDataDto());
    }

    @Test
    @DisplayName("/origins API 테스트")
    void testGetOrigins() throws Exception {
        mockMvc.perform(get("/environment/nhnacademy.com/origins"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/dropdown/{tag} API 테스트")
    void testGetDropdown() throws Exception {
        mockMvc.perform(get("/environment/nhnacademy.com/dropdown/location")
                        .param("origin", "sensor_data"))
                .andExpect(status().isOk());
    }

//    @Test
//    @DisplayName("/measurements API 테스트")
//    void testGetMeasurements() throws Exception {
//        mockMvc.perform(get("/environment/nhnacademy.com/measurements")
//                        .param("origin", "sensor_data"))
//                .andExpect(status().isOk());
//    }

    @Test
    @DisplayName("/time-series API 테스트")
    void testGetTimeSeriesData() throws Exception {
        mockMvc.perform(get("/environment/nhnacademy.com/time-series")
                        .param("origin", "sensor_data"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/chart/type/{sensor} API 테스트")
    void testChartType() throws Exception {
        mockMvc.perform(get("/environment/nhnacademy.com/chart/type/temperature")
                        .param("origin", "sensor_data"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/chart/pie API 테스트")
    void testChartPie() throws Exception {
        mockMvc.perform(get("/environment/nhnacademy.com/chart/pie")
                        .param("origin", "sensor_data"))
                .andExpect(status().isOk());
    }
}