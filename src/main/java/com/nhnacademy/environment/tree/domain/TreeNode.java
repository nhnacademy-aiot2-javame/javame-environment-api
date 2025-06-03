package com.nhnacademy.environment.tree.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TreeNode {

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
    private List<TreeNode> children = new ArrayList<>();

    public TreeNode(String label, String tag, String value) {
        this.label = label;
        this.tag = tag;
        this.value = value;
    }

    public void addChild(TreeNode child) {
        children.add(child);
    }
}
