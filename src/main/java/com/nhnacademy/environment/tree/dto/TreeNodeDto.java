package com.nhnacademy.environment.tree.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TreeNodeDto {

    /**
     * 프론트에 보여주는 노드 이름 (예: usage_idle 등).
     */
    private String label;

    /**
     * 태그 유형 (예: building, location, origin, deviceId 등).
     */
    private String tag;

    /**
     * fetch 요청시 실제 쿼리에서 사용한 영문 원본.
     */
    private String value;

    /**
     * 상위 태그에 대한 자식 노드.
     * 최하위 노드는 자식이 없습니다.
     */
    private List<TreeNodeDto> children;
}
