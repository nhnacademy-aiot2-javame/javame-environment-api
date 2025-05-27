package com.nhnacademy.environment.timeseries.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TreeNodeDto {

    /**
     * 노드 이름 (예: building, location, origin, deviceId 등).
     */
    private String label;

    /**
     * 태그 유형 (예: building, location, origin, deviceId 등).
     */
    private String tag;

    /**
     * 하위 노드 목록.
     */
    private List<TreeNodeDto> children;
}
