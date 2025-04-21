package com.nhnacademy.environment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Environment Resource 조회 응답용 DTO.
 */
@Getter
@AllArgsConstructor
public class ResourceDataDto {

    private String time;
    private String field;
    private Double value;

}
