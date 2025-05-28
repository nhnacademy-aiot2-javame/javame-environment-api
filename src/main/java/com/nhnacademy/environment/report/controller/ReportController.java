package com.nhnacademy.environment.report.controller;

import com.nhnacademy.environment.report.dto.ReportRequestDto;
import com.nhnacademy.environment.report.dto.ReportResponseDto;
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
    public ResponseEntity<ReportResponseDto> generateReport(@RequestBody ReportRequestDto reportRequestDto) {
        ReportResponseDto reportResponseDto = reportService.generateReport(reportRequestDto);
        return ResponseEntity.ok(reportResponseDto);
    }
}
