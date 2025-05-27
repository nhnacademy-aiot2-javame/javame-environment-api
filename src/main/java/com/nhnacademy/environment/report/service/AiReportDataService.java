package com.nhnacademy.environment.report.service;

import com.nhnacademy.environment.timeseries.dto.ChartDataDto;
import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiReportDataService {

    private final TimeSeriesDataService timeSeriesDataService;

    private static final DateTimeFormatter DEFAULT_CHART_X_AXIS_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    // ★★★ Map.of() 대신 HashMap 사용 ★★★
    private static final Map<String, String> KEYWORD_TO_GATEWAY;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("cpu", "cpu");
        map.put("메모리", "mem");
        map.put("memory", "mem");
        map.put("온도", "sensors");
        map.put("temperature", "sensors");
        map.put("전력", "modbus");
        map.put("power", "modbus");
        map.put("디스크", "disk");
        map.put("disk", "disk");
        map.put("네트워크", "net");
        map.put("network", "net");
        // 서비스별 JVM 키워드
        map.put("인증", "javame-auth");
        map.put("auth", "javame-auth");
        map.put("환경", "javame-environment-api");
        map.put("environment", "javame-environment-api");
        map.put("프론트", "javame-frontend");
        map.put("frontend", "javame-frontend");
        map.put("게이트웨이", "javame-gateway");
        map.put("gateway", "javame-gateway");
        map.put("회원", "javame-member");
        map.put("member", "javame-member");
        KEYWORD_TO_GATEWAY = Collections.unmodifiableMap(map);
    }

    // ★★★ gatewayId -> measurement 목록도 HashMap 사용 ★★★
    private static final Map<String, List<String>> GATEWAY_TO_MEASUREMENTS;
    static {
        Map<String, List<String>> map = new HashMap<>();
        map.put("cpu", Arrays.asList("usage_idle", "usage_iowait", "usage_system", "usage_user"));
        map.put("mem", Arrays.asList("available_percent", "used_percent"));
        map.put("sensors", Arrays.asList("temp_input"));
        map.put("modbus", Arrays.asList("current_amps", "power_watts", "temperature_celsius", "power_factor_avg_percent"));
        map.put("disk", Arrays.asList("used_percent"));
        map.put("diskio", Arrays.asList("io_time", "read_bytes", "write_bytes"));
        map.put("net", Arrays.asList("bytes_recv", "bytes_sent"));
        map.put("swap", Arrays.asList("used_percent"));
        map.put("system", Arrays.asList("load1"));
        // JVM 서비스별 메트릭
        map.put("javame-auth", Arrays.asList("cpu_utilization_percent", "gc_g1_young_generation_count",
                "memory_old_gen_used_bytes", "memory_total_heap_used_bytes",
                "process_open_file_descriptors_count", "thread_active_count"));
        map.put("javame-environment-api", Arrays.asList("cpu_utilization_percent", "gc_g1_young_generation_count",
                "memory_old_gen_used_bytes", "memory_total_heap_used_bytes",
                "process_open_file_descriptors_count", "thread_active_count"));
        map.put("javame-frontend", Arrays.asList("cpu_utilization_percent", "disk_io_bytes_direction_read", "disk_io_bytes_direction_write",
                "gc_g1_young_generation_count", "memory_old_gen_used_bytes", "memory_total_heap_used_bytes",
                "process_open_file_descriptors_count", "thread_active_count"));
        map.put("javame-gateway", Arrays.asList("cpu_utilization_percent", "gc_g1_young_generation_count",
                "memory_old_gen_used_bytes", "memory_total_heap_used_bytes",
                "process_open_file_descriptors_count", "thread_active_count"));
        map.put("javame-member", Arrays.asList("cpu_utilization_percent", "gc_g1_young_generation_count",
                "memory_old_gen_used_bytes", "memory_total_heap_used_bytes",
                "process_open_file_descriptors_count", "thread_active_count"));
        GATEWAY_TO_MEASUREMENTS = Collections.unmodifiableMap(map);
    }

    // ★★★ measurement 키워드 매핑도 HashMap 사용 ★★★
    private static final Map<String, String> MEASUREMENT_KEYWORDS;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("사용률", "usage_idle");
        map.put("usage", "usage_idle");
        map.put("idle", "usage_idle");
        map.put("온도", "temp_input");
        map.put("temperature", "temp_input");
        map.put("전력", "power_watts");
        map.put("power", "power_watts");
        map.put("메모리", "available_percent");
        map.put("memory", "available_percent");
        // JVM 키워드 매핑
        map.put("힙메모리", "memory_total_heap_used_bytes");
        map.put("heap", "memory_total_heap_used_bytes");
        map.put("올드젠", "memory_old_gen_used_bytes");
        map.put("oldgen", "memory_old_gen_used_bytes");
        map.put("가비지컬렉터", "gc_g1_young_generation_count");
        map.put("gc", "gc_g1_young_generation_count");
        map.put("스레드", "thread_active_count");
        map.put("thread", "thread_active_count");
        map.put("파일핸들러", "process_open_file_descriptors_count");
        map.put("file", "process_open_file_descriptors_count");
        map.put("디스크", "used_percent");
        map.put("네트워크", "bytes_recv");
        MEASUREMENT_KEYWORDS = Collections.unmodifiableMap(map);
    }

    // ★★★ gatewayId -> location 매핑도 HashMap 사용 ★★★
    private static final Map<String, String> GATEWAY_TO_LOCATION;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("cpu", "server_resource_data");
        map.put("mem", "server_resource_data");
        map.put("disk", "server_resource_data");
        map.put("diskio", "server_resource_data");
        map.put("net", "server_resource_data");
        map.put("sensors", "server_resource_data");
        map.put("swap", "server_resource_data");
        map.put("system", "server_resource_data");
        map.put("modbus", "power_meter");
        // JVM 서비스들은 service_resource_data location
        map.put("javame-auth", "service_resource_data");
        map.put("javame-environment-api", "service_resource_data");
        map.put("javame-frontend", "service_resource_data");
        map.put("javame-gateway", "service_resource_data");
        map.put("javame-member", "service_resource_data");
        GATEWAY_TO_LOCATION = Collections.unmodifiableMap(map);
    }

    // 나머지 메소드들은 동일하게 유지...
    public Map<String, Object> prepareSimpleReport(String userPrompt) {
        log.info("단순 리포트 데이터 준비 - 사용자 프롬프트: '{}'", userPrompt);

        try {
            // 1. 프롬프트에서 gatewayId 추출
            String gatewayId = extractGatewayFromPrompt(userPrompt);
            if (gatewayId == null) {
                return createErrorResult("요청하신 내용에서 시스템 유형을 찾을 수 없습니다. (CPU, 메모리, 온도, 전력, 인증서비스, 환경API, 프론트엔드, 게이트웨이, 회원서비스)");
            }

            // 2. gatewayId에 해당하는 location 결정
            String location = GATEWAY_TO_LOCATION.get(gatewayId);
            if (location == null) {
                return createErrorResult("시스템 유형 '" + gatewayId + "'에 대한 위치 정보를 찾을 수 없습니다.");
            }

            // 3. 프롬프트에서 measurement 추출
            String measurement = extractMeasurementFromPrompt(userPrompt, gatewayId);
            if (measurement == null) {
                // 기본값: 해당 gateway의 첫 번째 measurement
                measurement = GATEWAY_TO_MEASUREMENTS.get(gatewayId).get(0);
                log.info("measurement를 특정할 수 없어 기본값 사용: {}", measurement);
            }

            // 4. 기본 조회 기간: 최근 24시간
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(24);

            // 5. TimeSeriesDataService 호출 (location 필터 추가)
            Map<String, String> filters = new HashMap<>();
            filters.put("gatewayId", gatewayId);
            filters.put("location", location);

            List<TimeSeriesDataDto> rawData = timeSeriesDataService.getRawTimeSeriesData(
                    "server_data", // origin은 모두 server_data로 통일
                    measurement,
                    filters,
                    startTime,
                    endTime
            );

            // 6. 차트 데이터도 조회
            String aggregationInterval = isServiceMetric(gatewayId) ? "30m" : "1h";

            ChartDataDto chartData = timeSeriesDataService.getAggregatedChartDataForPeriod(
                    measurement,
                    "value",
                    filters,
                    startTime,
                    endTime,
                    aggregationInterval,
                    DEFAULT_CHART_X_AXIS_FORMATTER
            );

            // 7. 결과 구성
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("gatewayId", gatewayId);
            result.put("location", location);
            result.put("measurement", measurement);
            result.put("startTime", startTime);
            result.put("endTime", endTime);
            result.put("totalDataCount", rawData.size());
            result.put("rawDataSample", rawData.stream().limit(5).collect(Collectors.toList()));
            result.put("chartData", chartData);

            // AI에게 전달할 요약 정보
            if (!rawData.isEmpty()) {
                double avgValue = rawData.stream()
                        .mapToDouble(TimeSeriesDataDto::getValue)
                        .average()
                        .orElse(0.0);
                double maxValue = rawData.stream()
                        .mapToDouble(TimeSeriesDataDto::getValue)
                        .max()
                        .orElse(0.0);
                double minValue = rawData.stream()
                        .mapToDouble(TimeSeriesDataDto::getValue)
                        .min()
                        .orElse(0.0);

                String unit = getUnitForMeasurement(measurement);
                String description = getDescriptionForMeasurement(measurement);
                String serviceInfo = isServiceMetric(gatewayId) ? " (" + getServiceName(gatewayId) + ")" : "";

                result.put("summary", String.format(
                        "최근 24시간 %s%s 데이터 %d건 조회됨. %s\n평균: %.2f%s, 최대: %.2f%s, 최소: %.2f%s",
                        measurement, serviceInfo, rawData.size(), description,
                        avgValue, unit, maxValue, unit, minValue, unit
                ));
            } else {
                result.put("summary", "조회된 데이터가 없습니다.");
            }

            log.info("단순 리포트 데이터 준비 완료 - gatewayId: {}, location: {}, measurement: {}, 데이터 건수: {}",
                    gatewayId, location, measurement, rawData.size());
            return result;

        } catch (Exception e) {
            log.error("단순 리포트 데이터 준비 중 오류 발생", e);
            return createErrorResult("데이터 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    // 나머지 헬퍼 메소드들은 이전과 동일...
    private String extractGatewayFromPrompt(String prompt) {
        String lowerPrompt = prompt.toLowerCase();

        for (Map.Entry<String, String> entry : KEYWORD_TO_GATEWAY.entrySet()) {
            if (lowerPrompt.contains(entry.getKey())) {
                log.debug("프롬프트에서 키워드 '{}' 발견 -> gatewayId: {}", entry.getKey(), entry.getValue());
                return entry.getValue();
            }
        }

        log.warn("프롬프트에서 gatewayId를 추출할 수 없음: {}", prompt);
        return null;
    }

    private String extractMeasurementFromPrompt(String prompt, String gatewayId) {
        String lowerPrompt = prompt.toLowerCase();

        // 1. 키워드 기반 매핑 시도
        for (Map.Entry<String, String> entry : MEASUREMENT_KEYWORDS.entrySet()) {
            if (lowerPrompt.contains(entry.getKey())) {
                String candidateMeasurement = entry.getValue();
                if (GATEWAY_TO_MEASUREMENTS.get(gatewayId).contains(candidateMeasurement)) {
                    log.debug("프롬프트에서 measurement 키워드 '{}' 발견 -> measurement: {}",
                            entry.getKey(), candidateMeasurement);
                    return candidateMeasurement;
                }
            }
        }

        // 2. gatewayId별 특별 규칙
        if ("cpu".equals(gatewayId)) {
            if (lowerPrompt.contains("idle") || lowerPrompt.contains("유휴")) {
                return "usage_idle";
            } else if (lowerPrompt.contains("system") || lowerPrompt.contains("시스템")) {
                return "usage_system";
            } else if (lowerPrompt.contains("user") || lowerPrompt.contains("사용자")) {
                return "usage_user";
            }
            return "usage_idle";
        } else if ("mem".equals(gatewayId)) {
            if (lowerPrompt.contains("available") || lowerPrompt.contains("사용가능")) {
                return "available_percent";
            } else if (lowerPrompt.contains("used") || lowerPrompt.contains("사용중")) {
                return "used_percent";
            }
            return "available_percent";
        } else if (isServiceMetric(gatewayId)) {
            if (lowerPrompt.contains("heap") || lowerPrompt.contains("힙")) {
                return "memory_total_heap_used_bytes";
            } else if (lowerPrompt.contains("oldgen") || lowerPrompt.contains("올드젠")) {
                return "memory_old_gen_used_bytes";
            } else if (lowerPrompt.contains("gc") || lowerPrompt.contains("가비지")) {
                return "gc_g1_young_generation_count";
            } else if (lowerPrompt.contains("thread") || lowerPrompt.contains("스레드")) {
                return "thread_active_count";
            } else if (lowerPrompt.contains("file") || lowerPrompt.contains("파일")) {
                return "process_open_file_descriptors_count";
            } else if (lowerPrompt.contains("cpu") || lowerPrompt.contains("씨피유")) {
                return "cpu_utilization_percent";
            }
            return "cpu_utilization_percent";
        }

        return null;
    }

    private boolean isServiceMetric(String gatewayId) {
        return gatewayId.startsWith("javame-");
    }

    private String getServiceName(String gatewayId) {
        switch (gatewayId) {
            case "javame-auth": return "인증 서비스";
            case "javame-environment-api": return "환경 API";
            case "javame-frontend": return "프론트엔드";
            case "javame-gateway": return "게이트웨이";
            case "javame-member": return "회원 서비스";
            default: return gatewayId;
        }
    }

    private String getUnitForMeasurement(String measurement) {
        if (measurement.contains("bytes")) {
            return " bytes";
        } else if (measurement.contains("percent")) {
            return "%";
        } else if (measurement.contains("count")) {
            return " 개";
        } else if (measurement.contains("usage_")) {
            return "%";
        } else if (measurement.contains("temp")) {
            return "°C";
        } else if (measurement.contains("power")) {
            return " watts";
        } else if (measurement.contains("current")) {
            return " amps";
        } else if (measurement.contains("utilization")) {
            return "%";
        }
        return "";
    }

    private String getDescriptionForMeasurement(String measurement) {
        switch (measurement) {
            case "cpu_utilization_percent": return "서비스 CPU 사용률";
            case "memory_total_heap_used_bytes": return "JVM 힙 메모리 사용량";
            case "memory_old_gen_used_bytes": return "JVM 올드젠 메모리 사용량";
            case "gc_g1_young_generation_count": return "G1 Young Generation GC 실행 횟수";
            case "thread_active_count": return "활성 스레드 수";
            case "process_open_file_descriptors_count": return "열린 파일 디스크립터 수";
            case "usage_idle": return "CPU 유휴 사용률";
            case "temp_input": return "온도 센서 값";
            case "power_watts": return "전력 소비량";
            case "available_percent": return "메모리 가용률";
            case "used_percent": return "메모리 사용률";
            default: return "시스템 메트릭";
        }
    }

    private Map<String, Object> createErrorResult(String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", errorMessage);
        result.put("summary", errorMessage);
        return result;
    }
}
