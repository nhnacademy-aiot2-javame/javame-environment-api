package com.nhnacademy.environment.report.service; // 실제 패키지 경로로 수정

import com.nhnacademy.environment.report.dto.ReportRequestDto;
import com.nhnacademy.environment.report.dto.ReportResponseDto;
import com.nhnacademy.environment.timeseries.dto.ChartDataDto;
import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto;
import com.nhnacademy.environment.timeseries.service.TimeSeriesAverageService;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService { // 서비스 이름 변경 (GeminiReportService -> ReportService)

    private final TimeSeriesDataService timeSeriesDataService;
    private final TimeSeriesAverageService timeSeriesAverageService;


    private static final String DEFAULT_TIMEZONE = "Asia/Seoul";
    private static final DateTimeFormatter REPORT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter CHART_DATE_LABEL_FORMATTER_DAILY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter CHART_DATE_LABEL_FORMATTER_HOURLY = DateTimeFormatter.ofPattern("HH:mm");

    public ReportResponseDto generateWeeklyReport(ReportRequestDto requestDto) {
        return generateReportInternal(requestDto, "주간");
    }

    public ReportResponseDto generateMonthlyReport(ReportRequestDto requestDto) {
        return generateReportInternal(requestDto, "월간");
    }

    private ReportResponseDto generateReportInternal(ReportRequestDto requestDto, String periodType) {
        log.info("{} 리포트 생성 시작 - 요청: {}", periodType, requestDto);
        validateReportRequest(requestDto);

        String reportTitle = String.format("%s %s 리포트 (%s ~ %s)",
                determineReportSubject(requestDto), periodType,
                requestDto.getStartDate().toString(), requestDto.getEndDate().toString());
        String filterSummary = buildFilterSummary(requestDto);

        // 1. 데이터 조회 및 집계 (Gemini API 호출 없이, 데이터만 준비)
        List<Map<String, Object>> aggregatedMetricsTable = fetchAndAggregateMetricsForPeriod(requestDto);
        List<ChartDataDto> chartDataForReport = fetchChartDataForPeriod(requestDto, periodType);

        // 2. AI 요약 대신 기본 메시지 또는 빈 문자열 사용
        String reportSummaryText = String.format("%s (%s ~ %s)에 대한 데이터 기반 리포트입니다. 상세 내용은 차트와 표를 참고하세요.",
                determineReportSubject(requestDto),
                requestDto.getStartDate().toString(),
                requestDto.getEndDate().toString());
        if (requestDto.getUserPrompt() != null && !requestDto.getUserPrompt().isBlank()){
            reportSummaryText += "\n\n사용자 요청 사항: " + requestDto.getUserPrompt();
        }


        // 3. 최종 ReportResponseDto 구성
        return new ReportResponseDto(
                reportSummaryText, // AI 요약 대신 기본 텍스트
                chartDataForReport,
                aggregatedMetricsTable, // 이전에는 aggregatedMetricsForPrompt였으나, 테이블용으로 명확히
                reportTitle,
                requestDto.getStartDate(),
                requestDto.getEndDate(),
                LocalDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).format(REPORT_DATE_TIME_FORMATTER),
                filterSummary
        );
    }

    private void validateReportRequest(ReportRequestDto requestDto) {
        if (requestDto.getStartDate() == null || requestDto.getEndDate() == null || requestDto.getStartDate().isAfter(requestDto.getEndDate())) {
            throw new IllegalArgumentException("시작일과 종료일은 필수이며, 시작일은 종료일보다 이전이어야 합니다.");
        }
        if (requestDto.getMeasurements() == null || requestDto.getMeasurements().isEmpty()) {
            throw new IllegalArgumentException("분석할 측정항목(measurements)은 최소 하나 이상 필요합니다.");
        }
    }

    private List<Map<String, Object>> fetchAndAggregateMetricsForPeriod(ReportRequestDto requestDto) {
        List<Map<String, Object>> metricsTableData = new ArrayList<>(); // 변수명 변경 (prompt용이 아니므로)
        LocalDateTime startDateTime = requestDto.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = requestDto.getEndDate().plusDays(1).atStartOfDay();

        String primaryOrigin = getPrimaryFilterValue(requestDto.getTargetOrigins());
        if (primaryOrigin == null && (requestDto.getTargetOrigins() == null || requestDto.getTargetOrigins().isEmpty())) {
            log.warn("Origin 정보가 없어 집계 데이터 조회를 건너<0xEB><0x9C><0x85>니다.");
            return metricsTableData;
        }

        for (String measurement : requestDto.getMeasurements()) {
            Map<String, String> influxFilters = buildInfluxFilters(requestDto, primaryOrigin, measurement);

            Double averageValue = timeSeriesAverageService.getFixedRangeAverageSensorValue(
                    primaryOrigin, measurement, influxFilters, startDateTime, endDateTime);

            // ★★★ TimeSeriesDataService에 getRawTimeSeriesData 메소드가 구현되어 있어야 함 ★★★
            List<TimeSeriesDataDto> rawDataPoints = timeSeriesDataService.getRawTimeSeriesData(
                    primaryOrigin, measurement, influxFilters, startDateTime, endDateTime
            );

            Map<String, Object> metricRow = new HashMap<>(); // 변수명 변경
            metricRow.put("측정항목", measurement); // 테이블 컬럼명으로 변경
            metricRow.put("평균값", averageValue != null ? roundToTwoDecimals(averageValue) : "데이터 없음");
            metricRow.put("단위", inferUnitFromMeasurement(measurement));

            if (rawDataPoints != null && !rawDataPoints.isEmpty()) {
                OptionalDouble minOpt = rawDataPoints.stream().mapToDouble(TimeSeriesDataDto::getValue).min();
                OptionalDouble maxOpt = rawDataPoints.stream().mapToDouble(TimeSeriesDataDto::getValue).max();
                metricRow.put("최소값", minOpt.isPresent() ? roundToTwoDecimals(minOpt.getAsDouble()) : "데이터 없음");
                metricRow.put("최대값", maxOpt.isPresent() ? roundToTwoDecimals(maxOpt.getAsDouble()) : "데이터 없음");
            } else {
                metricRow.put("최소값", "데이터 없음");
                metricRow.put("최대값", "데이터 없음");
            }
            metricsTableData.add(metricRow);
        }
        log.info("기간 {} ~ {} 에 대한 테이블용 집계 데이터 생성 완료: {}개 항목", requestDto.getStartDate(), requestDto.getEndDate(), metricsTableData.size());
        return metricsTableData;
    }

    private List<ChartDataDto> fetchChartDataForPeriod(ReportRequestDto requestDto, String periodType) {
        List<ChartDataDto> chartDataList = new ArrayList<>();
        LocalDateTime startDateTime = requestDto.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = requestDto.getEndDate().plusDays(1).atStartOfDay();
        String primaryOrigin = getPrimaryFilterValue(requestDto.getTargetOrigins());

        if (primaryOrigin == null && (requestDto.getTargetOrigins() == null || requestDto.getTargetOrigins().isEmpty())) {
            log.warn("Origin 정보가 없어 차트 데이터 조회를 건너<0xEB><0x9C><0x85>니다.");
            return chartDataList;
        }

        DateTimeFormatter labelFormatter;
        String aggregationInterval;
        long daysBetween = ChronoUnit.DAYS.between(requestDto.getStartDate(), requestDto.getEndDate());

        if (periodType.equals("주간") || daysBetween <= 7) {
            labelFormatter = (daysBetween <= 1) ? CHART_DATE_LABEL_FORMATTER_HOURLY : CHART_DATE_LABEL_FORMATTER_DAILY;
            aggregationInterval = (daysBetween <= 1) ? "1h" : "1d";
        } else {
            labelFormatter = CHART_DATE_LABEL_FORMATTER_DAILY;
            aggregationInterval = "1d";
        }

        for (String measurement : requestDto.getMeasurements()) {
            Map<String, String> influxFilters = buildInfluxFilters(requestDto, primaryOrigin, measurement);

            // ★★★ TimeSeriesDataService에 getAggregatedChartDataForPeriod 메소드가 구현되어 있어야 함 ★★★
            ChartDataDto chartData = timeSeriesDataService.getAggregatedChartDataForPeriod(
                    measurement, "value", influxFilters, startDateTime, endDateTime,
                    aggregationInterval, labelFormatter
            );
            chartData.setTitle(String.format("%s %s 추이 (%s 집계)", measurement, periodType, aggregationInterval.equals("1d") ? "일별" : "시간별"));
            chartDataList.add(chartData);
        }
        log.info("기간 {} ~ {} 에 대한 차트 데이터 생성 완료: {}개 차트", requestDto.getStartDate(), requestDto.getEndDate(), chartDataList.size());
        return chartDataList;
    }

    private Map<String, String> buildInfluxFilters(ReportRequestDto requestDto, String primaryOrigin, String currentMeasurement) {
        Map<String, String> filters = new HashMap<>();
        if (primaryOrigin != null) {
            filters.put("origin", primaryOrigin);
        }
        Optional.ofNullable(getPrimaryFilterValue(requestDto.getTargetLocations())).ifPresent(loc -> filters.put("location", loc));
        Optional.ofNullable(getPrimaryFilterValue(requestDto.getDeviceIds())).ifPresent(devId -> filters.put("deviceId", devId));
        Optional.ofNullable(getPrimaryFilterValue(requestDto.getGatewayIds())).ifPresent(gwId -> filters.put("gatewayId", gwId));
        return filters;
    }

    private String getPrimaryFilterValue(List<String> list) {
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    private String buildFilterSummary(ReportRequestDto requestDto) {
        List<String> summaries = new ArrayList<>();
        if (requestDto.getTargetOrigins() != null && !requestDto.getTargetOrigins().isEmpty()) {
            summaries.add("Origin: " + String.join(", ", requestDto.getTargetOrigins()));
        }
        if (requestDto.getTargetLocations() != null && !requestDto.getTargetLocations().isEmpty()) {
            summaries.add("Location: " + String.join(", ", requestDto.getTargetLocations()));
        }
        if (requestDto.getDeviceIds() != null && !requestDto.getDeviceIds().isEmpty()) {
            summaries.add("Device ID: " + String.join(", ", requestDto.getDeviceIds()));
        }
        if (requestDto.getGatewayIds() != null && !requestDto.getGatewayIds().isEmpty()) {
            summaries.add("Gateway ID: " + String.join(", ", requestDto.getGatewayIds()));
        }
        if (requestDto.getMeasurements() != null && !requestDto.getMeasurements().isEmpty()) {
            summaries.add("측정항목: " + String.join(", ", requestDto.getMeasurements()));
        }
        return summaries.isEmpty() ? "전체 데이터에 대한 리포트" : String.join(" | ", summaries);
    }

    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }

    private String inferUnitFromMeasurement(String measurement) {
        if (measurement == null) return "";
        String lower = measurement.toLowerCase();
        if (lower.contains("percent") || lower.contains("cpu") || lower.contains("memory_usage") || lower.contains("disk_usage") || lower.contains("utilization")) return "%";
        if (lower.contains("temp") || lower.contains("celsius")) return "°C";
        if (lower.contains("humidity")) return "%RH";
        if (lower.contains("network_bytes") || lower.contains("disk_io_bytes")) return "Bytes";
        if (lower.contains("network_packets") || lower.contains("disk_ops")) return "개";
        if (lower.contains("voltage")) return "V";
        if (lower.contains("current")) return "A";
        if (lower.contains("power")) return "W";
        return "";
    }

    private String determineReportSubject(ReportRequestDto requestDto) {
        if (requestDto.getMeasurements() != null && !requestDto.getMeasurements().isEmpty()) {
            String firstMeasurement = requestDto.getMeasurements().get(0);
            if (firstMeasurement.toLowerCase().contains("cpu")) return "CPU 성능";
            if (firstMeasurement.toLowerCase().contains("mem")) return "메모리 사용량";
            if (firstMeasurement.toLowerCase().contains("disk")) return "디스크 사용량";
            if (firstMeasurement.toLowerCase().contains("net")) return "네트워크 트래픽";
            if (firstMeasurement.toLowerCase().contains("temp")) return "온도";
            if (firstMeasurement.toLowerCase().contains("humidity")) return "습도";
            return requestDto.getMeasurements().stream().limit(1).collect(Collectors.joining(", ")) + " 관련";
        }
        if (requestDto.getTargetOrigins() != null && !requestDto.getTargetOrigins().isEmpty()) {
            return getPrimaryFilterValue(requestDto.getTargetOrigins()) + " 데이터";
        }
        return "시스템 종합";
    }
}
