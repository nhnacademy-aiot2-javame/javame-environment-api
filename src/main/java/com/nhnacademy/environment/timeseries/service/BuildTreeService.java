package com.nhnacademy.environment.timeseries.service;

import com.nhnacademy.environment.timeseries.domain.TreeNode;
import com.nhnacademy.environment.timeseries.dto.TreeNodeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildTreeService {

    private final TimeSeriesDataService timeSeriesDataService;

    public TreeNode buildTree(String companyDomain) {
        TreeNode root = new TreeNode("root", "root");
        Map<String, String> baseFilters = Map.of("companyDomain", companyDomain);

        List<String> tagLevels = List.of("building", "origin", "location", "deviceId", "place", "gatewayId", "measurement");

        buildRecursive(root, tagLevels, 0, baseFilters);

        log.debug("최종 트리: {}", root);
        return root;
    }

    private void buildRecursive(TreeNode parent, List<String> tagLevels, int level, Map<String, String> filters) {
        if (level >= tagLevels.size()) return;

        String currentTag = tagLevels.get(level);
        List<String> values = timeSeriesDataService.getTagValues(currentTag, filters);

        for (String value : values) {
            TreeNode child = new TreeNode(value, currentTag);
            parent.addChild(child);

            // 다음 필터 조건에 현재 값을 포함시켜서 하위 노드 구성
            Map<String, String> nextFilters = new java.util.HashMap<>(filters);
            nextFilters.put(currentTag, value);

            buildRecursive(child, tagLevels, level + 1, nextFilters);
        }
    }

    public TreeNodeDto convertToDto (TreeNode node) {
        List<TreeNodeDto> childDtos = node.getChildren().stream()
                .map(this::convertToDto)
                .toList();

        return new TreeNodeDto(node.getLabel(), node.getTag(), childDtos);
    }
}
