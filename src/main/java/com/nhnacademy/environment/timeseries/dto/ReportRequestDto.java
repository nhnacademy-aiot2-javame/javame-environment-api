package com.nhnacademy.environment.timeseries.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
public class ReportRequestDto {

    private String reportType;

    private LocalDate startDate;

    private LocalDate endDate;

    private List<String> targetOrigins;

    private List<String> targetLocations;

    private List<String> deviceIds;

    private List<String> gatewayIds;

    private List<String> measurements;

    private String userPrompt;

}
