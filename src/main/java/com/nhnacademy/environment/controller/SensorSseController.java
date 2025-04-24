package com.nhnacademy.environment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.environment.dto.ResourceDataDto;
import com.nhnacademy.environment.dto.SensorDataDto;
import com.nhnacademy.environment.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    /**
     * SSE 방식으로 센서 데이터 스트리밍 + measurement 목록 전송
     */
    @GetMapping(value = "/sensor-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSensorData(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam(defaultValue = "180") int range,
            @RequestParam Map<String, String> allParams
    ) {
        allParams.remove("range");

        SseEmitter emitter = new SseEmitter(0L);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 초기 측정값 목록 전송
                emitter.send(SseEmitter.event()
                        .name("origin")
                        .data(objectMapper.writeValueAsString(sensorDataService.getMeasurementList())));

                // 실시간 센서 데이터 전송
                while (true) {
                    Map<String, List<SensorDataDto>> result =
                            sensorDataService.getSensorDataByOrigin(origin, allParams, range);

                    emitter.send(SseEmitter.event()
                            .name("sensor-update")
                            .data(objectMapper.writeValueAsString(result)));

                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 센서 필터용 드롭다운 데이터 제공
     */
    @GetMapping("/sensor-locations")
    public ResponseEntity<List<String>> getSensorLocations(
            @PathVariable String companyDomain,
            @RequestParam(defaultValue = "sensor_data") String origin
    ) {
        return ResponseEntity.ok(sensorDataService.getLocationList(origin, companyDomain));
    }

    @GetMapping("/sensor-buildings")
    public ResponseEntity<List<String>> getSensorBuildings(
            @PathVariable String companyDomain,
            @RequestParam(defaultValue = "sensor_data") String origin
    ) {
        return ResponseEntity.ok(sensorDataService.getBuildingList(origin, companyDomain));
    }

    @GetMapping("/sensor-places")
    public ResponseEntity<List<String>> getSensorPlaces(
            @PathVariable String companyDomain,
            @RequestParam(defaultValue = "sensor_data") String origin
    ) {
        return ResponseEntity.ok(sensorDataService.getPlaceList(origin, companyDomain));
    }

    @GetMapping("/sensor-deviceIds")
    public ResponseEntity<List<String>> getSensorDeviceIds(
            @PathVariable String companyDomain,
            @RequestParam(defaultValue = "sensor_data") String origin
    ) {
        return ResponseEntity.ok(sensorDataService.getDeviceIdList(origin, companyDomain));
    }

    @GetMapping("/sensor-companyDomains")
    public ResponseEntity<List<String>> getSensorCompanyDomains(
            @RequestParam(defaultValue = "sensor_data") String origin
    ) {
        return ResponseEntity.ok(sensorDataService.getCompanyDomainList(origin));
    }

    @GetMapping("/sensor-measurements")
    public ResponseEntity<List<String>> getSensorMeasurements(
            @PathVariable String companyDomain
    ) {
        return ResponseEntity.ok(sensorDataService.getMeasurementList());
    }

}
