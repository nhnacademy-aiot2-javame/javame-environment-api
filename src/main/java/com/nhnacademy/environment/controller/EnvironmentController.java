package com.nhnacademy.environment.controller;

import com.nhnacademy.environment.dto.ResourceDataDto;
import com.nhnacademy.environment.service.ServerEnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 서버 환경 리소스를 조회하는 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/environment") // API 기본 경로 설정
@RequiredArgsConstructor
public class EnvironmentController {

    private final ServerEnvironmentService serverEnvironmentService;

    /**
     * 전체 CPU 유휴율 데이터를 조회.
     * 예: /api/environment/cpu/idle?range=60
     *
     * @param rangeMinutes 조회 범위 (분 단위, 기본값 60)
     * @return 시간별 CPU 유휴율 리스트
     */
    @GetMapping("/cpu/idle")
    public ResponseEntity<List<ResourceDataDto>> getCpuIdle(
            @RequestParam(defaultValue = "60") int rangeMinutes) {
        Map<String, String> tags = Map.of("cpu", "cpu-total");
        List<ResourceDataDto> data =
                serverEnvironmentService.getResourceData("cpu", "usage_idle", tags, rangeMinutes);
        return ResponseEntity.ok(data);
    }

    /**
     * 메모리 사용률 데이터 조회.
     * 예: /api/environment/mem/used
     * @param rangeMinutes defaultValue: 60
     * @return serverEnvironmentService.getResourceData
     */
    @GetMapping("/mem/used")
    public ResponseEntity<List<ResourceDataDto>> getMemoryUsed(
            @RequestParam(defaultValue = "60") int rangeMinutes) {
        return ResponseEntity.ok(
                serverEnvironmentService.getResourceData("mem", "used_percent", Map.of(), rangeMinutes));
    }

    /**
     * 디스크 사용률 조회.
     * @param rangeMinutes defaultValue: 60
     * @return serverEnvironmentService.getResourceData
     */
    @GetMapping("/disk/used")
    public ResponseEntity<List<ResourceDataDto>> getDiskUsed(
            @RequestParam(defaultValue = "60") int rangeMinutes) {
        Map<String, String> tags = Map.of("path", "/");
        return ResponseEntity.ok(
                serverEnvironmentService.getResourceData("disk", "used_percent", tags, rangeMinutes));
    }

}

