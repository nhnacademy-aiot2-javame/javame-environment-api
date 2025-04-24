package com.nhnacademy.environment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SensorDataDto {
    private Instant time;
    private String field;
    private double value;
    private String measurement;
    private Map<String, String> tags;

}

