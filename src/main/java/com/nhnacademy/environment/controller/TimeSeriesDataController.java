package com.nhnacademy.environment.controller;

import com.nhnacademy.environment.config.annotation.CompanyDomainContext;
import com.nhnacademy.environment.config.annotation.HasRole;
import com.nhnacademy.environment.config.annotation.NormalizeCompanyDomain;
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
     * @param range         범위 (기본 180분)
     * @param allParams     measurement, location 등 필터용 파라미터
     * @return 측정값별 시계열 데이터 Map
     */
    @NormalizeCompanyDomain
    @GetMapping("/time-series")
//    //@HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
    public Map<String, List<TimeSeriesDataDto>> getTimeSeriesData(
            @PathVariable String companyDomain,
            @RequestParam(defaultValue = "180") int range,
            @RequestParam Map<String, String> allParams
    ) {
        allParams.remove("range");
        allParams.put("companyDomain", CompanyDomainContext.get());

        log.info("/time-series 요청 도착 - companyDomain: {}, range: {}, filters: {}", CompanyDomainContext.get(), range, allParams);
        return timeSeriesDataService.getTimeSeriesData(allParams, range);
    }


    /**
     * origin 목록 조회. (ex: sensor_data, server_data)
     * @param companyDomain 회사 도메인
     * @return origin 목록
     */
    @NormalizeCompanyDomain
    @GetMapping("/origins")
    //@HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
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
     * @param filters        데이터 출처
     * @return 해당 태그의 고유 값 리스트
     */
    @NormalizeCompanyDomain
    @GetMapping("/dropdown/{tag}")
    //@HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
    public List<String> getTagDropdown(
            @PathVariable String companyDomain,
            @PathVariable String tag,
            @RequestParam Map<String, String> filters
    ) {
        filters.put("companyDomain", CompanyDomainContext.get());
        return timeSeriesDataService.getTagValues(tag, filters);
    }

    /**
     * 측정값(_measurement) 목록 조회.
     * origin, location, companyDomain 필터에 따라 InfluxDB 에서 중복 제거된 측정 항목 목록을 반환합니다.
     *
     * @param companyDomain 회사 도메인 (실제로 사용하진 않지만 IDE 경고를 무시하기 위한 용도로 사용)
     * @param filters        companyDomain 을 제외한 influxdb 태그
     * @return 중복 제거된 _measurement 리스트 (예: usage_idle, battery 등)
     */
    @NormalizeCompanyDomain
    @GetMapping("/measurements")
    //@HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
    public List<String> getMeasurements(
            @PathVariable String companyDomain,
            @RequestParam Map<String, String> filters
    ) {
        filters.put("companyDomain", CompanyDomainContext.get());
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
    @NormalizeCompanyDomain
    @GetMapping("/chart/type/{sensor}")
    //@HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
    public ChartDataDto getChartDataForSensor(
            @PathVariable String companyDomain,
            @PathVariable String sensor,
            @RequestParam String origin
    ) {
        Map<String, String> filters = new HashMap<>();
        filters.put("origin", origin);
        filters.put("companyDomain", CompanyDomainContext.get());

        log.debug("chart called: companyDomain={}, origin={}", companyDomain, origin);
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
    @NormalizeCompanyDomain
    @GetMapping("/chart/pie")
    //@HasRole({"ROLE_ADMIN", "ROLE_OWNER", "ROLE_USER"})
    public ChartDataDto getPieChartData(
            @PathVariable String companyDomain,
            @RequestParam String origin
    ) {
        Map<String, String> filters = new HashMap<>();
        filters.put("origin", origin);
        filters.put("companyDomain", CompanyDomainContext.get());

        log.info("pie called: companyDomain={}, origin={}", CompanyDomainContext.get(), origin);
        return timeSeriesDataService.getPieChartData(filters);
    }

    @NormalizeCompanyDomain
    @GetMapping("/current") // 새로운 엔드포인트 경로
    // @HasRole(...) // 필요시 권한 설정
    public TimeSeriesDataDto getCurrentValue(
            @PathVariable String companyDomain,
            @RequestParam String origin,
            @RequestParam String location,
            @RequestParam("_measurement") String measurement, // JS의 getCurrentSensorValue와 파라미터명 일치
            @RequestParam(value = "_field", required = false) String field
    ) {
        Map<String, String> filters = new HashMap<>();
        filters.put("origin", origin);
        filters.put("location", location);
        filters.put("_measurement", measurement);
        filters.put("companyDomain", CompanyDomainContext.get()); // 서비스에서 필요하면 추가

        if (field != null && !field.isEmpty()) {
            filters.put("_field", field);
        }

        log.info("/current 요청 - company: {}, origin: {}, location: {}, measurement: {}, field: {}",
                companyDomain, origin, location, measurement, field);

        // TimeSeriesDataService에 최신 값 하나만 가져오는 메소드 호출
        TimeSeriesDataDto latestData = timeSeriesDataService.getLatestTimeSeriesData(filters);

        if (latestData == null) {
            log.warn("/current 요청에 대한 데이터 없음");
            // 적절한 응답 처리 (예: ResponseEntity.notFound().build(); 또는 빈 객체 반환)
            // 여기서는 간단히 null을 반환하거나, 클라이언트에서 처리할 수 있도록 빈 DTO 반환
            return null; // 또는 new TimeSeriesDataDto(); // 클라이언트에서 null 체크 필요
        }
        return latestData;
    }
}