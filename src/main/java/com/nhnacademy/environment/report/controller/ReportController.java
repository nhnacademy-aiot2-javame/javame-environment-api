package com.nhnacademy.environment.report.controller;

import com.nhnacademy.environment.report.dto.ReportRequest;
import com.nhnacademy.environment.report.dto.ReportResponse;
import com.nhnacademy.environment.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/environment/reports")
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/generate")
    public ResponseEntity<ReportResponse> generateReport(@RequestBody ReportRequest reportRequest) {
        ReportResponse reportResponse = reportService.generateReport(reportRequest);
        return ResponseEntity.ok(reportResponse);
    }
}
