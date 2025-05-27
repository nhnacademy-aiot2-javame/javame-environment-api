package com.nhnacademy.environment.report.service;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.nhnacademy.environment.timeseries.dto.ChartDataDto;
import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiReportDataService {

    private final TimeSeriesDataService timeSeriesDataService;

    private List<String> possibleBuildingValues = Collections.emptyList();
    private List<String> possibleOriginValues = Collections.emptyList();
    private List<String> possibleLocationValues = Collections.emptyList();
    private List<String> possiblePlaceValues = Collections.emptyList();
    private List<String> possibleGatewayIdValues = Collections.emptyList();
    private List<String> possibleMeasurementValues = Collections.emptyList();

    private static final String DEFAULT_TIMEZONE = "Asia/Seoul";
    private static final DateTimeFormatter DEFAULT_CHART_X_AXIS_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    @PostConstruct
    public void initializeTagValuesForFunctionDeclaration() {
        // (이전과 동일)
        log.info("AiReportDataService 초기화: InfluxDB 태그 값 목록 조회 시작...");
        try {
            this.possibleBuildingValues = safeGetDistinctTagValues("building");
            this.possibleOriginValues = safeGetDistinctTagValues("origin");
            this.possibleLocationValues = safeGetDistinctTagValues("location");
            this.possiblePlaceValues = safeGetDistinctTagValues("place");
            this.possibleGatewayIdValues = safeGetDistinctTagValues("gatewayId");
            this.possibleMeasurementValues = timeSeriesDataService.getDistinctMeasurementValues();

            log.info("InfluxDB 태그 값 목록 조회 완료. (샘플 크기) Buildings: {}, Origins: {}, Locations: {}, Places: {}, GatewayIds: {}, Measurements: {}",
                    this.possibleBuildingValues.size(), this.possibleOriginValues.size(),
                    this.possibleLocationValues.size(), this.possiblePlaceValues.size(),
                    this.possibleGatewayIdValues.size(), this.possibleMeasurementValues.size());
        } catch (Exception e) {
            log.error("AiReportDataService 초기화 중 InfluxDB 태그 값 조회 실패. FunctionDeclaration 설명이 제한될 수 있습니다.", e);
        }
    }

    private List<String> safeGetDistinctTagValues(String tagName) {
        // (이전과 동일)
        try {
            List<String> values = timeSeriesDataService.getDistinctTagValues(tagName);
            return values != null ? values : Collections.emptyList();
        } catch (Exception e) {
            log.warn(" 태그 '{}'의 고유 값 목록 조회 중 오류 발생. 빈 목록을 반환합니다.", tagName, e);
            return Collections.emptyList();
        }
    }
    // --- FunctionDeclaration 정의 메소드 (Schema.Builder().properties(Map) 사용) ---
    private FunctionDeclaration getInfluxDbTimeSeriesDataFunctionDeclaration() {
        // 각 파라미터의 Schema 정의
        Schema startDateSchema = Schema.builder().type(new Type(Type.Known.STRING))
                .description("조회 시작일 (YYYY-MM-DD 형식). '오늘', '어제', '이번 주 월요일' 등 상대적 표현은 실제 날짜로 변환해주세요. 예: '2025-05-20'")
                .build();
        Schema endDateSchema = Schema.builder().type(new Type(Type.Known.STRING))
                .description("조회 종료일 (YYYY-MM-DD 형식). startDateStr과 같거나 이후여야 합니다. 예: '2025-05-27'")
                .build();
        Schema measurementSchema = Schema.builder().type(new Type(Type.Known.STRING))
                .description("조회할 핵심 측정 항목의 정확한 값 (예: 'temperature_celsius', 'usage_idle'). 사용자가 '온도', 'CPU 사용률' 등을 언급하면 해당하는 시스템의 measurement 값으로 변환해주세요. 가능한 값 예시: " + formatPossibleValuesForDescription(this.possibleMeasurementValues, 7) + ". 이 외에도 사용자가 명시한 값을 사용할 수 있습니다.")
                .build();
        Schema buildingSchema = Schema.builder().type(new Type(Type.Known.STRING))
                .description("데이터가 속한 건물 이름 (선택 사항). 가능한 값 예시: " + formatPossibleValuesForDescription(this.possibleBuildingValues, 3))
                .build();
        Schema originSchema = Schema.builder().type(new Type(Type.Known.STRING))
                .description("데이터의 출처 또는 시스템 그룹 (선택 사항). 가능한 값 예시: " + formatPossibleValuesForDescription(this.possibleOriginValues, 5))
                .build();
        Schema locationSchema = Schema.builder().type(new Type(Type.Known.STRING))
                .description("데이터의 세부 위치 또는 센서 그룹 (선택 사항). 가능한 값 예시: " + formatPossibleValuesForDescription(this.possibleLocationValues, 5))
                .build();
        Schema deviceIdSchema = Schema.builder().type(new Type(Type.Known.STRING))
                .description("조회할 특정 장치의 고유 ID (선택 사항). (예: '192.168.71.74')")
                .build();
        Schema placeSchema = Schema.builder().type(new Type(Type.Known.STRING))
                .description("센서나 장치가 설치된 구체적인 장소 이름 (선택 사항). 가능한 값 예시: " + formatPossibleValuesForDescription(this.possiblePlaceValues, 3))
                .build();
        Schema gatewayIdSchema = Schema.builder().type(new Type(Type.Known.STRING))
                .description("데이터를 수집한 게이트웨이의 ID (선택 사항). 가능한 값 예시: " + formatPossibleValuesForDescription(this.possibleGatewayIdValues, 5))
                .build();
        Schema aggregationIntervalSchema = Schema.builder().type(new Type(Type.Known.STRING))
                .description("차트 데이터 집계 간격 (선택 사항, 예: '10m', '1h', '1d'). 사용자가 '시간별', '일별' 등을 언급하면 적절히 변환하거나, 없으면 기간에 따라 자동 결정됩니다.")
                .build();
        Schema includeRawDataSampleSchema = Schema.builder().type(new Type(Type.Known.BOOLEAN))
                .description("결과에 원시 데이터 샘플을 포함할지 여부 (선택 사항, 기본값 true).")
                .build();
        Schema includeChartDataSchema = Schema.builder().type(new Type(Type.Known.BOOLEAN))
                .description("결과에 차트용 집계 데이터를 포함할지 여부 (선택 사항, 기본값 true).")
                .build();

        // 파라미터 이름과 Schema 객체를 매핑하는 Map 생성 (순서 유지를 위해 LinkedHashMap 사용 가능)
        Map<String, Schema> propertiesMap = new LinkedHashMap<>(); // 또는 HashMap
        propertiesMap.put("startDateStr", startDateSchema);
        propertiesMap.put("endDateStr", endDateSchema);
        propertiesMap.put("measurement", measurementSchema);
        propertiesMap.put("building", buildingSchema);
        propertiesMap.put("origin", originSchema);
        propertiesMap.put("location", locationSchema);
        propertiesMap.put("deviceId", deviceIdSchema);
        propertiesMap.put("place", placeSchema);
        propertiesMap.put("gatewayId", gatewayIdSchema);
        propertiesMap.put("aggregationInterval", aggregationIntervalSchema);
        propertiesMap.put("includeRawDataSample", includeRawDataSampleSchema);
        propertiesMap.put("includeChartData", includeChartDataSchema);

        // 필수 파라미터 이름 목록 생성
        List<String> requiredParams = List.of("startDateStr", "endDateStr", "measurement");

        Schema parametersSchema = Schema.builder()
                .type(new Type(Type.Known.OBJECT))
                .properties(propertiesMap)
                .required(requiredParams) // ★★★ required(List<String>) 메소드 사용 ★★★
                .build();

        return FunctionDeclaration.builder()
                .name("getInfluxDbTimeSeriesData")
                .description("사용자의 자연어 요청에 따라 InfluxDB에서 시계열 데이터를 조회하여, 건물, 출처, 위치, 장치, 측정 항목 등의 상세 필터링을 지원합니다. 분석 리포트 생성에 필요한 데이터를 준비합니다.")
                .parameters(parametersSchema)
                .build();
    }

    // formatPossibleValuesForDescription 메소드 (이전과 동일)
    private String formatPossibleValuesForDescription(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "(사용 가능한 값 정보 없음)";
        }
        List<String> limitedValues = values.stream()
                .filter(s -> s != null && !s.isBlank())
                .limit(limit)
                .map(s -> "'" + s + "'")
                .collect(Collectors.toList());
        if (limitedValues.isEmpty()) return "(예시 값 없음)";

        String examples = String.join(", ", limitedValues);
        if (values.size() > limit) {
            examples += ", 등 (총 " + values.size() + "개)";
        }
        return examples;
    }

    // prepareDataForAiReport 메소드 (이전과 거의 동일, Schema 변경과 직접 관련 없음)
    public Map<String, Object> prepareDataForAiReport(Map<String, Object> functionCallParams) {
        // (이전 답변의 prepareDataForAiReport 메소드 내용 전체 복사)
        log.info("AI 리포트용 데이터 준비 요청 - Function Call 파라미터: {}", functionCallParams);
        Map<String, Object> result = new HashMap<>();

        // 1. 파라미터 파싱, 유효성 검사, 기본값 설정
        String startDateStr = (String) functionCallParams.get("startDateStr");
        String endDateStr = (String) functionCallParams.get("endDateStr");
        String measurement = (String) functionCallParams.get("measurement");
        String building = (String) functionCallParams.get("building");
        String origin = (String) functionCallParams.get("origin");
        String location = (String) functionCallParams.get("location");
        String deviceId = (String) functionCallParams.get("deviceId");
        String place = (String) functionCallParams.get("place");
        String gatewayId = (String) functionCallParams.get("gatewayId");

        LocalDateTime actualStartDateTime;
        LocalDateTime actualEndDateTime;
        try {
            actualStartDateTime = (startDateStr != null && !startDateStr.isBlank())
                    ? LocalDate.parse(startDateStr).atStartOfDay()
                    : LocalDate.now().minusWeeks(1).atStartOfDay();
            actualEndDateTime = (endDateStr != null && !endDateStr.isBlank())
                    ? LocalDate.parse(endDateStr).atTime(LocalTime.MAX)
                    : LocalDate.now().atTime(LocalTime.MAX);
        } catch (DateTimeParseException e) {
            log.error("날짜 형식 오류 - startDateStr='{}', endDateStr='{}'", startDateStr, endDateStr, e);
            result.put("error", "날짜 형식이 올바르지 않습니다 (YYYY-MM-DD 형식으로 요청해주세요).");
            return result;
        }

        if (measurement == null || measurement.isBlank()) {
            log.warn("AI Function Call: measurement 파라미터가 누락되었습니다.");
            result.put("error", "분석할 측정 항목(measurement)이 지정되지 않았습니다.");
            return result;
        }
        String actualMeasurement = measurement;
        String actualOrigin = (origin != null && !origin.isBlank()) ? origin : getDefaultOriginForAi(actualMeasurement);

        Map<String, String> tagFiltersForQuery = new HashMap<>();
        if (building != null && !building.isBlank()) tagFiltersForQuery.put("building", building);
        if (location != null && !location.isBlank()) tagFiltersForQuery.put("location", location);
        if (deviceId != null && !deviceId.isBlank()) tagFiltersForQuery.put("deviceId", deviceId);
        if (place != null && !place.isBlank()) tagFiltersForQuery.put("place", place);
        if (gatewayId != null && !gatewayId.isBlank()) tagFiltersForQuery.put("gatewayId", gatewayId);

        boolean includeRawDataSample = functionCallParams.get("includeRawDataSample") instanceof Boolean ? (Boolean) functionCallParams.get("includeRawDataSample") : true;
        boolean includeChartData = functionCallParams.get("includeChartData") instanceof Boolean ? (Boolean) functionCallParams.get("includeChartData") : true;
        String aggregationInterval = (String) functionCallParams.getOrDefault("aggregationInterval",
                determineDynamicAggregationInterval(actualStartDateTime.toLocalDate(), actualEndDateTime.toLocalDate()));

        result.put("actualStartDate", actualStartDateTime.toLocalDate());
        result.put("actualEndDate", actualEndDateTime.toLocalDate());
        result.put("actualMeasurement", actualMeasurement);
        if (actualOrigin != null) result.put("actualOrigin", actualOrigin);
        else result.put("actualOrigin", "모든 Origin (또는 기본값)");
        result.put("appliedFilters", new HashMap<>(tagFiltersForQuery));
        result.put("actualAggregationInterval", aggregationInterval);

        log.info("TimeSeriesDataService 호출 준비 - Origin: {}, Measurement: {}, TagFilters: {}, Period: {} ~ {}",
                actualOrigin, actualMeasurement, tagFiltersForQuery, actualStartDateTime, actualEndDateTime);

        if (includeRawDataSample) {
            List<TimeSeriesDataDto> rawData = timeSeriesDataService.getRawTimeSeriesData(
                    actualOrigin, actualMeasurement, tagFiltersForQuery, actualStartDateTime, actualEndDateTime);
            result.put("rawDataSample", rawData != null ? rawData.stream().limit(10).collect(Collectors.toList()) : Collections.emptyList());
            result.put("totalRawDataCount", rawData != null ? rawData.size() : 0);
        }

        if (includeChartData) {
            Map<String, String> chartTagFilters = new HashMap<>(tagFiltersForQuery);
            if (actualOrigin != null && !actualOrigin.isBlank()) {
                chartTagFilters.put("origin", actualOrigin);
            }
            ChartDataDto chart = timeSeriesDataService.getAggregatedChartDataForPeriod(
                    actualMeasurement, "value", chartTagFilters, actualStartDateTime, actualEndDateTime,
                    aggregationInterval, DEFAULT_CHART_X_AXIS_FORMATTER);
            result.put("charts", chart != null && chart.getLabels() != null && !chart.getLabels().isEmpty() ? List.of(chart) : Collections.emptyList());
        }

        boolean noRawData = !result.containsKey("rawDataSample") || ((List<?>)result.get("rawDataSample")).isEmpty();
        boolean noChartData = !result.containsKey("charts") || ((List<?>)result.get("charts")).isEmpty();
        if (noRawData && noChartData) {
            result.put("dataRetrievalMessage", "요청하신 조건으로 조회된 데이터가 없습니다. 기간, 측정항목, 필터 등을 확인해주세요.");
        } else {
            result.put("dataRetrievalMessage", "데이터 조회가 완료되었습니다.");
        }

        log.info("AI 리포트용 데이터 준비 완료. 결과 키: {}", result.keySet());
        return result;
    }

    // getDefaultOriginForAi, determineDynamicAggregationInterval 메소드 (이전과 동일)
    private String getDefaultOriginForAi(String measurement) {
        if (this.possibleOriginValues != null && !this.possibleOriginValues.isEmpty()) {
            if (measurement != null) {
                if (measurement.toLowerCase().contains("cpu") && this.possibleOriginValues.contains("server_data")) {
                    return "server_data";
                } else if ((measurement.toLowerCase().contains("temperature") || measurement.toLowerCase().contains("humidity")) && this.possibleOriginValues.contains("server_data")) {
                    return "server_data";
                }
            }
            return this.possibleOriginValues.get(0);
        }
        return "default_system_origin";
    }

    private String determineDynamicAggregationInterval(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) return "1h";
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween < 0) daysBetween = 0;
        if (daysBetween <= 1) return "10m";
        if (daysBetween <= 7) return "1h";
        if (daysBetween <= 31) return "6h";
        if (daysBetween <= 90) return "1d";
        return "1w";
    }
}
