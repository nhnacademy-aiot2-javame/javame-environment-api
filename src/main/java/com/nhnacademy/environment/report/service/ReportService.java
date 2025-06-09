package com.nhnacademy.environment.report.service;

import com.nhnacademy.environment.report.client.GeminiClient;
import com.nhnacademy.environment.report.dto.ReportRequest;
import com.nhnacademy.environment.report.dto.ReportResponse;
import com.nhnacademy.environment.timeseries.dto.ChartDataDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private final AiReportDataService aiReportDataService;
    private final GeminiClient geminiClient;

    // ReportService.java에서 GeminiClient 호출 부분 수정
    public ReportResponse generateReport(ReportRequest request) {
        String userPrompt = request.getUserPrompt();
        log.info("AI 리포트 생성 시작 - 사용자 프롬프트: '{}'", userPrompt);

        try {
            // 1. 데이터 준비
            Map<String, Object> preparedData = aiReportDataService.prepareSimpleReport(userPrompt);
            log.info("데이터 준비 완료 - 키: {}", preparedData.keySet());

            // 2. 차트 데이터 생성
            List<ChartDataDto> chartVisualizations = extractChartsFromPreparedData(preparedData);
            log.info("차트 생성 완료 - {} 개", chartVisualizations.size());

            // 3. AI 요약 생성 (★★★ 2개 파라미터로 수정 ★★★)
            String aiSummary = geminiClient.generateSummary(preparedData, userPrompt);
            log.info("AI 요약 생성 완료");

            return ReportResponse.builder()
                    .summaryText(aiSummary)
                    .chartVisualizations(chartVisualizations)
                    .reportOverallTitle("AI 분석 리포트")
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("리포트 생성 실패", e);
            throw new RuntimeException("리포트 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }


    /**
     * 기존 ChartDataDto 구조를 사용하여 차트 생성.
     */
    @SuppressWarnings("unchecked")
    private List<ChartDataDto> extractChartsFromPreparedData(Map<String, Object> preparedData) {
        log.info("차트 데이터 추출 시작 - preparedData keys: {}", preparedData.keySet());

        List<ChartDataDto> charts = new ArrayList<>();

        // ★★★ 1. 기존 chartData 확인 ★★★
        Object chartDataObj = preparedData.get("chartData");
        if (chartDataObj instanceof ChartDataDto) {
            ChartDataDto existingChart = (ChartDataDto) chartDataObj;
            if (existingChart.getLabels() != null && !existingChart.getLabels().isEmpty() &&
                    existingChart.getData() != null && !existingChart.getData().isEmpty()) {
                log.info("✅ 기존 차트 데이터 사용 - {} 포인트", existingChart.getLabels().size());
                charts.add(existingChart);
                return charts;
            }
        }

        // ★★★ 2. rawDataSample에서 시계열 차트 생성 ★★★
        ChartDataDto timeSeriesChart = createTimeSeriesChartFromRawData(preparedData);
        if (timeSeriesChart != null) {
            charts.add(timeSeriesChart);
            log.info("✅ 시계열 차트 생성 성공 - {} 포인트", timeSeriesChart.getLabels().size());
        }

        // ★★★ 3. aggregatedData에서 집계 차트 생성 ★★★
        ChartDataDto aggregatedChart = createAggregatedChartFromData(preparedData);
        if (aggregatedChart != null) {
            charts.add(aggregatedChart);
            log.info("✅ 집계 차트 생성 성공 - {} 포인트", aggregatedChart.getLabels().size());
        }

        if (charts.isEmpty()) {
            log.warn("❌ 차트 생성 불가 - 유효한 데이터 없음");
        }

        return charts;
    }

    /**
     * rawDataSample에서 시계열 차트 생성 (기존 DTO 구조 사용).
     */
    @SuppressWarnings("unchecked")
    private ChartDataDto createTimeSeriesChartFromRawData(Map<String, Object> preparedData) {
        try {
            List<?> samples = (List<?>) preparedData.get("rawDataSample");
            if (samples == null || samples.isEmpty()) {
                return null;
            }

            List<String> labels = new ArrayList<>();
            List<Double> data = new ArrayList<>(); // ★★★ data 필드 사용 ★★★

            // 최대 20개 포인트로 제한
            samples.stream().limit(20).forEach(sample -> {
                if (sample instanceof Map) {
                    Map<?, ?> sampleMap = (Map<?, ?>) sample;
                    Object time = sampleMap.get("time");
                    Object value = sampleMap.get("value");

                    if (time != null && value != null) {
                        // 시간 포맷팅
                        String timeLabel = formatTimeLabel(time.toString());

                        // 값 변환
                        try {
                            double numValue = Double.parseDouble(value.toString());
                            labels.add(timeLabel);
                            data.add(numValue);
                        } catch (NumberFormatException e) {
                            log.debug("숫자 변환 실패: {}", value);
                        }
                    }
                }
            });

            if (!labels.isEmpty() && !data.isEmpty()) {
                String measurement = (String) preparedData.get("measurement");
                String title = getMetricDisplayName(measurement) + " 시계열 추이";

                ChartDataDto chart = new ChartDataDto();
                chart.setLabels(labels);
                chart.setData(data); // ★★★ data 필드에 숫자 데이터 ★★★
                chart.setTitle(title);

                return chart;
            }

        } catch (Exception e) {
            log.error("시계열 차트 생성 실패", e);
        }

        return null;
    }

    /**
     * aggregatedData에서 집계 차트 생성 (기존 DTO 구조 사용).
     */
    @SuppressWarnings("unchecked")
    private ChartDataDto createAggregatedChartFromData(Map<String, Object> preparedData) {
        try {
            Object aggregatedObj = preparedData.get("aggregatedData");
            if (!(aggregatedObj instanceof List)) {
                return null;
            }

            List<?> aggregatedList = (List<?>) aggregatedObj;
            if (aggregatedList.isEmpty()) {
                return null;
            }

            List<String> labels = new ArrayList<>();
            List<Double> data = new ArrayList<>();

            aggregatedList.forEach(item -> {
                if (item instanceof Map) {
                    Map<?, ?> itemMap = (Map<?, ?>) item;
                    Object time = itemMap.get("time");
                    Object value = itemMap.get("value");

                    if (time != null && value != null) {
                        try {
                            String timeLabel = formatTimeLabel(time.toString());
                            double numValue = Double.parseDouble(value.toString());
                            labels.add(timeLabel);
                            data.add(numValue);
                        } catch (Exception e) {
                            log.debug("집계 데이터 변환 실패: time={}, value={}", time, value);
                        }
                    }
                }
            });

            if (!labels.isEmpty() && !data.isEmpty()) {
                String measurement = (String) preparedData.get("measurement");
                String title = getMetricDisplayName(measurement) + " 시간별 집계";

                ChartDataDto chart = new ChartDataDto();
                chart.setLabels(labels);
                chart.setData(data);
                chart.setTitle(title);

                return chart;
            }

        } catch (Exception e) {
            log.error("집계 차트 생성 실패", e);
        }

        return null;
    }

    /**
     * 시간 라벨 포맷팅.
     */
    private String formatTimeLabel(String timeStr) {
        try {
            if (timeStr.length() >= 19) {
                LocalDateTime dateTime = LocalDateTime.parse(timeStr.substring(0, 19));
                return dateTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
            }
        } catch (Exception e) {
            log.debug("시간 포맷팅 실패: {}", timeStr);
        }

        return timeStr.substring(0, Math.min(16, timeStr.length()));
    }

    /**
     * 측정값 표시명 반환.
     */
    private String getMetricDisplayName(String measurement) {
        if (measurement == null) return "측정값";

        switch (measurement) {
            case "usage_user":
            case "usage_idle":
                return "CPU 사용률";
            case "used_percent":
                return "사용률";
            case "temp_input":
                return "온도";
            case "bytes_sent":
                return "송신 바이트";
            case "bytes_recv":
                return "수신 바이트";
            case "load1":
                return "시스템 로드";
            default:
                return measurement.replace("_", " ");
        }
    }
}

