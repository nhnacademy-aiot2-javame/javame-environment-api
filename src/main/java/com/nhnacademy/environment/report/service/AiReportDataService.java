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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiReportDataService {

    private final TimeSeriesDataService timeSeriesDataService;

    private static final DateTimeFormatter DEFAULT_CHART_X_AXIS_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    // ★★★ 기간 추출을 위한 패턴 ★★★
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)\\s*(시간|일|주|개월|month|hour|day|week)");

    // 기존 키워드 매핑들...
    private static final Map<String, String> KEYWORD_TO_GATEWAY;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("cpu", "cpu");
        map.put("씨피유", "cpu");
        map.put("프로세서", "cpu");
        map.put("메모리", "mem");
        map.put("memory", "mem");
        map.put("램", "mem");
        map.put("온도", "sensors");
        map.put("temperature", "sensors");
        map.put("센서", "sensors");
        map.put("전력", "modbus");
        map.put("power", "modbus");
        map.put("파워", "modbus");
        map.put("디스크", "disk");
        map.put("disk", "disk");
        map.put("하드", "disk");
        map.put("네트워크", "net");
        map.put("network", "net");
        map.put("네트", "net");
        // 서비스별 JVM 키워드 확장
        map.put("인증", "javame-auth");
        map.put("auth", "javame-auth");
        map.put("authentication", "javame-auth");
        map.put("환경", "javame-environment-api");
        map.put("environment", "javame-environment-api");
        map.put("env", "javame-environment-api");
        map.put("프론트", "javame-frontend");
        map.put("frontend", "javame-frontend");
        map.put("front", "javame-frontend");
        map.put("ui", "javame-frontend");
        map.put("게이트웨이", "javame-gateway");
        map.put("gateway", "javame-gateway");
        map.put("gw", "javame-gateway");
        map.put("회원", "javame-member");
        map.put("member", "javame-member");
        map.put("user", "javame-member");
        KEYWORD_TO_GATEWAY = Collections.unmodifiableMap(map);
    }

    // 기존 매핑들은 그대로 유지... (GATEWAY_TO_MEASUREMENTS, MEASUREMENT_KEYWORDS, GATEWAY_TO_LOCATION)
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

    private static final Map<String, String> MEASUREMENT_KEYWORDS;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("사용률", "usage_idle");
        map.put("usage", "usage_idle");
        map.put("idle", "usage_idle");
        map.put("유휴", "usage_idle");
        map.put("온도", "temp_input");
        map.put("temperature", "temp_input");
        map.put("전력", "power_watts");
        map.put("power", "power_watts");
        map.put("메모리", "available_percent");
        map.put("memory", "available_percent");
        // JVM 키워드 매핑 확장
        map.put("힙메모리", "memory_total_heap_used_bytes");
        map.put("heap", "memory_total_heap_used_bytes");
        map.put("힙", "memory_total_heap_used_bytes");
        map.put("올드젠", "memory_old_gen_used_bytes");
        map.put("oldgen", "memory_old_gen_used_bytes");
        map.put("old", "memory_old_gen_used_bytes");
        map.put("가비지컬렉터", "gc_g1_young_generation_count");
        map.put("가비지", "gc_g1_young_generation_count");
        map.put("gc", "gc_g1_young_generation_count");
        map.put("스레드", "thread_active_count");
        map.put("thread", "thread_active_count");
        map.put("쓰레드", "thread_active_count");
        map.put("파일핸들러", "process_open_file_descriptors_count");
        map.put("파일", "process_open_file_descriptors_count");
        map.put("file", "process_open_file_descriptors_count");
        map.put("디스크", "used_percent");
        map.put("네트워크", "bytes_recv");
        MEASUREMENT_KEYWORDS = Collections.unmodifiableMap(map);
    }

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

    /**
     * ★★★ 메인 메소드: 일반 요청과 구체적 요청을 모두 처리 ★★★
     */
    public Map<String, Object> prepareSimpleReport(String userPrompt) {
        log.info("리포트 데이터 준비 시작 - 사용자 프롬프트: '{}'", userPrompt);

        try {
            // ★★★ JVM 메모리 관련 특별 처리 ★★★
            String lowerPrompt = userPrompt.toLowerCase();
            if ((lowerPrompt.contains("자바") || lowerPrompt.contains("서비스") || lowerPrompt.contains("jvm")) &&
                    (lowerPrompt.contains("메모리") || lowerPrompt.contains("memory")) &&
                    !lowerPrompt.contains("인증") && !lowerPrompt.contains("환경") &&
                    !lowerPrompt.contains("프론트") && !lowerPrompt.contains("게이트웨이") && !lowerPrompt.contains("회원")) {

                return handleJvmMemoryRequest(userPrompt);
            }

            // 기존 로직 계속...
            String gatewayId = extractGatewayFromPrompt(userPrompt);

            if (gatewayId == null) {
                return handleGeneralRequest(userPrompt);
            }

            return handleSpecificSystemRequest(userPrompt, gatewayId);

        } catch (Exception e) {
            log.error("리포트 데이터 준비 중 예외 발생", e);
            return createErrorResult("데이터 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }


    /**
     * ★★★ 새로운 메소드: 일반적인 요청 처리 (AI 가이드 제공) ★★★
     */
    private Map<String, Object> handleGeneralRequest(String userPrompt) {
        log.info("일반 요청으로 판단, AI 가이드 제공: {}", userPrompt);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("isGeneralRequest", true);
        result.put("userPrompt", userPrompt);

        // AI에게 전달할 시스템 정보 구성
        StringBuilder systemGuide = new StringBuilder();
        systemGuide.append("🤖 **AI 시스템 모니터링 가이드**\n\n");
        systemGuide.append("안녕하세요! 현재 모니터링 가능한 시스템을 안내해드리겠습니다.\n\n");

        systemGuide.append("## 📊 서버 리소스 모니터링\n");
        systemGuide.append("**사용 예시:**\n");
        systemGuide.append("- \"CPU 사용률 알려줘\" - 서버 CPU 상태 분석\n");
        systemGuide.append("- \"메모리 사용량 보여줘\" - 메모리 사용 현황\n");
        systemGuide.append("- \"온도 센서 값 확인해줘\" - 서버 온도 모니터링\n");
        systemGuide.append("- \"전력 소비량 분석해줘\" - 전력 사용 패턴\n\n");

        systemGuide.append("## ☕ JVM 서비스 모니터링\n");
        systemGuide.append("**사용 예시:**\n");
        systemGuide.append("- \"인증 서비스 힙 메모리 알려줘\" - 메모리 사용량 분석\n");
        systemGuide.append("- \"환경 API 가비지 컬렉션 횟수 보여줘\" - GC 성능 분석\n");
        systemGuide.append("- \"프론트엔드 스레드 수 확인해줘\" - 스레드 상태 확인\n");
        systemGuide.append("- \"게이트웨이 파일 핸들러 분석해줘\" - 파일 디스크립터 사용량\n");
        systemGuide.append("- \"회원 서비스 CPU 사용률 알려줘\" - 서비스별 CPU 분석\n\n");

        systemGuide.append("## 💡 분석 팁\n");
        systemGuide.append("- **기간 지정**: \"지난 3일간 CPU 사용률\" (1시간~7일 지원)\n");
        systemGuide.append("- **비교 분석**: \"인증 서비스와 회원 서비스 메모리 비교\"\n");
        systemGuide.append("- **트렌드 분석**: \"최근 일주일 온도 변화 추이\"\n\n");

        systemGuide.append("위 예시를 참고하여 구체적으로 요청해주시면 더 정확한 분석을 제공해드릴 수 있습니다! 😊");

        result.put("summary", systemGuide.toString());
        result.put("chartData", null);
        result.put("totalDataCount", 0);
        result.put("startTime", LocalDateTime.now().minusHours(24));
        result.put("endTime", LocalDateTime.now());

        return result;
    }

    /**
     * ★★★ 구체적인 시스템 요청 처리 (기존 로직 + 기간 추출 개선) ★★★
     */
    private Map<String, Object> handleSpecificSystemRequest(String userPrompt, String gatewayId) {
        // gatewayId에 해당하는 location 결정
        String location = GATEWAY_TO_LOCATION.get(gatewayId);
        if (location == null) {
            return createErrorResult("시스템 유형 '" + gatewayId + "'에 대한 위치 정보를 찾을 수 없습니다.");
        }

        // 프롬프트에서 measurement 추출
        String measurement = extractMeasurementFromPrompt(userPrompt, gatewayId);
        if (measurement == null) {
            measurement = GATEWAY_TO_MEASUREMENTS.get(gatewayId).get(0);
            log.info("measurement를 특정할 수 없어 기본값 사용: {}", measurement);
        }

        // ★★★ 프롬프트에서 기간 추출 (개선된 로직) ★★★
        LocalDateTime[] timeRange = extractTimeRangeFromPrompt(userPrompt);
        LocalDateTime startTime = timeRange[0];
        LocalDateTime endTime = timeRange[1];

        // TimeSeriesDataService 호출
        Map<String, String> filters = new HashMap<>();
        filters.put("gatewayId", gatewayId);
        filters.put("location", location);

        List<TimeSeriesDataDto> rawData = timeSeriesDataService.getRawTimeSeriesData(
                "server_data",
                measurement,
                filters,
                startTime,
                endTime
        );

        // 차트 데이터 조회 (기간에 따라 집계 간격 동적 조정)
        String aggregationInterval = determineAggregationInterval(startTime, endTime, isServiceMetric(gatewayId));

        ChartDataDto chartData = timeSeriesDataService.getAggregatedChartDataForPeriod(
                measurement,
                "value",
                filters,
                startTime,
                endTime,
                aggregationInterval,
                DEFAULT_CHART_X_AXIS_FORMATTER
        );

        // 결과 구성
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

        // AI에게 전달할 요약 정보 (개선된 통계)
        if (!rawData.isEmpty()) {
            result.put("summary", generateEnhancedSummary(rawData, measurement, gatewayId, startTime, endTime));
        } else {
            result.put("summary", String.format("요청하신 기간(%s ~ %s) 동안 %s 데이터가 조회되지 않았습니다.",
                    startTime.toLocalDate(), endTime.toLocalDate(), getDescriptionForMeasurement(measurement)));
        }

        log.info("구체적 시스템 요청 처리 완료 - gatewayId: {}, measurement: {}, 기간: {} ~ {}, 데이터 건수: {}",
                gatewayId, measurement, startTime.toLocalDate(), endTime.toLocalDate(), rawData.size());
        return result;
    }

    /**
     * ★★★ 새로운 메소드: 프롬프트에서 기간 추출 ★★★
     */
    private LocalDateTime[] extractTimeRangeFromPrompt(String prompt) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(24); // 기본값: 24시간

        String lowerPrompt = prompt.toLowerCase();

        // 상대적 기간 표현 처리
        if (lowerPrompt.contains("어제") || lowerPrompt.contains("yesterday")) {
            startTime = endTime.minusDays(1);
        } else if (lowerPrompt.contains("지난 주") || lowerPrompt.contains("last week")) {
            startTime = endTime.minusWeeks(1);
        } else if (lowerPrompt.contains("이번 주") || lowerPrompt.contains("this week")) {
            startTime = endTime.minusDays(endTime.getDayOfWeek().getValue() - 1);
        } else if (lowerPrompt.contains("지난 달") || lowerPrompt.contains("last month")) {
            startTime = endTime.minusMonths(1);
        } else {
            // 숫자 + 단위 패턴 추출 ("3일", "2시간", "1주일" 등)
            Matcher matcher = TIME_PATTERN.matcher(lowerPrompt);
            if (matcher.find()) {
                int number = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2);

                switch (unit) {
                    case "시간":
                    case "hour":
                        startTime = endTime.minusHours(number);
                        break;
                    case "일":
                    case "day":
                        startTime = endTime.minusDays(number);
                        break;
                    case "주":
                    case "week":
                        startTime = endTime.minusWeeks(number);
                        break;
                    case "개월":
                    case "month":
                        startTime = endTime.minusMonths(number);
                        break;
                }
                log.debug("프롬프트에서 기간 추출: {} {} -> {} ~ {}", number, unit, startTime.toLocalDate(), endTime.toLocalDate());
            }
        }

        return new LocalDateTime[]{startTime, endTime};
    }

    /**
     * ★★★ 새로운 메소드: 기간에 따른 집계 간격 동적 결정 ★★★
     */
    private String determineAggregationInterval(LocalDateTime startTime, LocalDateTime endTime, boolean isServiceMetric) {
        long hours = java.time.Duration.between(startTime, endTime).toHours();

        if (hours <= 6) {
            return isServiceMetric ? "10m" : "15m";
        } else if (hours <= 24) {
            return isServiceMetric ? "30m" : "1h";
        } else if (hours <= 168) { // 1주일
            return "6h";
        } else {
            return "1d";
        }
    }

    /**
     * ★★★ 새로운 메소드: 향상된 요약 정보 생성 ★★★
     */
    private String generateEnhancedSummary(List<TimeSeriesDataDto> rawData, String measurement,
                                           String gatewayId, LocalDateTime startTime, LocalDateTime endTime) {
        double avgValue = rawData.stream().mapToDouble(TimeSeriesDataDto::getValue).average().orElse(0.0);
        double maxValue = rawData.stream().mapToDouble(TimeSeriesDataDto::getValue).max().orElse(0.0);
        double minValue = rawData.stream().mapToDouble(TimeSeriesDataDto::getValue).min().orElse(0.0);

        // 표준편차 계산
        double variance = rawData.stream()
                .mapToDouble(dto -> Math.pow(dto.getValue() - avgValue, 2))
                .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        String unit = getUnitForMeasurement(measurement);
        String description = getDescriptionForMeasurement(measurement);
        String serviceInfo = isServiceMetric(gatewayId) ? " (" + getServiceName(gatewayId) + ")" : "";
        long hours = java.time.Duration.between(startTime, endTime).toHours();

        return String.format(
                "📊 **%s%s 분석 결과** (최근 %d시간)\n\n" +
                        "**기본 통계:**\n" +
                        "- 데이터 건수: %,d건\n" +
                        "- 평균값: %.2f%s\n" +
                        "- 최대값: %.2f%s\n" +
                        "- 최소값: %.2f%s\n" +
                        "- 표준편차: %.2f%s\n" +
                        "- 변동성: %s\n\n" +
                        "**분석 기간:** %s ~ %s\n" +
                        "**측정 항목:** %s",
                description, serviceInfo, hours,
                rawData.size(),
                avgValue, unit,
                maxValue, unit,
                minValue, unit,
                stdDev, unit,
                getVariabilityDescription(stdDev, avgValue),
                startTime.toLocalDate(), endTime.toLocalDate(),
                measurement
        );
    }

    /**
     * ★★★ 새로운 메소드: 변동성 설명 ★★★
     */
    private String getVariabilityDescription(double stdDev, double avgValue) {
        if (avgValue == 0) return "안정적";
        double cv = (stdDev / avgValue) * 100; // 변동계수
        if (cv < 10) return "매우 안정적";
        else if (cv < 20) return "안정적";
        else if (cv < 30) return "보통";
        else return "변동이 큼";
    }

    /**
     * ★★★ 개선된 키워드 추출 로직 (우선순위 및 컨텍스트 고려) ★★★
     */
    private String extractGatewayFromPrompt(String prompt) {
        String lowerPrompt = prompt.toLowerCase();

        // ★★★ 1단계: JVM 서비스 관련 키워드 우선 검사 (가장 구체적) ★★★
        if (lowerPrompt.contains("자바") || lowerPrompt.contains("java") || lowerPrompt.contains("jvm") ||
                // ★★★ 서비스명이 명시된 경우도 JVM 관련으로 우선 처리 ★★★
                lowerPrompt.contains("인증") || lowerPrompt.contains("환경") || lowerPrompt.contains("프론트") ||
                lowerPrompt.contains("게이트웨이") || lowerPrompt.contains("회원") || lowerPrompt.contains("서비스")) {

            // 구체적인 서비스가 명시된 경우
            if (lowerPrompt.contains("인증") || lowerPrompt.contains("auth")) {
                return "javame-auth";
            } else if (lowerPrompt.contains("환경") || lowerPrompt.contains("environment") || lowerPrompt.contains("env")) {
                return "javame-environment-api";
            } else if (lowerPrompt.contains("프론트") || lowerPrompt.contains("frontend") || lowerPrompt.contains("front") || lowerPrompt.contains("ui")) {
                return "javame-frontend";
            } else if (lowerPrompt.contains("게이트웨이") || lowerPrompt.contains("gateway") || lowerPrompt.contains("gw")) {
                return "javame-gateway";
            } else if (lowerPrompt.contains("회원") || lowerPrompt.contains("member") || lowerPrompt.contains("user")) {
                return "javame-member";
            }

            // ★★★ JVM 관련이지만 특정 서비스가 명시되지 않은 경우만 일반 요청 처리 ★★★
            if ((lowerPrompt.contains("자바") || lowerPrompt.contains("java") || lowerPrompt.contains("jvm")) &&
                    (lowerPrompt.contains("힙") || lowerPrompt.contains("heap") ||
                            lowerPrompt.contains("올드젠") || lowerPrompt.contains("oldgen") ||
                            lowerPrompt.contains("가비지") || lowerPrompt.contains("gc") ||
                            lowerPrompt.contains("스레드") || lowerPrompt.contains("thread"))) {

                log.debug("JVM 메트릭 관련 키워드 발견했지만 특정 서비스 미지정: {}", prompt);
                return "javame-environment-api"; // 기본 서비스로 매핑하거나
                // return null; // 일반 요청으로 처리
            }
        }

        // ★★★ 2단계: 서버 리소스 키워드 검사 ★★★
        // CPU 관련 (높은 우선순위)
        if (lowerPrompt.contains("cpu") || lowerPrompt.contains("씨피유") || lowerPrompt.contains("프로세서")) {
            return "cpu";
        }

        // 온도 관련 (높은 우선순위 - 구체적)
        if (lowerPrompt.contains("온도") || lowerPrompt.contains("temperature") || lowerPrompt.contains("센서")) {
            return "sensors";
        }

        // 전력 관련 (높은 우선순위 - 구체적)
        if (lowerPrompt.contains("전력") || lowerPrompt.contains("power") || lowerPrompt.contains("파워")) {
            return "modbus";
        }

        // 디스크 관련
        if (lowerPrompt.contains("디스크") || lowerPrompt.contains("disk") || lowerPrompt.contains("하드")) {
            return "disk";
        }

        // 네트워크 관련
        if (lowerPrompt.contains("네트워크") || lowerPrompt.contains("network") || lowerPrompt.contains("네트")) {
            return "net";
        }

        // ★★★ 메모리 관련 (시스템 메모리만 처리) ★★★
        if (lowerPrompt.contains("메모리") || lowerPrompt.contains("memory") || lowerPrompt.contains("램")) {
            // ★★★ JVM 메모리 키워드가 함께 있어도 이미 위에서 처리되었으므로 여기는 시스템 메모리 ★★★
            return "mem";
        }

        // ★★★ 3단계: 확장된 패턴 매칭 ★★★
        if (lowerPrompt.contains("서버") || lowerPrompt.contains("시스템")) {
            if (lowerPrompt.contains("성능") || lowerPrompt.contains("리소스") || lowerPrompt.contains("자원")) {
                return "cpu"; // 기본적으로 CPU 분석
            }
        }

        // ★★★ 4단계: 일반적인 요청 패턴 ★★★
        if (lowerPrompt.contains("전체") || lowerPrompt.contains("종합") || lowerPrompt.contains("전반적") ||
                lowerPrompt.contains("모든") || lowerPrompt.contains("전부") || lowerPrompt.contains("리포트") ||
                lowerPrompt.contains("분석") || lowerPrompt.contains("상태")) {
            log.debug("일반적인 요청 패턴 감지: {}", prompt);
            return null; // 일반 요청으로 처리
        }

        log.debug("프롬프트에서 구체적인 gatewayId를 추출할 수 없음: {}", prompt);
        return null;
    }


    // 기존 헬퍼 메소드들은 그대로 유지...
    private String extractMeasurementFromPrompt(String prompt, String gatewayId) {
        // 기존 로직과 동일
        String lowerPrompt = prompt.toLowerCase();

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

        // gatewayId별 특별 규칙 (기존과 동일)
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

    // 기존 헬퍼 메소드들 (isServiceMetric, getServiceName, getUnitForMeasurement, getDescriptionForMeasurement, createErrorResult)
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

    /**
     * ★★★ 새로운 메소드: JVM 메모리 요청을 위한 서비스 추천 ★★★
     */
    private Map<String, Object> handleJvmMemoryRequest(String userPrompt) {
        log.info("JVM 메모리 관련 요청 감지, 서비스 선택 가이드 제공: {}", userPrompt);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("isGeneralRequest", true);
        result.put("userPrompt", userPrompt);

        StringBuilder guide = new StringBuilder();
        guide.append("🔍 **JVM 메모리 분석 가이드**\n\n");
        guide.append("JVM 메모리 분석을 원하시는군요! 어떤 서비스의 메모리를 분석하고 싶으신가요?\n\n");

        guide.append("## ☕ 사용 가능한 JVM 서비스\n");
        guide.append("**구체적인 요청 예시:**\n");
        guide.append("- \"**인증 서비스** 힙 메모리 알려줘\"\n");
        guide.append("- \"**환경 API** 올드젠 메모리 분석해줘\"\n");
        guide.append("- \"**프론트엔드** 가비지 컬렉션 상태 보여줘\"\n");
        guide.append("- \"**게이트웨이** 스레드 수 확인해줘\"\n");
        guide.append("- \"**회원 서비스** JVM 전체 상태 알려줘\"\n\n");

        guide.append("## 📊 분석 가능한 JVM 메트릭\n");
        guide.append("- **힙 메모리**: 전체 힙 사용량\n");
        guide.append("- **올드젠 메모리**: Old Generation 메모리 사용량\n");
        guide.append("- **가비지 컬렉션**: GC 실행 횟수 및 성능\n");
        guide.append("- **스레드**: 활성 스레드 수\n");
        guide.append("- **파일 핸들러**: 열린 파일 디스크립터 수\n");
        guide.append("- **CPU 사용률**: 서비스별 CPU 사용량\n\n");

        guide.append("위 예시를 참고하여 **구체적인 서비스명**과 함께 요청해주세요! 😊");

        result.put("summary", guide.toString());
        result.put("chartData", null);
        result.put("totalDataCount", 0);
        result.put("startTime", LocalDateTime.now().minusHours(24));
        result.put("endTime", LocalDateTime.now());

        return result;
    }
}
