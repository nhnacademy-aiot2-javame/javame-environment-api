package com.nhnacademy.environment.report.service;

import com.nhnacademy.environment.report.client.GeminiClient;
import com.nhnacademy.environment.report.dto.ReportRequestDto;
import com.nhnacademy.environment.report.dto.ReportResponseDto;
import com.nhnacademy.environment.timeseries.dto.ChartDataDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private final AiReportDataService aiReportDataService;
    private final GeminiClient geminiClient;

    private static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    public ReportResponseDto generateReport(ReportRequestDto request) {
        String userPrompt = request.getUserPrompt();
        if (userPrompt == null || userPrompt.isBlank()) {
            log.warn("사용자 프롬프트가 비어있습니다.");
            return createErrorResponse("분석 요청 내용이 없습니다.", "프롬프트 누락");
        }
        log.info("AI 리포트 생성 시작 - 사용자 프롬프트: '{}'", userPrompt);

        try {
            // --- 1단계: AiReportDataService로 키워드 기반 데이터 조회 ---
            Map<String, Object> preparedData = aiReportDataService.prepareSimpleReport(userPrompt);

            // 오류 체크
            if (!(Boolean) preparedData.getOrDefault("success", false)) {
                String errorMsg = (String) preparedData.get("error");
                log.error("데이터 준비 중 오류 발생: {}", errorMsg);
                return createErrorResponse(errorMsg, "데이터 조회 실패");
            }

            log.info("AiReportDataService로부터 데이터 준비 완료. gatewayId: {}, measurement: {}, 데이터 건수: {}",
                    preparedData.get("gatewayId"), preparedData.get("measurement"), preparedData.get("totalDataCount"));

            // --- 2단계: 조회된 데이터를 Gemini에게 보내서 AI 요약 생성 ---
            String dataForAI = buildPromptForAI(userPrompt, preparedData);

            String aiSummary = geminiClient.generateSummary(dataForAI);
            if (aiSummary == null || aiSummary.isBlank()) {
                aiSummary = "AI가 요약을 생성했지만 내용이 비어있습니다. 데이터: " + preparedData.get("summary");
                log.warn("Gemini 요약이 비어있음. 원본 데이터 요약으로 대체");
            }

            log.info("Gemini AI 요약 생성 완료 (일부): '{}'",
                    aiSummary.substring(0, Math.min(aiSummary.length(), 100)) + "...");

            // --- 3단계: 최종 ReportResponseDto 구성 ---
            List<ChartDataDto> charts = extractChartsFromPreparedData(preparedData);
            String reportTitle = buildReportTitle(preparedData, request.getReportType());
            LocalDate startDate = extractDateFromPreparedData(preparedData, "startTime", LocalDate.now().minusDays(1));
            LocalDate endDate = extractDateFromPreparedData(preparedData, "endTime", LocalDate.now());
            String filterSummary = buildFilterSummary(preparedData);

            return new ReportResponseDto(
                    aiSummary,
                    charts,
                    reportTitle,
                    startDate,
                    endDate,
                    ZonedDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).toString(),
                    filterSummary
            );

        } catch (Exception e) {
            log.error("AI 리포트 생성 중 예외 발생: {}", e.getMessage(), e);
            return createErrorResponse("AI 리포트 생성 중 내부 오류가 발생했습니다: " + e.getMessage(), "내부 서버 오류");
        }
    }

    /**
     * AiReportDataService에서 조회한 데이터를 Gemini에게 전달할 프롬프트로 구성
     */
    private String buildPromptForAI(String originalUserPrompt, Map<String, Object> preparedData) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("사용자 요청: ").append(originalUserPrompt).append("\n\n");

        prompt.append("조회된 데이터 정보:\n");
        prompt.append("- 시스템: ").append(preparedData.get("gatewayId")).append("\n");
        prompt.append("- 측정 항목: ").append(preparedData.get("measurement")).append("\n");
        prompt.append("- 위치: ").append(preparedData.get("location")).append("\n");
        prompt.append("- 조회 기간: ").append(preparedData.get("startTime")).append(" ~ ").append(preparedData.get("endTime")).append("\n");
        prompt.append("- 데이터 건수: ").append(preparedData.get("totalDataCount")).append("건\n\n");

        prompt.append("데이터 요약: ").append(preparedData.get("summary")).append("\n\n");

        // 샘플 데이터가 있으면 포함
        if (preparedData.containsKey("rawDataSample")) {
            List<?> samples = (List<?>) preparedData.get("rawDataSample");
            if (samples != null && !samples.isEmpty()) {
                prompt.append("최근 데이터 샘플 (최대 3개):\n");
                samples.stream().limit(3).forEach(sample -> {
                    prompt.append("- ").append(sample.toString()).append("\n");
                });
                prompt.append("\n");
            }
        }

        prompt.append("위 데이터를 바탕으로 사용자에게 친근하고 이해하기 쉽게 분석 결과를 설명해주세요. ");
        prompt.append("데이터의 패턴, 특이사항, 권장사항 등을 포함해서 종합적인 리포트를 작성해주세요.");

        return prompt.toString();
    }

    /**
     * preparedData에서 차트 데이터 추출
     */
    @SuppressWarnings("unchecked")
    private List<ChartDataDto> extractChartsFromPreparedData(Map<String, Object> preparedData) {
        Object chartDataObj = preparedData.get("chartData");
        if (chartDataObj instanceof ChartDataDto) {
            ChartDataDto chartData = (ChartDataDto) chartDataObj;
            // 차트에 데이터가 있는지 확인
            if (chartData.getLabels() != null && !chartData.getLabels().isEmpty() &&
                    chartData.getValues() != null && !chartData.getValues().isEmpty()) {
                return List.of(chartData);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 리포트 제목 생성
     */
    private String buildReportTitle(Map<String, Object> preparedData, String requestReportType) {
        String measurement = (String) preparedData.get("measurement");
        String gatewayId = (String) preparedData.get("gatewayId");

        if (measurement != null) {
            return measurement + " 분석 리포트";
        } else if (gatewayId != null) {
            return gatewayId + " 시스템 리포트";
        } else if (requestReportType != null && !requestReportType.isBlank()) {
            return requestReportType + " 리포트";
        } else {
            return "시스템 모니터링 리포트";
        }
    }

    /**
     * preparedData에서 날짜 추출 (LocalDateTime -> LocalDate 변환)
     */
    private LocalDate extractDateFromPreparedData(Map<String, Object> preparedData, String key, LocalDate defaultValue) {
        Object dateObj = preparedData.get(key);
        if (dateObj instanceof LocalDateTime) {
            return ((LocalDateTime) dateObj).toLocalDate();
        } else if (dateObj instanceof LocalDate) {
            return (LocalDate) dateObj;
        }
        return defaultValue;
    }

    /**
     * 필터 요약 생성
     */
    private String buildFilterSummary(Map<String, Object> preparedData) {
        StringBuilder sb = new StringBuilder("분석 조건: ");

        LocalDate startDate = extractDateFromPreparedData(preparedData, "startTime", null);
        LocalDate endDate = extractDateFromPreparedData(preparedData, "endTime", null);
        if (startDate != null && endDate != null) {
            sb.append("기간=").append(startDate).append("~").append(endDate);
        }

        String measurement = (String) preparedData.get("measurement");
        if (measurement != null) {
            sb.append(", 측정항목=").append(measurement);
        }

        String gatewayId = (String) preparedData.get("gatewayId");
        if (gatewayId != null) {
            sb.append(", 시스템=").append(gatewayId);
        }

        String location = (String) preparedData.get("location");
        if (location != null) {
            sb.append(", 위치=").append(location);
        }

        return sb.toString();
    }

    private ReportResponseDto createErrorResponse(String errorMessage, String filterSummary) {
        return new ReportResponseDto(
                errorMessage,
                Collections.emptyList(),
                "오류 발생",
                LocalDate.now(),
                LocalDate.now(),
                ZonedDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).toString(),
                filterSummary
        );
    }
}
