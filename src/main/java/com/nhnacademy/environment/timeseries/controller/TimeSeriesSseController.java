package com.nhnacademy.environment.timeseries.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/environment/{companyDomain}")
@RequiredArgsConstructor
public class TimeSeriesSseController {

    private final TimeSeriesDataService timeSeriesDataService;
    private final ObjectMapper objectMapper;

    /**
     * 시계열 데이터를 SSE 방식으로 스트리밍합니다.
     * 프론트에서는 `/time-series-stream` 요청 후 `data:` 이벤트를 통해 수신.
     *
     * @param companyDomain 회사 도메인 (경로 파라미터)
     * @param origin         origin 값 (sensor_data, server_data 등)
     * @param range          시간 범위 (기본값: 180분)
     * @param allParams      measurement, location 등 필터 조건
     * @return SseEmitter 스트림 응답
     */
    @GetMapping(value = "/time-series-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTimeSeriesData(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam(defaultValue = "180") int range,
            @RequestParam Map<String, String> allParams
    ) {
        allParams.remove("range");
        allParams.remove("origin");

        SseEmitter emitter = new SseEmitter(0L); // timeout 없음
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                while (true) {
                    Map<String, List<TimeSeriesDataDto>> data =
                            timeSeriesDataService.getTimeSeriesData(origin, allParams, range);

                    emitter.send(SseEmitter.event()
                            .name("time-series-update")
                            .data(objectMapper.writeValueAsBytes(data), MediaType.APPLICATION_JSON));

                    Thread.sleep(30000); // 5분 간격
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        log.info("SSE 연결 요청 도착: origin={}, filters={}", origin, allParams);
        return emitter;
    }
}
