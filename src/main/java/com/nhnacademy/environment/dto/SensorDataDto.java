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

    /**
     * Instant time
     * → 데이터의 시각(타임스탬프). 시계열 데이터에서 필수
     */
    private Instant time;

    /**
     * String field
     * → InfluxDB의 _field 값(예: usage_idle, used_percent 등).
     * 여러 필드를 가진 measurement에서 어떤 필드 값인지 구분할 때 필요합니다.
     */
    private String field;

    /**
     * Double value
     * → 필드 값(예: 0.0, 1.0 등).
     * InfluxDB에서 측정한 값입니다.
     */
    private double value;

    /**
     * String measurement
     * → InfluxDB의 measurement 값(예: cpu, mem 등).
     * 여러 measurement에서 어떤 측정값인지 구분할 때 필요합니다.
     */
    private String measurement;

    /**
     Map<String, String> tags
     → InfluxDB의 태그(예: companyDomain, deviceId, location 등).
     태그 기반 필터링, 화면 표시, 상세 정보 제공에 유용합니다.
     */
    private Map<String, String> tags;

}

