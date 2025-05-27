package com.nhnacademy.environment.report.service;

import com.google.common.collect.ImmutableList;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
// Struct와 Value는 FunctionResponse 구성 시 Map<String, Object>를 직접 사용할 수 있다면 필요 없을 수 있음
// import com.google.genai.types.Struct;
// import com.google.genai.types.Value;

import com.nhnacademy.environment.report.client.GeminiClient;
import com.nhnacademy.environment.report.dto.ReportRequestDto;
import com.nhnacademy.environment.report.dto.ReportResponseDto;
import com.nhnacademy.environment.timeseries.dto.ChartDataDto;
import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto; // summarizePreparedDataForAI 에서 사용

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private final AiReportDataService aiReportDataService;
    private final GeminiClient geminiClient;

    private static final String DEFAULT_TIMEZONE = "Asia/Seoul";
    // AiReportDataService의 FunctionDeclaration에 정의된 함수 이름과 일치해야 함
    private static final String DATA_QUERY_FUNCTION_NAME = "getInfluxDbTimeSeriesData";

    public ReportResponseDto generateReport(ReportRequestDto request) {
        String userPrompt = request.getUserPrompt();
        if (userPrompt == null || userPrompt.isBlank()) {
            log.warn("사용자 프롬프트가 비어있습니다.");
            return createErrorResponse("분석 요청 내용이 없습니다.", "프롬프트 누락");
        }
        log.info("AI 리포트 생성 시작 - 사용자 프롬프트: '{}'", userPrompt);

        try {
            // --- 1단계: 사용자 프롬프트를 Gemini에게 보내 함수 호출 요청 또는 직접 답변 받기 ---
            GenerateContentResponse initialResponse = geminiClient.generateContentWithFunctionCalling(userPrompt);

            Candidate candidate = getValidCandidate(initialResponse);
            Content aiResponseContent = candidate.content().get(); // 이전 코드의 오타 수정

            // 함수 호출이 있는지 확인 (Content.parts() 및 Part.functionCall() 사용)
            Optional<FunctionCall> functionCallOptional = aiResponseContent.parts()
                    .orElse(Collections.emptyList()).stream()
                    .filter(part -> part.functionCall().isPresent())
                    .map(part -> part.functionCall().get())
                    .findFirst();

            if (functionCallOptional.isEmpty()) {
                // 함수 호출 없이 바로 텍스트 답변이 온 경우 (Content.text() 사용)
                String directAnswer = aiResponseContent.text(); // Content.text() 활용
                if (directAnswer == null || directAnswer.isBlank()) {
                    directAnswer = "AI가 응답을 생성했지만, 텍스트 내용이 없습니다. (모델 응답 확인 필요)";
                    log.warn("Gemini가 함수 호출 없이 응답했으나 텍스트 내용이 비어있음. Full Response: {}", initialResponse);
                }
                log.info("Gemini가 함수 호출 없이 직접 답변 생성: '{}'", directAnswer);
                return new ReportResponseDto(
                        directAnswer, Collections.emptyList(),
                        "AI 직접 답변", LocalDate.now(), LocalDate.now(),
                        ZonedDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).toString(),
                        "사용자 프롬프트 기반 직접 응답"
                );
            }

            FunctionCall functionCall = functionCallOptional.get();
            // FunctionCall.name()은 Optional<String> 반환
            String functionName = functionCall.name().orElseThrow(() -> new IllegalStateException("FunctionCall에 name이 없습니다."));
            // FunctionCall.args()는 Optional<Map<String, Object>> 반환
            Map<String, Object> argumentsMap = functionCall.args().orElse(Collections.emptyMap());

            log.info("Gemini 함수 호출 요청 수신: 함수명='{}', 인자='{}'", functionName, argumentsMap);

            // --- 2단계: 요청된 함수 실행 (AiReportDataService 호출) ---
            if (!DATA_QUERY_FUNCTION_NAME.equals(functionName)) {
                log.warn("지원하지 않는 함수 호출 요청: {}", functionName);
                return createErrorResponse("AI가 알 수 없는 분석 요청을 했습니다: " + functionName, "지원하지 않는 함수");
            }

            if (request.getReportType() != null && !request.getReportType().isBlank()){
                // argumentsMap은 불변 Map일 수 있으므로, 복사본을 만들어 수정
                Map<String, Object> modifiableArgumentsMap = new HashMap<>(argumentsMap);
                modifiableArgumentsMap.put("reportTypeHint", request.getReportType());
                argumentsMap = modifiableArgumentsMap; // 수정된 맵으로 교체
            }

            Map<String, Object> preparedData = aiReportDataService.prepareDataForAiReport(argumentsMap);
            if (preparedData.containsKey("error")) {
                String errorMsg = (String) preparedData.get("error");
                log.error("데이터 준비 중 오류 발생: {}", errorMsg);
                return createErrorResponse("데이터를 준비하는 중 오류가 발생했습니다: " + errorMsg, "데이터 준비 오류");
            }
            log.info("AiReportDataService로부터 데이터 준비 완료. 준비된 데이터 키: {}", preparedData.keySet());


            // --- 3단계: 함수 실행 결과를 Gemini에게 보내 최종 답변 생성 요청 ---
            Map<String, Object> functionExecutionResultForAI = summarizePreparedDataForAI(preparedData);

            // FunctionResponse.Builder의 response(Map<String, Object>) 사용
            Part functionResponsePart = Part.fromFunctionResponse(
                    functionName,
                    functionExecutionResultForAI // ★★★ Map<String, Object>를 직접 전달 ★★★
            );


            // 이전 대화 내용 구성
            Content userPromptContent = Content.builder().role("user").parts(ImmutableList.of(Part.fromText(userPrompt))).build();
            // aiResponseContent는 initialResponse에서 가져온 AI의 함수 호출을 포함한 Content

            GenerateContentResponse finalResponse = geminiClient.generateFinalAnswerFromFunctionResponse(
                    ImmutableList.of(functionResponsePart), userPromptContent, aiResponseContent);

            String finalSummaryText = finalResponse.text(); // Content.text()와 유사하게 GenerateContentResponse.text() 활용
            if (finalSummaryText == null || finalSummaryText.isBlank()) {
                finalSummaryText = "AI가 최종 요약을 생성했지만, 텍스트 내용이 없습니다. (모델 응답 확인 필요)";
                log.warn("Gemini 최종 요약 텍스트가 비어있음. Full Response: {}", finalResponse);
            }
            log.info("Gemini 최종 요약 수신 (일부): '{}'", finalSummaryText.substring(0, Math.min(finalSummaryText.length(), 200)) + "...");


            // --- 4. 최종 ReportResponseDto 구성 ---
            List<ChartDataDto> charts = (List<ChartDataDto>) preparedData.getOrDefault("charts", Collections.emptyList());
            String reportTitle = preparedData.getOrDefault("actualMeasurement", request.getReportType()) + " 분석 리포트";
            LocalDate actualStartDate = (LocalDate) preparedData.get("actualStartDate");
            LocalDate actualEndDate = (LocalDate) preparedData.get("actualEndDate");
            String filterSummary = buildFilterSummaryFromResult(preparedData);

            return new ReportResponseDto(
                    finalSummaryText,
                    charts,
                    reportTitle,
                    actualStartDate,
                    actualEndDate,
                    ZonedDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).toString(),
                    filterSummary
            );

        } catch (Exception e) {
            log.error("AI 리포트 생성 중 예외 발생: {}", e.getMessage(), e);
            return createErrorResponse("AI 리포트 생성 중 내부 오류가 발생했습니다: " + e.getMessage(), "내부 서버 오류");
        }
    }

    // --- Helper Methods ---

    private Candidate getValidCandidate(GenerateContentResponse response) {
        if (response == null || response.candidates().isEmpty()) {
            log.error("Gemini 응답이 null이거나 후보가 없습니다.");
            throw new IllegalStateException("AI로부터 유효한 응답을 받지 못했습니다.");
        }
        Candidate candidate = response.candidates().get().getFirst(); // 첫 번째 후보 사용
        if (candidate == null || candidate.content().isEmpty()) { // getContent()가 null일 수 있음
            log.error("Gemini 응답 후보 또는 후보의 Content가 null입니다. FinishReason: {}",
                    candidate != null ? candidate.finishReason() : "N/A");
            throw new IllegalStateException("AI 응답 내용이 비어있습니다.");
        }
        return candidate;
    }
    // AiReportDataService에서 반환된 데이터를 AI에게 전달하기 전에 요약/가공 (이전과 동일)
    private Map<String, Object> summarizePreparedDataForAI(Map<String, Object> preparedData) {
        Map<String, Object> summaryForAI = new HashMap<>();
        summaryForAI.put("dataRetrievalStatus", preparedData.getOrDefault("dataRetrievalMessage", "데이터 조회 상태 알 수 없음"));
        summaryForAI.put("totalRawDataCount", preparedData.getOrDefault("totalRawDataCount", 0));

        List<?> rawSampleObj = (List<?>) preparedData.getOrDefault("rawDataSample", Collections.emptyList());
        if (rawSampleObj instanceof List && !rawSampleObj.isEmpty() && rawSampleObj.get(0) instanceof TimeSeriesDataDto) {
            List<TimeSeriesDataDto> rawSample = (List<TimeSeriesDataDto>) rawSampleObj;
            summaryForAI.put("rawDataSamplePreview", rawSample.stream()
                    .map(dto -> "Time: " + (dto.getTime() != null ? dto.getTime().atZone(ZoneId.of(DEFAULT_TIMEZONE)).toLocalDate() : "N/A") +
                            ", Value: " + dto.getValue() +
                            (dto.getTags() != null && dto.getTags().containsKey("location") ? ", Location: " + dto.getTags().get("location") : ""))
                    .collect(Collectors.toList()));
        }


        List<?> chartsObj = (List<?>) preparedData.getOrDefault("charts", Collections.emptyList());
        if (chartsObj instanceof List && !chartsObj.isEmpty() && chartsObj.get(0) instanceof ChartDataDto) {
            List<ChartDataDto> charts = (List<ChartDataDto>) chartsObj;
            summaryForAI.put("chartCount", charts.size());
            summaryForAI.put("chartTitles", charts.stream().map(ChartDataDto::getTitle).collect(Collectors.toList()));
            if (!charts.isEmpty()) {
                ChartDataDto firstChart = charts.get(0);
                if (firstChart.getValues() != null && !firstChart.getValues().isEmpty()) {
                    summaryForAI.put("firstChartStats", Map.of(
                            "title", firstChart.getTitle(),
                            "average", firstChart.getValues().stream().filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(Double.NaN),
                            "max", firstChart.getValues().stream().filter(v -> v != null).mapToDouble(Double::doubleValue).max().orElse(Double.NaN),
                            "min", firstChart.getValues().stream().filter(v -> v != null).mapToDouble(Double::doubleValue).min().orElse(Double.NaN),
                            "dataPointCount", firstChart.getValues().size()
                    ));
                }
            }
        }
        summaryForAI.put("analyzedPeriod", preparedData.get("actualStartDate") + " ~ " + preparedData.get("actualEndDate"));
        summaryForAI.put("analyzedMeasurement", preparedData.get("actualMeasurement"));
        if (preparedData.containsKey("actualOrigin")) {
            summaryForAI.put("analyzedOrigin", preparedData.get("actualOrigin"));
        }
        if (preparedData.containsKey("appliedFilters") && !((Map<?,?>)preparedData.get("appliedFilters")).isEmpty()) {
            summaryForAI.put("appliedFilters", preparedData.get("appliedFilters"));
        }

        log.debug("AI에게 전달할 함수 실행 결과 요약 (FunctionResponse에 사용될 내용): {}", summaryForAI);
        return summaryForAI;
    }

    // buildFilterSummaryFromResult, createErrorResponse (이전과 동일)
    private String buildFilterSummaryFromResult(Map<String, Object> preparedData) {
        StringBuilder sb = new StringBuilder("분석 조건: ");
        sb.append("기간=").append(preparedData.get("actualStartDate")).append("~").append(preparedData.get("actualEndDate"));
        sb.append(", 측정항목=").append(preparedData.get("actualMeasurement"));
        if (preparedData.get("actualOrigin") != null && !"모든 Origin (또는 기본값)".equals(preparedData.get("actualOrigin"))) {
            sb.append(", Origin=").append(preparedData.get("actualOrigin"));
        }
        Map<?,?> filters = (Map<?,?>) preparedData.getOrDefault("appliedFilters", Collections.emptyMap());
        if (!filters.isEmpty()) {
            sb.append(", 추가 필터={");
            filters.forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
            if (!filters.isEmpty()) sb.setLength(sb.length() - 2);
            sb.append("}");
        }
        return sb.toString();
    }

    private ReportResponseDto createErrorResponse(String errorMessage, String filterSummary) {
        return new ReportResponseDto(
                errorMessage, Collections.emptyList(), "오류 발생",
                LocalDate.now(), LocalDate.now(),
                ZonedDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).toString(),
                filterSummary
        );
    }
}
