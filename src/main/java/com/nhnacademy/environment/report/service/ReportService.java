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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class ReportService {

    private final TimeSeriesDataService influxQueryService;
    private final GeminiClient geminiClient;

    // 프롬프트 최대 길이 (예: 4000자) - 필요에 따라 조절
    private static final int MAX_PROMPT_LENGTH = 4000;
    private static final String DEFAULT_TIMEZONE = "Asia/Seoul"; // 시간대 상수 추가

    public ReportResponseDto generateReport(ReportRequestDto request) {
        log.info("리포트 생성 요청 수신: userPrompt='{}', reportType='{}'", request.getUserPrompt(), request.getReportType());

        // 1. ReportRequestDto 필드 기본값 처리
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now().minusWeeks(1);
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : LocalDate.now();
        List<String> targetOrigins = (request.getTargetOrigins() != null && !request.getTargetOrigins().isEmpty())
                ? request.getTargetOrigins()
                : getDefaultOrigins();
        List<String> measurements = (request.getMeasurements() != null && !request.getMeasurements().isEmpty())
                ? request.getMeasurements()
                : getDefaultMeasurements();
        List<String> targetLocations = request.getTargetLocations() != null ? request.getTargetLocations() : getDefaultLocations();
        List<String> deviceIds = request.getDeviceIds() != null ? request.getDeviceIds() : getDefaultDeviceIds();
        List<String> gatewayIds = request.getGatewayIds() != null ? request.getGatewayIds() : getDefaultGatewayIds();
        String reportTypeDisplay = request.getReportType() != null ? request.getReportType() : "종합 분석";
        String userPrompt = request.getUserPrompt(); // 사용자 프롬프트는 그대로 사용

        // 유효성 검사 (기본값이 설정된 후)
        if (targetOrigins.isEmpty()) {
            log.error("처리할 대상 Origin이 없습니다. 기본 Origin 설정을 확인하거나 요청 시 명시해주세요.");
            return new ReportResponseDto("오류: 분석 대상 Origin이 지정되지 않았습니다.", Collections.emptyList(), "오류 리포트", startDate, endDate, ZonedDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).toString(), "오류: Origin 누락");
        }
        if (measurements.isEmpty()) {
            log.error("처리할 측정 항목이 없습니다. 기본 측정 항목 설정을 확인하거나 요청 시 명시해주세요.");
            return new ReportResponseDto("오류: 분석할 측정 항목이 지정되지 않았습니다.", Collections.emptyList(), "오류 리포트", startDate, endDate, ZonedDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).toString(), "오류: 측정항목 누락");
        }

        // 2. 필터 조합 (기본값이 적용된 필드 사용)
        Map<String, String> filters = new HashMap<>();
        if (!targetLocations.isEmpty()) {
            filters.put("location", String.join(",", targetLocations));
        }
        if (!deviceIds.isEmpty()) {
            filters.put("deviceId", String.join(",", deviceIds));
        }
        if (!gatewayIds.isEmpty()) {
            filters.put("gatewayId", String.join(",", gatewayIds));
        }

        List<ChartDataDto> charts = new ArrayList<>();
//        List<Map<String, Object>> table = new ArrayList<>();

        // 3. 시계열 데이터 조회 및 차트/테이블 데이터 생성
        log.info("데이터 조회 시작 - Origins: {}, Measurements: {}", targetOrigins, measurements);
        for (String origin : targetOrigins) {
            for (String measurement : measurements) {
                log.debug("Origin: {}, Measurement: {} 데이터 조회 중...", origin, measurement);
                List<TimeSeriesDataDto> rawData = influxQueryService.getRawTimeSeriesData(
                        origin,
                        measurement,
                        filters, // origin은 루프 변수이므로, filters에는 location, deviceId 등만 포함됨
                        startDate.atStartOfDay(),
                        endDate.atTime(LocalTime.MAX)
                );

//                // 테이블용 데이터 적재
//                if (rawData != null) { // rawData가 null일 수 있음을 방지
//                    for (TimeSeriesDataDto dto : rawData) {
//                        Map<String, Object> row = new HashMap<>();
//                        row.put("time", dto.getTime() != null ? dto.getTime().toString() : "N/A");
//                        row.put("location", dto.getLocation() != null ? dto.getLocation() : "N/A");
//                        row.put("value", dto.getValue()); // 값은 필수라고 가정
//                        row.put("measurement", dto.getMeasurement() != null ? dto.getMeasurement() : "N/A");
//                        if (dto.getTags() != null) {
//                            row.putAll(dto.getTags());
//                        }
//                        table.add(row);
//                    }
//                } else {
//                    log.warn("Origin: {}, Measurement: {}에 대한 rawData가 null입니다.", origin, measurement);
//                }


                // 차트 데이터 조회
                ChartDataDto chart = influxQueryService.getAggregatedChartDataForPeriod(
                        measurement,
                        "value", // 집계할 필드
                        filters, // 여기에도 origin은 포함되지 않음 (TimeSeriesDataService 내부에서 처리 가정 또는 filters에 origin 추가)
                        startDate.atStartOfDay(),
                        endDate.atTime(LocalTime.MAX),
                        determineAggregationInterval(startDate, endDate), // 집계 간격 동적 결정
                        DateTimeFormatter.ofPattern("MM-dd HH:mm") // X축 포맷
                );
                if (chart != null) { // chart가 null일 수 있음을 방지
                    if (chart.getTitle() == null || chart.getTitle().isBlank()) { // 차트 제목이 없다면 기본 제목 설정
                        chart.setTitle(String.format("%s (%s) 추이", measurement, origin));
                    }
                    charts.add(chart);
                } else {
                    log.warn("Origin: {}, Measurement: {}에 대한 chartData가 null입니다.", origin, measurement);
                }
            }
        }
//        log.info("데이터 조회 완료. 테이블 항목 수: {}, 차트 수: {}", table.size(), charts.size());


        // 4. Gemini AI 프롬프트 생성 및 요약 요청
        //    프롬프트 생성에는 기본값이 적용된 정보를 사용
        ReportRequestDto internalRequestDto = new ReportRequestDto(reportTypeDisplay, startDate, endDate, targetOrigins, targetLocations, deviceIds, gatewayIds, measurements, userPrompt);
        String prompt = buildPromptForGemini(internalRequestDto, Collections.emptyList());

        if (prompt.length() > MAX_PROMPT_LENGTH) {
            int originalLength = prompt.length();
            prompt = truncatePrompt(prompt, MAX_PROMPT_LENGTH);
            log.warn("프롬프트 길이 초과로 일부 내용이 잘렸습니다. 원본 길이: {}, 제한 적용 후 길이: {}", originalLength, prompt.length());
        }

        String summary = "";
        if (geminiClient != null) { // 데이터가 있을 때만 AI 요약 시도
            log.info("Gemini API 호출 시작...");
            summary = geminiClient.generateSummary(prompt);
            log.info("Gemini API 요약 수신 완료.");
        } else if (geminiClient == null) {
            log.warn("GeminiClient가 설정되지 않아 AI 요약을 생성할 수 없습니다.");
            summary = "AI 요약 기능을 사용할 수 없습니다. (관리자에게 문의)";
        } else { // 데이터가 없을 경우
            log.info("분석할 데이터가 없어 AI 요약을 생성하지 않습니다.");
            summary = "분석할 데이터가 부족하여 AI 요약을 생성하지 않았습니다. 조회 조건을 확인해주세요.";
        }

        // 5. 결과 반환
        String reportTitle = String.format("%s AI 분석 리포트 (%s ~ %s)",
                determineReportSubject(internalRequestDto), // 제목 생성용으로도 내부 DTO 사용
                startDate.toString(),
                endDate.toString());

        String finalFilterSummary = buildFilterSummary(internalRequestDto);

        return new ReportResponseDto(
                summary,
                charts,
                reportTitle,
                startDate,
                endDate,
                ZonedDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).toString(),
                finalFilterSummary
        );
    }

    // 기본 Origin 목록 반환 (실제 구현 필요)
    private List<String> getDefaultOrigins() {
        // TODO: DB에서 자주 사용되는 Origin 목록을 가져오거나, 설정 파일 등에서 관리
        return List.of("server_data"); // 예시 기본값
    }

    // 기본 측정항목 목록 반환 (실제 구현 필요)
    private List<String> getDefaultMeasurements() {
        // TODO: 가장 중요하거나 일반적인 측정항목 목록 설정
        return List.of("temp_input"); // 예시 기본값
    }

    // 기본 측정항목 목록 반환 (실제 구현 필요)
    private List<String> getDefaultLocations() {
        // TODO: 가장 중요하거나 일반적인 측정항목 목록 설정
        return List.of("server_resource_data");
    }

    private List<String> getDefaultDeviceIds() {
        // TODO: 가장 중요하거나 일반적인 측정항목 목록 설정
        return List.of("192.168.71.74");
    }

    private List<String> getDefaultGatewayIds() {
        // TODO: 가장 중요하거나 일반적인 측정항목 목록 설정
        return List.of("sensors");
    }




    // 집계 간격 동적 결정 로직 (예시)
    private String determineAggregationInterval(LocalDate startDate, LocalDate endDate) {
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween <= 1) return "10m"; // 하루 이내: 10분 간격
        if (daysBetween <= 7) return "1h";  // 일주일 이내: 1시간 간격
        if (daysBetween <= 31) return "6h"; // 한 달 이내: 6시간 간격
        return "1d"; // 그 이상: 하루 간격
    }


    private String buildPromptForGemini(ReportRequestDto request, List<Map<String, Object>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 환경 측정 데이터 리포트를 위한 요약입니다.\n");
        sb.append("분석 기간: ").append(request.getStartDate()).append(" 부터 ").append(request.getEndDate()).append(" 까지\n");
        if (request.getMeasurements() != null && !request.getMeasurements().isEmpty()) {
            sb.append("주요 측정 항목: ").append(String.join(", ", request.getMeasurements())).append("\n");
        }
        if (request.getTargetOrigins() != null && !request.getTargetOrigins().isEmpty()) {
            sb.append("대상 Origin: ").append(String.join(", ", request.getTargetOrigins())).append("\n");
        }
        // 필요시 다른 필터 정보도 추가 (locations, deviceIds, gatewayIds)

        if (request.getUserPrompt() != null && !request.getUserPrompt().isBlank()) {
            sb.append("사용자의 특별 요청 사항: ").append(request.getUserPrompt()).append("\n");
        }
        sb.append("\n아래는 수집된 데이터의 일부 예시입니다 (최대 5개):\n");

        if (data.isEmpty()) {
            sb.append("제공된 데이터 샘플이 없습니다.\n");
        } else {
            for (int i = 0; i < Math.min(5, data.size()); i++) {
                Map<String, Object> row = data.get(i);
                sb.append(String.format("- 시간: %s, 위치: %s, 측정항목: %s, 값: %s",
                        row.get("time"), row.get("location"), row.get("measurement"), row.get("value")));
                // 다른 주요 태그 정보도 추가 가능
                sb.append("\n");
            }
        }

        sb.append("\n위 정보와 데이터 예시를 바탕으로, 다음 지침에 따라 분석 요약 보고서를 작성해주세요:\n");
        sb.append("1. 전반적인 데이터 경향 및 주요 패턴을 설명해주세요.\n");
        sb.append("2. 주목할 만한 최고/최저 수치나 급격한 변화가 있었다면 언급하고, 가능한 원인을 추론해주세요.\n");
        sb.append("3. 사용자 요청 사항이 있다면, 해당 내용에 초점을 맞춰 답변해주세요.\n");
        sb.append("4. 결론적으로 시스템/환경 상태에 대한 종합적인 의견을 제시해주세요.\n");
        sb.append("5. 답변은 마크다운 형식을 사용하여 가독성을 높여주세요. (예: 제목, 부제목, 목록, 강조 등)\n");
        sb.append("6. 데이터가 부족하여 분석이 어려운 부분이 있다면 명확히 언급해주세요.\n");
        return sb.toString();
    }

    private String truncatePrompt(String prompt, int maxLength) {
        if (prompt.length() <= maxLength) {
            return prompt;
        }
        String truncated = prompt.substring(0, maxLength);
        int lastPeriodIndex = Math.max(
                Math.max(truncated.lastIndexOf('.'), truncated.lastIndexOf('!')),
                truncated.lastIndexOf('?')
        );
        return (lastPeriodIndex > 0) ? truncated.substring(0, lastPeriodIndex + 1) : truncated;
    }

    // ReportResponseDto에 포함될 필터 요약 문자열 생성
    private String buildFilterSummary(ReportRequestDto request) {
        List<String> summaries = new ArrayList<>();
        if (request.getTargetOrigins() != null && !request.getTargetOrigins().isEmpty()) {
            summaries.add("Origin: " + String.join(", ", request.getTargetOrigins()));
        }
        if (request.getMeasurements() != null && !request.getMeasurements().isEmpty()) {
            summaries.add("측정항목: " + String.join(", ", request.getMeasurements()));
        }
        if (request.getTargetLocations() != null && !request.getTargetLocations().isEmpty()) {
            summaries.add("Location: " + String.join(", ", request.getTargetLocations()));
        }
        // deviceIds, gatewayIds 등도 필요시 추가
        if (request.getUserPrompt() != null && !request.getUserPrompt().isBlank()) {
            summaries.add("사용자 요청: \"" + request.getUserPrompt().substring(0, Math.min(request.getUserPrompt().length(), 30)) + "...\"");
        }
        return summaries.isEmpty() ? "전체 데이터 (기본 설정)" : String.join(" | ", summaries);
    }

    // 리포트 제목에 사용될 주제 결정 (간단화)
    private String determineReportSubject(ReportRequestDto request) {
        if (request.getMeasurements() != null && !request.getMeasurements().isEmpty()) {
            return request.getMeasurements().get(0) + " 등"; // 첫 번째 측정항목 + "등"
        }
        if (request.getTargetOrigins() != null && !request.getTargetOrigins().isEmpty()) {
            return request.getTargetOrigins().get(0) + " 대상";
        }
        return "환경 데이터";
    }
}
