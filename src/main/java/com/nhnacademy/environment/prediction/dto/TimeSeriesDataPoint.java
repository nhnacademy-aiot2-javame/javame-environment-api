package com.nhnacademy.environment.prediction.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesDataPoint {
    private LocalDateTime timestamp;
    private Double value;
    private Double confidenceScore;
}