package com.nhnacademy.environment.timeseries.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.environment.timeseries.controller.TimeSeriesSseController;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TimeSeriesSseController.class)
@Import(TimeSeriesSseControllerTest.MockConfig.class)
class TimeSeriesSseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final TimeSeriesDataService mockService = Mockito.mock(TimeSeriesDataService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @TestConfiguration
    static class MockConfig {
        @Bean
        public TimeSeriesDataService timeSeriesDataService() {
            return mockService;
        }

        @Bean
        public ObjectMapper objectMapper() {
            return objectMapper;
        }
    }

    @BeforeEach
    void setUp() {
        Mockito.when(mockService.getTimeSeriesData(eq("sensor_data"), anyMap(), anyInt()))
                .thenReturn(Map.of("temperature", List.of(new TimeSeriesDataDto())));
    }

    @Test
    @DisplayName("SSE 연결 요청 테스트 - /time-series-stream")
    void testSseConnection() throws Exception {
        mockMvc.perform(get("/environment/nhnacademy.com/time-series-stream")
                        .param("origin", "sensor_data")
                        .param("range", "60")
                        .param("location", "lab"))
                .andExpect(status().isOk());
    }
}