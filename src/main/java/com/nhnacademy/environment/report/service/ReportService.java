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
            // --- 1단계: AiReportDataService로 데이터 조회 (일반/구체적 요청 모두 처리) ---
            Map<String, Object> preparedData = aiReportDataService.prepareSimpleReport(userPrompt);

            // 오류 체크
            if (!(Boolean) preparedData.getOrDefault("success", false)) {
                String errorMsg = (String) preparedData.get("error");
                log.error("데이터 준비 중 오류 발생: {}", errorMsg);
                return createErrorResponse(errorMsg, "데이터 조회 실패");
            }

            // ★★★ 일반 요청인 경우 특별 처리 ★★★
            if ((Boolean) preparedData.getOrDefault("isGeneralRequest", false)) {
                return handleGeneralRequest(userPrompt, preparedData, request.getReportType());
            }

            // ★★★ 구체적 시스템 요청 처리 ★★★
            return handleSpecificSystemRequest(userPrompt, preparedData, request.getReportType());

        } catch (Exception e) {
            log.error("AI 리포트 생성 중 예외 발생: {}", e.getMessage(), e);
            return createErrorResponse("AI 리포트 생성 중 내부 오류가 발생했습니다: " + e.getMessage(), "내부 서버 오류");
        }
    }

    /**
     * ★★★ 새로운 메소드: 일반 요청 처리 (AI 가이드 제공) ★★★
     */
    private ReportResponseDto handleGeneralRequest(String userPrompt, Map<String, Object> preparedData, String reportType) {
        log.info("일반 요청 처리 - AI 시스템 가이드 제공");

        // 시스템 프롬프트 + 사용자 요청 + 시스템 정보 조합
        String enhancedPrompt = buildSystemPromptForGeneral() + "\n\n" +
                "사용자 요청: " + userPrompt + "\n\n" +
                preparedData.get("summary");

        String aiResponse = geminiClient.generateSummary(enhancedPrompt);
        if (aiResponse == null || aiResponse.isBlank()) {
            aiResponse = (String) preparedData.get("summary"); // 기본 가이드 사용
            log.warn("Gemini 응답이 비어있어 기본 시스템 가이드 사용");
        }

        log.info("일반 요청 AI 가이드 생성 완료 (일부): '{}'",
                aiResponse.substring(0, Math.min(aiResponse.length(), 100)) + "...");

        return new ReportResponseDto(
                aiResponse,
                Collections.emptyList(), // 일반 요청은 차트 없음
                "시스템 모니터링 가이드",
                LocalDate.now(),
                LocalDate.now(),
                ZonedDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).toString(),
                "일반 요청 - AI 추천 가이드"
        );
    }

    /**
     * ★★★ 새로운 메소드: 구체적 시스템 요청 처리 ★★★
     */
    private ReportResponseDto handleSpecificSystemRequest(String userPrompt, Map<String, Object> preparedData, String reportType) {
        log.info("구체적 시스템 요청 처리 - gatewayId: {}, measurement: {}, 데이터 건수: {}",
                preparedData.get("gatewayId"), preparedData.get("measurement"), preparedData.get("totalDataCount"));

        // --- 2단계: 조회된 데이터를 Gemini에게 보내서 AI 요약 생성 ---
        String dataForAI = buildPromptForSpecificAnalysis(userPrompt, preparedData);

        String aiSummary = geminiClient.generateSummary(dataForAI);
        if (aiSummary == null || aiSummary.isBlank()) {
            aiSummary = "AI가 요약을 생성했지만 내용이 비어있습니다. 데이터: " + preparedData.get("summary");
            log.warn("Gemini 요약이 비어있음. 원본 데이터 요약으로 대체");
        }

        log.info("구체적 시스템 분석 AI 요약 생성 완료 (일부): '{}'",
                aiSummary.substring(0, Math.min(aiSummary.length(), 100)) + "...");

        // --- 3단계: 최종 ReportResponseDto 구성 ---
        List<ChartDataDto> charts = extractChartsFromPreparedData(preparedData);
        String reportTitle = buildReportTitle(preparedData, reportType);
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
    }

    /**
     * ★★★ 새로운 메소드: 일반 요청용 시스템 프롬프트 ★★★
     */
    private String buildSystemPromptForGeneral() {
        return "당신은 IoT 시스템 모니터링 전문가입니다. " +
                "사용자의 일반적인 요청을 분석하여 다음과 같이 친근하게 응답해주세요:\n\n" +
                "1. 사용자 요청의 의도를 파악하고 간단히 설명\n" +
                "2. 현재 모니터링 가능한 시스템들을 카테고리별로 소개\n" +
                "3. 각 시스템별로 구체적인 명령어 예시 제공 (따옴표로 감싸서)\n" +
                "4. 추천하는 분석 시나리오나 조합 제안\n" +
                "5. 마지막에 \"위 예시를 참고하여 구체적으로 요청해주세요!\" 메시지 포함\n\n" +
                "응답은 마크다운 형식으로 작성하고, 이모지를 적절히 사용하여 친근하게 작성해주세요.";
    }

    /**
     * ★★★ 개선된 메소드: 구체적 분석용 프롬프트 구성 ★★★
     */
    private String buildPromptForSpecificAnalysis(String originalUserPrompt, Map<String, Object> preparedData) {
        StringBuilder prompt = new StringBuilder();

        // 시스템 프롬프트 추가
        prompt.append("당신은 시스템 모니터링 데이터 분석 전문가입니다. ");
        prompt.append("아래 데이터를 바탕으로 전문적이면서도 이해하기 쉬운 분석 리포트를 작성해주세요.\n\n");

        prompt.append("## 사용자 요청\n");
        prompt.append(originalUserPrompt).append("\n\n");

        prompt.append("## 분석 대상 시스템\n");
        if (preparedData.get("gatewayId") != null) {
            prompt.append("- **시스템 ID**: ").append(preparedData.get("gatewayId")).append("\n");
        }
        if (preparedData.get("measurement") != null) {
            prompt.append("- **측정 항목**: ").append(preparedData.get("measurement")).append("\n");
        }
        if (preparedData.get("location") != null) {
            prompt.append("- **위치**: ").append(preparedData.get("location")).append("\n");
        }

        // ★★★ 분석 기간 정보 개선 ★★★
        LocalDateTime startTime = (LocalDateTime) preparedData.get("startTime");
        LocalDateTime endTime = (LocalDateTime) preparedData.get("endTime");
        if (startTime != null && endTime != null) {
            long hours = java.time.Duration.between(startTime, endTime).toHours();
            prompt.append("- **분석 기간**: ").append(startTime.toLocalDate())
                    .append(" ~ ").append(endTime.toLocalDate())
                    .append(" (").append(hours).append("시간)\n");
        }

        prompt.append("- **수집 데이터**: ").append(preparedData.get("totalDataCount")).append("건\n\n");

        prompt.append("## 데이터 분석 결과\n");
        prompt.append(preparedData.get("summary")).append("\n\n");

        // ★★★ 샘플 데이터 표시 개선 ★★★
        if (preparedData.containsKey("rawDataSample")) {
            List<?> samples = (List<?>) preparedData.get("rawDataSample");
            if (samples != null && !samples.isEmpty()) {
                prompt.append("## 최근 데이터 샘플\n");
                samples.stream().limit(3).forEach(sample -> {
                    prompt.append("- ").append(sample.toString()).append("\n");
                });
                prompt.append("\n");
            }
        }

        prompt.append("## 요청사항\n");
        prompt.append("위 데이터를 바탕으로 다음 내용을 포함한 종합 분석 리포트를 마크다운 형식으로 작성해주세요:\n");
        prompt.append("1. **현재 상태 요약** - 핵심 지표와 전반적인 상태\n");
        prompt.append("2. **데이터 패턴 분석** - 시간대별 변화, 트렌드, 특이점\n");
        prompt.append("3. **성능 평가** - 정상/주의/경고 수준 판단\n");
        prompt.append("4. **문제점 및 개선사항** - 발견된 이슈와 해결 방안\n");
        prompt.append("5. **권장 조치** - 구체적인 액션 아이템\n");
        prompt.append("6. **추가 모니터링 제안** - 함께 확인하면 좋은 다른 메트릭\n\n");
        prompt.append("친근하고 전문적인 톤으로 작성해주세요.");

        return prompt.toString();
    }

    /**
     * preparedData에서 차트 데이터 추출 (기존과 동일)
     */
    @SuppressWarnings("unchecked")
    private List<ChartDataDto> extractChartsFromPreparedData(Map<String, Object> preparedData) {
        Object chartDataObj = preparedData.get("chartData");
        if (chartDataObj instanceof ChartDataDto) {
            ChartDataDto chartData = (ChartDataDto) chartDataObj;
            if (chartData.getLabels() != null && !chartData.getLabels().isEmpty() &&
                    chartData.getValues() != null && !chartData.getValues().isEmpty()) {
                return List.of(chartData);
            }
        }
        return Collections.emptyList();
    }

    /**
     * ★★★ 개선된 리포트 제목 생성 ★★★
     */
    private String buildReportTitle(Map<String, Object> preparedData, String requestReportType) {
        String measurement = (String) preparedData.get("measurement");
        String gatewayId = (String) preparedData.get("gatewayId");

        // 서비스 메트릭인 경우 더 친근한 제목 생성
        if (gatewayId != null && gatewayId.startsWith("javame-")) {
            String serviceName = getServiceDisplayName(gatewayId);
            if (measurement != null) {
                String metricDisplayName = getMetricDisplayName(measurement);
                return serviceName + " " + metricDisplayName + " 분석 리포트";
            } else {
                return serviceName + " 성능 분석 리포트";
            }
        }

        // 일반 시스템 메트릭인 경우
        if (measurement != null) {
            String metricDisplayName = getMetricDisplayName(measurement);
            return metricDisplayName + " 분석 리포트";
        } else if (gatewayId != null) {
            return gatewayId.toUpperCase() + " 시스템 리포트";
        } else if (requestReportType != null && !requestReportType.isBlank()) {
            return requestReportType + " 리포트";
        } else {
            return "시스템 모니터링 리포트";
        }
    }

    /**
     * ★★★ 새로운 메소드: 서비스 표시명 반환 ★★★
     */
    private String getServiceDisplayName(String gatewayId) {
        switch (gatewayId) {
            case "javame-auth": return "인증 서비스";
            case "javame-environment-api": return "환경 API";
            case "javame-frontend": return "프론트엔드";
            case "javame-gateway": return "게이트웨이";
            case "javame-member": return "회원 서비스";
            default: return gatewayId;
        }
    }

    /**
     * ★★★ 새로운 메소드: 메트릭 표시명 반환 ★★★
     */
    private String getMetricDisplayName(String measurement) {
        switch (measurement) {
            case "cpu_utilization_percent": return "CPU 사용률";
            case "memory_total_heap_used_bytes": return "힙 메모리";
            case "memory_old_gen_used_bytes": return "올드젠 메모리";
            case "gc_g1_young_generation_count": return "가비지 컬렉션";
            case "thread_active_count": return "활성 스레드";
            case "process_open_file_descriptors_count": return "파일 핸들러";
            case "usage_idle": return "CPU 유휴율";
            case "usage_system": return "시스템 CPU 사용률";
            case "usage_user": return "사용자 CPU 사용률";
            case "available_percent": return "메모리 가용률";
            case "used_percent": return "메모리 사용률";
            case "temp_input": return "온도";
            case "power_watts": return "전력 소비량";
            default: return measurement;
        }
    }

    /**
     * preparedData에서 날짜 추출 (기존과 동일)
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
     * ★★★ 개선된 필터 요약 생성 ★★★
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
            sb.append(", 측정항목=").append(getMetricDisplayName(measurement));
        }

        String gatewayId = (String) preparedData.get("gatewayId");
        if (gatewayId != null) {
            if (gatewayId.startsWith("javame-")) {
                sb.append(", 서비스=").append(getServiceDisplayName(gatewayId));
            } else {
                sb.append(", 시스템=").append(gatewayId.toUpperCase());
            }
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
