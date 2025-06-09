package com.nhnacademy.environment.prediction.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourcePredictionDto {
    private String resourceType;
    private List<TimeSeriesDataPoint> historicalData;
    private List<TimeSeriesDataPoint> predictedData;
    private LocalDateTime splitTime;
}