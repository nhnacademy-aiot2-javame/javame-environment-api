package com.nhnacademy.environment.dto;

import lombok.Getter;

/**
 * Environment Resource 조회 응답용 DTO.
 */
@Getter
public class ResourceDataDto {

    private final String time;
    private final String field;
    private final Double value;


    public ResourceDataDto(String time, String field, Double value) {
        this.time = time;
        this.field = field;
        this.value = value;
    }
}
