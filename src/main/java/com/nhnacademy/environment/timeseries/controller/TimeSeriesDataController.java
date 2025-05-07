package com.nhnacademy.environment.timeseries.controller;

import com.nhnacademy.environment.timeseries.dto.ChartDataDto;
import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/environment/{companyDomain}")
@RequiredArgsConstructor
public class TimeSeriesDataController {

    private final TimeSeriesDataService timeSeriesDataService;

    /**
     * 특정 origin의 시계열 데이터를 필터 기준으로 조회
     * @param companyDomain 회사 도메인
     * @param origin origin(sensor_data, server_data 등)
     * @param range 범위 (기본 180분)
     * @param allParams measurement, location 등 필터용 파라미터
     * @return 측정값별 시계열 데이터 Map
     */
    @GetMapping("/time-series")
    public Map<String, List<TimeSeriesDataDto>> getTimeSeriesData(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam(defaultValue = "180") int range,
            @RequestParam Map<String, String> allParams
    ) {
        allParams.remove("range");
        allParams.remove("origin");

        log.info("/time-series 요청 도착 - origin: {}, range: {}, filters: {}", origin, range, allParams);
        return timeSeriesDataService.getTimeSeriesData(origin, allParams, range);
    }

    /**
     * origin 목록 조회 (ex: sensor_data, server_data)
     */
    @GetMapping("/origins")
    public List<String> getOrigins(
            @PathVariable String companyDomain
    ) {
        return timeSeriesDataService.getOriginList(companyDomain);
    }

    /**
     * tag 기반 드롭다운 리스트 조회 (location, building, place, device_id 등)
     */
    @GetMapping("/dropdown/{tag}")
    public List<String> getTagDropdown(
            @PathVariable String companyDomain,
            @PathVariable String tag,
            @RequestParam String origin
    ) {
        return timeSeriesDataService.getTagValues(tag, Map.of("origin", origin, "companyDomain", companyDomain));
    }

    /**
     * measurement 목록 조회 (origin + location 기반)
     */
    @GetMapping("/measurements")
    public List<String> getMeasurements(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam(required = false) String location
    ) {
        Map<String, String> filters = new HashMap<>();
        filters.put("origin", origin);
        filters.put("companyDomain", companyDomain);
        if (location != null) {
            filters.put("location", location);
        }
        return timeSeriesDataService.getMeasurementList(filters);
    }

    /**
     * 단일 측정값 기반의 라인 차트 데이터 조회
     * 예: /environment/nhnacademy/chart/type/temperature?origin=sensor_data
     */
    @GetMapping("/chart/type/{sensor}")
    public ChartDataDto getChartDataForSensor(
            @PathVariable String companyDomain,
            @PathVariable String sensor,
            @RequestParam String origin
    ) {
        Map<String, String> filters = new HashMap<>();
        filters.put("origin", origin);
        filters.put("companyDomain", companyDomain);

        return timeSeriesDataService.getChartData(sensor, "value", filters, 60);
    }

    /**
     * 측정값 분포 기반의 파이 차트 데이터 조회
     * 예: /environment/nhnacademy/chart/pie?origin=sensor_data
     */
    @GetMapping("/chart/pie")
    public ChartDataDto getPieChartData(
            @PathVariable String companyDomain,
            @RequestParam String origin
    ) {
        Map<String, String> filters = new HashMap<>();
        filters.put("origin", origin);
        filters.put("companyDomain", companyDomain);

        return timeSeriesDataService.getPieChartData(filters);
    }
}
