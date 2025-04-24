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
    @GetMapping(value = "/sensor-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8") // 백엔드에서 인코딩 후 넘겨야한대서 넣어봤지만 여전히 안됨
    public SseEmitter streamSensorData(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam(defaultValue = "720") int range,
            @RequestParam Map<String, String> allParams
    ) {
        allParams.remove("range");

        SseEmitter emitter = new SseEmitter(0L);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 초기 측정값 목록 전송
                emitter.send(SseEmitter.event()
                        .name("measurements")
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
     *
     * 드롭다운 메서드의 ResponseEntity<List<String>> 반환 구조를 하나의 공용 메서드로 사용하기 위한 메서드.
     * @param column
     * @param companyDomain
     * @param origin
     * @return
     */
    private ResponseEntity<List<String>> getDropdownResponse(String column, String companyDomain, String origin) {
        List<String> values = switch (column) {
            case "companyDomain" -> sensorDataService.getCompanyDomainList(origin);
            case "location", "building", "place", "deviceId", "origin" ->
                    sensorDataService.getTagValues(column, Map.of("origin", origin, "companyDomain", companyDomain));
            default -> List.of();
        };
        return ResponseEntity.ok(values);
    }


    /**
     * 센서 필터용 드롭다운 데이터 제공
     * getSensorMeasurements() 는 InfluxDB의 스키마 정보를 사용하는 별도의 쿼리이기 때문에 단독으로 사용함
     * getSensorOrigins() 를 통해 사용자가 sensor_data 등 원하는 센서를 선택해야 하기 때문에 단독으로 사용함
     * 이 구조를 유지하면 JS 에서 "sensor-companyDomains" 와 같이 API 만 바꿔가며 요청 가능함.
     */
    @GetMapping("/sensor-measurements")
    public ResponseEntity<List<String>> getSensorMeasurements(
            @PathVariable String companyDomain
    ) {
        return ResponseEntity.ok(sensorDataService.getMeasurementList());
    }

    @GetMapping("/sensor-origins")
    public ResponseEntity<List<String>> getSensorOrigins(
            @PathVariable String companyDomain
    ) {
        return ResponseEntity.ok(sensorDataService.getOriginList(companyDomain));
    }


    @GetMapping("/sensor-locations")
    public ResponseEntity<List<String>> getSensorLocations(
            @PathVariable String companyDomain,
            @RequestParam(defaultValue = "sensor_data") String origin
    ) {
        return getDropdownResponse("location", companyDomain, origin);
    }

    @GetMapping("/sensor-buildings")
    public ResponseEntity<List<String>> getSensorBuildings(
            @PathVariable String companyDomain,
            @RequestParam(defaultValue = "sensor_data") String origin
    ) {
        return getDropdownResponse("building", companyDomain, origin);
    }

    @GetMapping("/sensor-places")
    public ResponseEntity<List<String>> getSensorPlaces(
            @PathVariable String companyDomain,
            @RequestParam(defaultValue = "sensor_data") String origin
    ) {
        return getDropdownResponse("place", companyDomain, origin);
    }

    @GetMapping("/sensor-deviceIds")
    public ResponseEntity<List<String>> getSensorDeviceIds(
            @PathVariable String companyDomain,
            @RequestParam(defaultValue = "sensor_data") String origin
    ) {
        return getDropdownResponse("deviceId", companyDomain, origin);
    }

    // CompanyDomainController 와 역할을 분리하기 위해 주석처리
//    @GetMapping("/sensor-companyDomains")
//    public ResponseEntity<List<String>> getSensorCompanyDomains(
//            @RequestParam(defaultValue = "sensor_data") String origin
//    ) {
//        return getDropdownResponse("companyDomain", null, origin);
//    }
}
