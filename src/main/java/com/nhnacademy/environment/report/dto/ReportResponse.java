package com.nhnacademy.environment.report.dto;

import com.nhnacademy.environment.timeseries.dto.ChartDataDto; // ChartDataDto는 그대로 사용
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResponse {

    // AI가 생성한 최종 요약 텍스트 (마크다운 형식 등)
    private String summaryText;

    // 시각화를 위한 차트 데이터 리스트
    // Function Calling을 통해 조회된 데이터를 바탕으로 생성됨
    private List<ChartDataDto> chartVisualizations;

    // 리포트 전체 제목 (예: "지난 주 서버실 CPU 온도 분석 리포트")
    private String reportOverallTitle;

    // AI가 분석 대상으로 삼은 (또는 사용자가 명시한) 실제 기간
    private LocalDateTime reportPeriodStart;
    private LocalDateTime reportPeriodEnd;

    private LocalDateTime generatedAt;

    // AI가 어떤 기준으로 분석했는지 알려주는 요약 (선택적)
    // 예: "기간: 2025-05-19~2025-05-25, 대상: 서버실 CPU 온도"
    private String filterCriteriaSummary;

}
