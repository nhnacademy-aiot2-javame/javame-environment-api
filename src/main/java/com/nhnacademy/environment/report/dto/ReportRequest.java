package com.nhnacademy.environment.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {

    // 사용자의 프롬프트
    private String userPrompt;

    // 리포트의 성격 및 형태
    private String reportType;

}
