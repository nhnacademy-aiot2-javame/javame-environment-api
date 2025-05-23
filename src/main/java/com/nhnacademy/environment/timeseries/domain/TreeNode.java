package com.nhnacademy.environment.timeseries.domain;

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
    private List<TreeNode> children = new ArrayList<>();

    public TreeNode(String label, String tag) {
        this.label = label;
        this.tag = tag;
    }

    public void addChild(TreeNode child) {
        children.add(child);
    }
}
