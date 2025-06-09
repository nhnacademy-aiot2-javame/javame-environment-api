package com.nhnacademy.environment.tree.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import com.nhnacademy.environment.tree.domain.TreeNode;
import com.nhnacademy.environment.tree.dto.TreeNodeDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildTreeService {

    private final TimeSeriesDataService timeSeriesDataService;

    private Map<String, String> translationMap;

    @PostConstruct
    public void loadTranslationMap() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("translation.json")) {
            if (inputStream == null) {
                throw new IllegalStateException("translation.json not found");
            }
            ObjectMapper objectMapper = new ObjectMapper();
            translationMap = objectMapper.readValue(inputStream, new TypeReference<>() { });
        } catch (IOException e) {
            throw new RuntimeException("Failed to load translation.json", e);
        }
    }

    public TreeNode buildTree(String companyDomain) {
        TreeNode root = new TreeNode("루트", "root", "root");
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

        if (values.isEmpty()) {
            // gatewayId 등에서 값이 없으면, 이 단계를 건너뛰고 다음 단계로 진행
            buildRecursive(parent, tagLevels, level + 1, filters);
            return;
        }

        for (String value : values) {
            String label = translationMap.getOrDefault(value, value);
            TreeNode child = new TreeNode(label, currentTag, value);
            parent.addChild(child);

            Map<String, String> nextFilters = new HashMap<>(filters);
            nextFilters.put(currentTag, value); // 쿼리는 영문 그대로 사용

            buildRecursive(child, tagLevels, level + 1, nextFilters);
        }
    }


    /**
     * 도메인 트리 구조(TreeNode)를 DTO(TreeNodeDto) 구조로 재귀적으로 변환합니다.
     * @param node 변환할 트리 노드(TreeNode). 해당 노드를 루트로 하여 자식 노드까지 모두 DTO 로 변환됩니다.
     * @return 변환된 트리 구조의 TreeNodeDto 객체
     */
    public TreeNodeDto convertToDto(TreeNode node) {
        List<TreeNodeDto> childDtos = node.getChildren().stream()
                .map(this::convertToDto)
                .toList();

        return new TreeNodeDto(
                node.getLabel(),     // 한글 라벨
                node.getTag(),       // 태그 키
                node.getValue(),     // 영문 원본 값 (쿼리용)
                childDtos
        );
    }
}
