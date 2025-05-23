package com.nhnacademy.environment.timeseries.controller;

import com.nhnacademy.environment.timeseries.domain.TreeNode;
import com.nhnacademy.environment.timeseries.dto.TreeNodeDto;
import com.nhnacademy.environment.timeseries.service.BuildTreeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/environment/{companyDomain}")
@RequiredArgsConstructor
public class BuildTreeController {

    private final BuildTreeService buildTreeService;

    @GetMapping("/tree")
    public TreeNodeDto getTree(@PathVariable String companyDomain) {
        TreeNode tree = buildTreeService.buildTree(companyDomain);
        log.debug("반환되는 트리구조: {}", tree);
        return buildTreeService.convertToDto(tree);
    }

}
