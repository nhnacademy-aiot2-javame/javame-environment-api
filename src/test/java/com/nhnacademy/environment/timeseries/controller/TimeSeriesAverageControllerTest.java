package com.nhnacademy.environment.timeseries.controller;

import com.nhnacademy.environment.controller.TimeSeriesAverageController;
import com.nhnacademy.environment.timeseries.service.TimeSeriesAverageService;
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

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TimeSeriesAverageController.class)
@Import(TimeSeriesAverageControllerTest.MockConfig.class)
class TimeSeriesAverageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final TimeSeriesAverageService mockService = Mockito.mock(TimeSeriesAverageService.class);

    @TestConfiguration
    static class MockConfig {
        @Bean
        public TimeSeriesAverageService timeSeriesAverageService() {
            return mockService;
        }
    }

//    @BeforeEach
//    void setup() {
//        when(mockService.get1HourAverageSensorValues(eq("sensor_data"), eq("temperature"), anyMap(), eq(60)))
//                .thenReturn(List.of(21.5, 22.1));
//        when(mockService.getAverageSensorValue(eq("sensor_data"), eq("temperature"), anyMap(), eq(60)))
//                .thenReturn(21.8);
//        when(mockService.getAverageSensorValue(eq("sensor_data"), eq("temperature"), anyMap(), eq(1440)))
//                .thenReturn(20.5); // day
//        when(mockService.getAverageSensorValue(eq("sensor_data"), eq("temperature"), anyMap(), eq(10080)))
//                .thenReturn(19.9); // week
//        when(mockService.getAverageSensorValue(eq("sensor_data"), eq("temperature"), anyMap(), eq(43200)))
//                .thenReturn(18.7); // month
//    }
//
//    @Test
//    @DisplayName("컨트롤러 평균 응답 확인 - 1시간")
//    void testGet1HourAverageWithTotalAverage() throws Exception {
//        mockMvc.perform(get("/environment/nhnacademy.com/1h")
//                        .param("origin", "sensor_data")
//                        .param("measurement", "temperature")
//                        .param("rangeMinutes", "60"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.oneHourAverage[0]").value(21.5))
//                .andExpect(jsonPath("$.overallAverage").value(21.8));
//    }

//    @Test
//    @DisplayName("컨트롤러 평균 응답 확인 - 일간")
//    void testDayAverage() throws Exception {
//        mockMvc.perform(get("/environment/nhnacademy.com/day")
//                        .param("origin", "sensor_data")
//                        .param("measurement", "temperature"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.average").value(20.5));
//    }
//
//    @Test
//    @DisplayName("컨트롤러 평균 응답 확인 - 주간")
//    void testWeeklyAverage() throws Exception {
//        mockMvc.perform(get("/environment/nhnacademy.com/weekly")
//                        .param("origin", "sensor_data")
//                        .param("measurement", "temperature"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.average").value(19.9));
//    }
//
//    @Test
//    @DisplayName("컨트롤러 평균 응답 확인 - 월간")
//    void testMonthlyAverage() throws Exception {
//        mockMvc.perform(get("/environment/nhnacademy.com/monthly")
//                        .param("origin", "sensor_data")
//                        .param("measurement", "temperature"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.average").value(18.7));
//    }
}