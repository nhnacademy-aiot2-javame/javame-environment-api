package com.nhnacademy.environment.report.service;

import com.nhnacademy.environment.report.client.GeminiClient;
import com.nhnacademy.environment.report.dto.ReportRequestDto;
import com.nhnacademy.environment.report.dto.ReportResponseDto;
import com.nhnacademy.environment.timeseries.dto.ChartDataDto;
import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RequiredArgsConstructor
@Service
@Slf4j
public class ReportService {

    private final TimeSeriesDataService influxQueryService;
    private final GeminiClient geminiClient;

    // 프롬프트 최대 길이 (예: 4000자) - 필요에 따라 조절
    private static final int MAX_PROMPT_LENGTH = 4000;

    public ReportResponseDto generateReport(ReportRequestDto request) {
        // 1. 필터 조합
        Map<String, String> filters = new HashMap<>();
        if (request.getTargetLocations() != null) {
            filters.put("location", String.join(",", request.getTargetLocations()));
        }
        if (request.getDeviceIds() != null) {
            filters.put("deviceId", String.join(",", request.getDeviceIds()));
        }
        if (request.getGatewayIds() != null) {
            filters.put("gatewayId", String.join(",", request.getGatewayIds()));
        }

        List<ChartDataDto> charts = new ArrayList<>();
        List<Map<String, Object>> table = new ArrayList<>();

        // 2. 시계열 데이터 조회 및 차트 생성
        for (String origin : request.getTargetOrigins()) {
            for (String measurement : request.getMeasurements()) {
                List<TimeSeriesDataDto> rawData = influxQueryService.getRawTimeSeriesData(
                        origin,
                        measurement,
                        filters,
                        request.getStartDate().atStartOfDay(),
                        request.getEndDate().atTime(LocalTime.MAX)
                );

                // 테이블용 데이터 적재
                for (TimeSeriesDataDto dto : rawData) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("time", dto.getTime().toString());
                    row.put("location", dto.getLocation());
                    row.put("value", dto.getValue());
                    row.put("measurement", dto.getMeasurement());
                    row.putAll(dto.getTags());
                    table.add(row);
                }

                // 차트 데이터 조회
                ChartDataDto chart = influxQueryService.getAggregatedChartDataForPeriod(
                        measurement,
                        "value",
                        filters,
                        request.getStartDate().atStartOfDay(),
                        request.getEndDate().atTime(LocalTime.MAX),
                        "1h",
                        DateTimeFormatter.ofPattern("MM-dd HH:mm")
                );
                charts.add(chart);
            }
        }

        // 3. Gemini AI 프롬프트 생성 및 요약 요청
        String prompt = buildPromptForGemini(request, table);

        // 프롬프트 길이 제한 체크 및 자르기
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            prompt = truncatePrompt(prompt, MAX_PROMPT_LENGTH);
            log.warn("[WARN] 프롬프트 길이 초과로 자름. 현재 길이: " + prompt.length());
        }

        String summary = geminiClient.generateSummary(prompt);

        // 4. 결과 반환
        return new ReportResponseDto(
                summary,
                charts,
                table,
                request.getReportType() + " 리포트",
                request.getStartDate(),
                request.getEndDate(),
                ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toString(),
                "origin: " + request.getTargetOrigins() + ", measurement: " + request.getMeasurements()
        );
    }

    private String buildPromptForGemini(ReportRequestDto request, List<Map<String, Object>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 환경 측정 데이터 리포트를 위한 요약입니다.\n");
        sb.append("기간: ").append(request.getStartDate()).append(" ~ ").append(request.getEndDate()).append("\n");
        sb.append("측정 항목: ").append(String.join(", ", request.getMeasurements())).append("\n");
        if (request.getUserPrompt() != null && !request.getUserPrompt().isBlank()) {
            sb.append("사용자 요청 사항: ").append(request.getUserPrompt()).append("\n");
        }
        sb.append("데이터 예시:\n");

        // 최대 5개 데이터만 예시로 추가
        for (int i = 0; i < Math.min(5, data.size()); i++) {
            Map<String, Object> row = data.get(i);
            sb.append("- ");
            row.forEach((k, v) -> sb.append(k).append(": ").append(v).append(", "));
            sb.setLength(sb.length() - 2); // 마지막 콤마 제거
            sb.append("\n");
        }

        sb.append("위 데이터를 기반으로 주요 이상 징후나 경향을 요약해 주세요.");
        return sb.toString();
    }

    /**
     * 프롬프트를 최대 길이에 맞게 자연스럽게 자르는 메서드
     * 문장 단위로 자르도록 최대한 마침표(., !, ?)를 기준으로 자릅니다.
     */
    private String truncatePrompt(String prompt, int maxLength) {
        if (prompt.length() <= maxLength) {
            return prompt;
        }

        // maxLength보다 작은 길이의 부분 문자열을 자름
        String truncated = prompt.substring(0, maxLength);

        // 가장 마지막 마침표 위치 찾기 (문장 단위 자름)
        int lastPeriodIndex = Math.max(
                Math.max(truncated.lastIndexOf('.'), truncated.lastIndexOf('!')),
                truncated.lastIndexOf('?')
        );

        if (lastPeriodIndex > 0) {
            return truncated.substring(0, lastPeriodIndex + 1);
        } else {
            // 마침표가 없으면 그냥 자른 부분 반환
            return truncated;
        }
    }
}
