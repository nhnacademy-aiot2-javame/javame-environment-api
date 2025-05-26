package com.nhnacademy.environment.report.dto;

import com.nhnacademy.environment.timeseries.dto.ChartDataDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponseDto {

    private String summaryText;

    private List<ChartDataDto> chartVisualizations;

//    /**
//     * 표 형태로 보여줄 상세 데이터 리스트 (선택적)
//     * 각 Map은 테이블 행을 나타내며, Key는 컬럼명, Value는 셀 값
//     */
//    private List<Map<String, Object>> detailedMetricsTable;

    private String reportOverallTitle;

    private LocalDate reportPeriodStart;

    private LocalDate reportPeriodEnd;

    private String generatedAt;

    private String filterCriteriaSummary;

}
