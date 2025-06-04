package com.nhnacademy.environment.timeseries.service;

import com.influxdb.client.QueryApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * 시계열 데이터 통계 서비스
 * 서비스/센서/서버 개수 등 대시보드 통계 정보 제공
 */
@Service
@Slf4j
public class TimeSeriesStatsService {

    private final QueryApi queryApi;
    private final String bucket;
    private final String influxOrg;

    // ★★★ IP 주소 패턴 정규식 ★★★
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    // 수동으로 생성자 작성
    public TimeSeriesStatsService(QueryApi queryApi,
                                  @Qualifier("influxBucket") String bucket,
                                  @Qualifier("influxOrganization") String influxOrg) {
        this.queryApi = queryApi;
        this.bucket = bucket;
        this.influxOrg = influxOrg;
    }

    /**
     * 회사 도메인에서 .com 제거하는 헬퍼 메소드
     */
    private String processCompanyDomain(String companyDomain) {
        if (companyDomain != null && companyDomain.endsWith(".com")) {
            return companyDomain.substring(0, companyDomain.length() - 4);
        }
        return companyDomain;
    }

    /**
     * deviceId가 IP 주소인지 판별
     */
    private boolean isIpAddress(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return false;
        }

        try {
            // 정규식으로 1차 검증
            if (IP_PATTERN.matcher(deviceId.trim()).matches()) {
                return true;
            }

            // InetAddress로 2차 검증
            InetAddress.getByName(deviceId.trim());
            return deviceId.contains("."); // IPv4만 체크

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 서버 개수 조회 (server_resource_data location의 deviceId)
     */
    public int countUniqueServers(String companyDomain) {
        String processedDomain = processCompanyDomain(companyDomain);

        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -30d) " +
                        "|> filter(fn: (r) => r[\"companyDomain\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"origin\"] == \"server_data\") " +
                        "|> filter(fn: (r) => r[\"location\"] == \"server_resource_data\") " + // ★★★ 서버 location ★★★
                        "|> filter(fn: (r) => r[\"deviceId\"] != \"\") " +
                        "|> keep(columns: [\"deviceId\"]) " +
                        "|> group() " +
                        "|> distinct(column: \"deviceId\") " +
                        "|> count()",
                bucket, processedDomain
        );

        log.debug("서버 개수 Flux 쿼리 (원본: {}, 처리됨: {}): {}", companyDomain, processedDomain, flux);

        try {
            List<Long> results = queryApi.query(flux, influxOrg).stream()
                    .flatMap(table -> table.getRecords().stream())
                    .map(record -> {
                        Object value = record.getValue();
                        if (value instanceof Number) {
                            return ((Number) value).longValue();
                        }
                        return 0L;
                    })
                    .filter(Objects::nonNull)
                    .toList();

            int count = results.isEmpty() ? 0 : results.get(0).intValue();
            log.info("고유 서버 개수: {} (원본: {}, 처리됨: {})", count, companyDomain, processedDomain);

            return count;

        } catch (Exception e) {
            log.error("서버 개수 조회 실패 - companyDomain: {} (처리됨: {})", companyDomain, processedDomain, e);
            return 0;
        }
    }

    /**
     * 센서 개수 조회 (server_resource_data가 아닌 모든 location의 deviceId)
     */
    public int countUniqueSensors(String companyDomain) {
        String processedDomain = processCompanyDomain(companyDomain);

        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -30d) " +
                        "|> filter(fn: (r) => r[\"companyDomain\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"origin\"] == \"server_data\") " +
                        "|> filter(fn: (r) => r[\"location\"] != \"server_resource_data\") " + // ★★★ 서버가 아닌 location ★★★
                        "|> filter(fn: (r) => r[\"location\"] != \"service_resource_data\") " + // ★★★ 서비스도 아닌 location ★★★
                        "|> filter(fn: (r) => r[\"deviceId\"] != \"\") " +
                        "|> keep(columns: [\"deviceId\"]) " +
                        "|> group() " +
                        "|> distinct(column: \"deviceId\") " +
                        "|> count()",
                bucket, processedDomain
        );

        log.debug("센서 개수 Flux 쿼리 (원본: {}, 처리됨: {}): {}", companyDomain, processedDomain, flux);

        try {
            List<Long> results = queryApi.query(flux, influxOrg).stream()
                    .flatMap(table -> table.getRecords().stream())
                    .map(record -> {
                        Object value = record.getValue();
                        if (value instanceof Number) {
                            return ((Number) value).longValue();
                        }
                        return 0L;
                    })
                    .filter(Objects::nonNull)
                    .toList();

            int count = results.isEmpty() ? 0 : results.get(0).intValue();
            log.info("고유 센서 개수: {} (원본: {}, 처리됨: {})", count, companyDomain, processedDomain);

            return count;

        } catch (Exception e) {
            log.error("센서 개수 조회 실패 - companyDomain: {} (처리됨: {})", companyDomain, processedDomain, e);
            return 0;
        }
    }

    /**
     * 아웃바운드 트래픽 계산 (검색 결과 [4] Proxmox 패턴 적용)
     */
    public Map<String, Object> calculateOutboundTraffic(String companyDomain) {
        String processedDomain = processCompanyDomain(companyDomain);

        // ★★★ 검색 결과 [4] LAST - FIRST 패턴 적용 ★★★
        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -24h) " +
                        "|> filter(fn: (r) => r[\"companyDomain\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"origin\"] == \"server_data\") " +
                        "|> filter(fn: (r) => r[\"location\"] == \"server_resource_data\") " +
                        "|> filter(fn: (r) => r[\"gatewayId\"] == \"net\") " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"bytes_sent\") " + // 아웃바운드
                        "|> filter(fn: (r) => r[\"_field\"] == \"value\") " +
                        "|> group(columns: [\"deviceId\"]) " +
                        "|> aggregateWindow(every: 1h, fn: last, createEmpty: false) " +
                        "|> difference() " + // 검색 결과 [3] 누적값 차이 계산
                        "|> sum()", // 24시간 총합
                bucket, processedDomain
        );

        log.debug("아웃바운드 트래픽 Flux 쿼리: {}", flux);

        try {
            List<Double> results = queryApi.query(flux, influxOrg).stream()
                    .flatMap(table -> table.getRecords().stream())
                    .map(record -> {
                        Object value = record.getValue();
                        if (value instanceof Number) {
                            return ((Number) value).doubleValue();
                        }
                        return 0.0;
                    })
                    .filter(Objects::nonNull)
                    .toList();

            double totalBytes = results.stream().mapToDouble(Double::doubleValue).sum();

            // ★★★ 바이트를 MB로 변환 ★★★
            double totalMB = totalBytes / (1024.0 * 1024.0);

            Map<String, Object> trafficData = new HashMap<>();
            trafficData.put("totalBytes", (long) totalBytes);
            trafficData.put("totalMB", Math.round(totalMB * 100.0) / 100.0);
            trafficData.put("formattedValue", String.format("%.1f MB", totalMB));
            trafficData.put("period", "24h");
            trafficData.put("measurement", "bytes_sent");
            trafficData.put("success", true);

            log.info("아웃바운드 트래픽 계산 완료 - {} bytes ({} MB)", (long) totalBytes, totalMB);

            return trafficData;

        } catch (Exception e) {
            log.error("아웃바운드 트래픽 계산 실패", e);

            return Map.of(
                    "totalBytes", 0L,
                    "totalMB", 0.0,
                    "formattedValue", "0.0 MB",
                    "period", "24h",
                    "measurement", "bytes_sent",
                    "success", false,
                    "error", true
            );
        }
    }

    /**
     * 고유 서비스 개수 조회 (gatewayId 기준)
     */
    public int countUniqueServices(String companyDomain) {
        String processedDomain = processCompanyDomain(companyDomain);

        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -30d) " +
                        "|> filter(fn: (r) => r[\"companyDomain\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"origin\"] == \"server_data\") " +
                        "|> filter(fn: (r) => r[\"location\"] == \"service_resource_data\") " +
                        "|> keep(columns: [\"gatewayId\"]) " +
                        "|> group() " +
                        "|> distinct(column: \"gatewayId\") " +
                        "|> count()",
                bucket, processedDomain
        );

        log.debug("서비스 개수 Flux 쿼리 (원본: {}, 처리됨: {}): {}", companyDomain, processedDomain, flux);

        try {
            List<Long> results = queryApi.query(flux, influxOrg).stream()
                    .flatMap(table -> table.getRecords().stream())
                    .map(record -> {
                        Object value = record.getValue();
                        if (value instanceof Number) {
                            return ((Number) value).longValue();
                        }
                        return 0L;
                    })
                    .filter(Objects::nonNull)
                    .toList();

            int count = results.isEmpty() ? 0 : results.get(0).intValue();
            log.info("고유 서비스 개수: {} (원본: {}, 처리됨: {})", count, companyDomain, processedDomain);

            return count;

        } catch (Exception e) {
            log.error("서비스 개수 조회 실패 - companyDomain: {} (처리됨: {})", companyDomain, processedDomain, e);
            return 0;
        }
    }

    /**
     * 통합 대시보드 통계에 트래픽 정보 추가
     */
    public Map<String, Object> getDashboardStats(String companyDomain) {
        Map<String, Object> stats = new HashMap<>();

        try {
            log.info("통합 대시보드 통계 조회 시작 - companyDomain: {}", companyDomain);

            int serviceCount = countUniqueServices(companyDomain);
            int serverCount = countUniqueServers(companyDomain);
            int sensorCount = countUniqueSensors(companyDomain);

            // ★★★ 트래픽 정보 추가 ★★★
            Map<String, Object> outboundTraffic = calculateOutboundTraffic(companyDomain);

            stats.put("serviceCount", serviceCount);
            stats.put("serverCount", serverCount);
            stats.put("sensorCount", sensorCount);
            stats.put("totalDevices", serverCount + sensorCount);
            stats.put("outboundTraffic", outboundTraffic);
            stats.put("companyDomain", companyDomain);
            stats.put("success", true);

            log.info("통합 대시보드 통계 조회 완료 - 서비스: {}, 서버: {}, 센서: {}, 아웃바운드: {}",
                    serviceCount, serverCount, sensorCount,
                    outboundTraffic.get("formattedValue"));

        } catch (Exception e) {
            log.error("통합 대시보드 통계 조회 실패", e);

            stats.put("serviceCount", 0);
            stats.put("serverCount", 0);
            stats.put("sensorCount", 0);
            stats.put("totalDevices", 0);
            stats.put("outboundTraffic", Map.of("formattedValue", "0.0 MB", "success", false));
            stats.put("companyDomain", companyDomain);
            stats.put("success", false);
            stats.put("error", true);
            stats.put("message", e.getMessage());
        }

        return stats;
    }
}
