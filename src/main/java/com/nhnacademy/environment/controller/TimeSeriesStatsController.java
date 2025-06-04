package com.nhnacademy.environment.controller;

import com.nhnacademy.environment.timeseries.service.TimeSeriesStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 시계열 데이터 통계 컨트롤러
 * 대시보드용 서비스/서버/센서 개수 등 통계 정보 제공
 */
@RestController
@RequestMapping("/environment")
@RequiredArgsConstructor
@Slf4j
public class TimeSeriesStatsController {

    private final TimeSeriesStatsService statsService;

    /**
     * 서비스 개수 조회 (gatewayId 기준)
     */
    @GetMapping("/{companyDomain}/services/count")
    public ResponseEntity<Map<String, Object>> getServiceCount(@PathVariable String companyDomain) {
        log.info("서비스 개수 조회 요청 - companyDomain: {}", companyDomain);

        try {
            int count = statsService.countUniqueServices(companyDomain);

            Map<String, Object> response = new HashMap<>();
            response.put("count", count);
            response.put("companyDomain", companyDomain);
            response.put("type", "services");
            response.put("success", true);

            log.info("서비스 개수 조회 완료 - count: {}", count);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("서비스 개수 조회 실패", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("count", 0);
            errorResponse.put("companyDomain", companyDomain);
            errorResponse.put("type", "services");
            errorResponse.put("success", false);
            errorResponse.put("error", true);
            errorResponse.put("message", "서비스 개수 조회 실패");

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * 서버 개수 조회 (IP 형태 deviceId 기준)
     */
    @GetMapping("/{companyDomain}/servers/count")
    public ResponseEntity<Map<String, Object>> getServerCount(@PathVariable String companyDomain) {
        log.info("서버 개수 조회 요청 - companyDomain: {}", companyDomain);

        try {
            int count = statsService.countUniqueServers(companyDomain);

            Map<String, Object> response = new HashMap<>();
            response.put("count", count);
            response.put("companyDomain", companyDomain);
            response.put("type", "servers");
            response.put("success", true);

            log.info("서버 개수 조회 완료 - count: {}", count);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("서버 개수 조회 실패", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("count", 0);
            errorResponse.put("companyDomain", companyDomain);
            errorResponse.put("type", "servers");
            errorResponse.put("success", false);
            errorResponse.put("error", true);
            errorResponse.put("message", "서버 개수 조회 실패");

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * 센서 개수 조회 (센서 ID 형태 deviceId 기준)
     */
    @GetMapping("/{companyDomain}/sensors/count")
    public ResponseEntity<Map<String, Object>> getSensorCount(@PathVariable String companyDomain) {
        log.info("센서 개수 조회 요청 - companyDomain: {}", companyDomain);

        try {
            int count = statsService.countUniqueSensors(companyDomain);

            Map<String, Object> response = new HashMap<>();
            response.put("count", count);
            response.put("companyDomain", companyDomain);
            response.put("type", "sensors");
            response.put("success", true);

            log.info("센서 개수 조회 완료 - count: {}", count);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("센서 개수 조회 실패", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("count", 0);
            errorResponse.put("companyDomain", companyDomain);
            errorResponse.put("type", "sensors");
            errorResponse.put("success", false);
            errorResponse.put("error", true);
            errorResponse.put("message", "센서 개수 조회 실패");

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * 아웃바운드 트래픽 조회
     */
    @GetMapping("/{companyDomain}/traffic/outbound")
    public ResponseEntity<Map<String, Object>> getOutboundTraffic(@PathVariable String companyDomain) {
        log.info("아웃바운드 트래픽 조회 요청 - companyDomain: {}", companyDomain);

        try {
            Map<String, Object> trafficData = statsService.calculateOutboundTraffic(companyDomain);

            Map<String, Object> response = new HashMap<>();
            response.put("traffic", trafficData);
            response.put("companyDomain", companyDomain);
            response.put("type", "outbound_traffic");
            response.put("success", true);

            log.info("아웃바운드 트래픽 조회 완료 - {}", trafficData.get("formattedValue"));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("아웃바운드 트래픽 조회 실패", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("traffic", Map.of("formattedValue", "0.0 MB"));
            errorResponse.put("companyDomain", companyDomain);
            errorResponse.put("type", "outbound_traffic");
            errorResponse.put("success", false);
            errorResponse.put("error", true);
            errorResponse.put("message", "트래픽 조회 실패");

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * 통합 통계 정보 조회 (권장)
     * 서비스/서버/센서 개수를 한 번에 조회하여 네트워크 효율성 향상
     */
    @GetMapping("/{companyDomain}/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats(@PathVariable String companyDomain) {
        log.info("통합 통계 조회 요청 - companyDomain: {}", companyDomain);

        Map<String, Object> stats = statsService.getDashboardStats(companyDomain);

        if (Boolean.TRUE.equals(stats.get("success"))) {
            log.info("통합 통계 조회 성공 - 서비스: {}, 서버: {}, 센서: {}, 총 디바이스: {}",
                    stats.get("serviceCount"), stats.get("serverCount"),
                    stats.get("sensorCount"), stats.get("totalDevices"));
        } else {
            log.warn("통합 통계 조회 실패 - companyDomain: {}", companyDomain);
        }

        return ResponseEntity.ok(stats);
    }
}
