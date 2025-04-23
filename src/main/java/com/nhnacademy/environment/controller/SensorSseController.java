package com.nhnacademy.environment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.environment.dto.ResourceDataDto;
import com.nhnacademy.environment.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/environment/{companyDomain}")
public class SensorSseController {

    private final SensorDataService sensorDataService;

    private final ObjectMapper objectMapper;

    @GetMapping(value = "/sensor-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSensorData(
            @PathVariable String companyDomain,
            @RequestParam String measurement,
            @RequestParam(defaultValue = "60") int range,
            @RequestParam Map<String, String> allParams
    ) {
        allParams.remove("measurement");
        allParams.remove("range");

        SseEmitter emitter = new SseEmitter(0L); // 타임아웃 무한
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                while (true) {
                    Map<String, List<ResourceDataDto>> result =
                            sensorDataService.getSensorData(measurement, allParams, range);

                    emitter.send(SseEmitter.event()
                            .name("sensor-update")
                            .data(objectMapper.writeValueAsString(result)));

                    Thread.sleep(5000); // 5초마다 새로 데이터 푸시
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
