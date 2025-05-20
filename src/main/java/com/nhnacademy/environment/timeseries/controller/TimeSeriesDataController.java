package com.nhnacademy.environment.timeseries.controller;

import com.nhnacademy.environment.config.annotation.HasRole;
import com.nhnacademy.environment.timeseries.dto.ChartDataDto;
import com.nhnacademy.environment.timeseries.dto.TimeSeriesDataDto;
import com.nhnacademy.environment.timeseries.service.TimeSeriesDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/environment/{companyDomain}")
@RequiredArgsConstructor
public class TimeSeriesDataController {

    /**
     * TimeSeriesDataService 를 받습니다.
     */
    private final TimeSeriesDataService timeSeriesDataService;

    /**
     * 특정 origin 의 시계열 데이터를 필터 기준으로 조회.
     *
     * @param companyDomain 회사 도메인
     * @param origin        origin(sensor_data, server_data 등)
     * @param range         범위 (기본 180분)
     * @param allParams     measurement, location 등 필터용 파라미터
     * @return 측정값별 시계열 데이터 Map
     */
    @GetMapping("/time-series")
    @HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
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
     * origin 목록 조회. (ex: sensor_data, server_data)
     * @param companyDomain 회사 도메인
     * @return origin 목록
     */
    @GetMapping("/origins")
    @HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
    public List<String> getOrigins(
            @PathVariable String companyDomain
    ) {
        return timeSeriesDataService.getOriginList(companyDomain);
    }

    /**
     * tag(location, building, place, device_id 등) 기준으로 드롭다운에 사용할 값 목록을 조회합니다.
     *
     * @param companyDomain 회사 도메인
     * @param tag           조회할 태그 명 (예: location)
     * @param origin        데이터 출처
     * @return 해당 태그의 고유 값 리스트
     */
    @GetMapping("/dropdown/{tag}")
    @HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
    public List<String> getTagDropdown(
            @PathVariable String companyDomain,
            @PathVariable String tag,
            @RequestParam String origin
    ) {
        return timeSeriesDataService.getTagValues(tag, Map.of("origin", origin, "companyDomain", companyDomain));
    }

    /**
     * 측정값(_measurement) 목록 조회.
     * origin, location, companyDomain 필터에 따라 InfluxDB에서 중복 제거된 측정 항목 목록을 반환합니다.
     *
     * @param companyDomain 회사 도메인
     * @param origin        데이터 출처 (예: sensor_data, server_data)
     * @param location      선택적 위치 필터 (예: cpu, memory 등)
     * @return 중복 제거된 _measurement 리스트 (예: usage_idle, battery 등)
     */
    @GetMapping("/measurements")
    @HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
    public List<String> getMeasurements(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam String location
    ) {
        Map<String, String> filters = new HashMap<>();
        filters.put("origin", origin);
        filters.put("companyDomain", companyDomain);
        filters.put("location", location);

        return timeSeriesDataService.getMeasurementList(filters);
    }

    /**
     * 단일 센서 기준의 라인 차트 데이터 조회.
     * 특정 origin 내에서 sensor 타입(예: temperature)의 시계열 데이터를 조회합니다.
     *
     * @param companyDomain 회사 도메인
     * @param sensor        측정 항목 이름 (예: temperature)
     * @param origin        데이터 출처
     * @return 차트에 사용할 시계열 데이터 DTO
     */
    @GetMapping("/chart/type/{sensor}")
    @HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
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
     * 측정값 분포 기반의 파이 차트 데이터 조회.
     * 주어진 origin 내에서 측정값 분포를 파이 차트 형식으로 반환합니다.
     *
     * @param companyDomain 회사 도메인
     * @param origin        데이터 출처
     * @return 파이 차트에 사용할 데이터 DTO
     */
    @GetMapping("/chart/pie")
    @HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
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
