package com.nhnacademy.environment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class SensorDataDto {

    private String measurement;

    private Map<String, Object> fields;

    private Map<String, String> tags;

    //private Instant timestamp;
}
