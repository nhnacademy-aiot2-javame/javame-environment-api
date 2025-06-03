package com.nhnacademy.environment.timeseries.service;

import com.nhnacademy.environment.timeseries.domain.AverageData;
import com.nhnacademy.environment.timeseries.repository.AverageDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * InfluxDB 에서 수집된 시계열 데이터를 기반으로
 * 하루 단위 평균값을 계산하여 MySQL 에 저장하는 배치 서비스입니다.
 * <p>
 * 매일 00:10에 실행되어 전날 데이터의 평균을 계산하고 저장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSeriesAverageBatchService {

    /**
     * 평균 계산을 위한 서비스.
     * origin, measurement, 필터 범위를 기준으로 평균값을 계산합니다.
     */
    private final TimeSeriesAverageService averageService;

    /**
     * 시계열 데이터 조회 서비스.
     * 태그 목록(companyDomain, origin, measurement 등)을 조회할 수 있습니다.
     */
    private final TimeSeriesDataService timeSeriesDataService;

    /**
     * 평균값 저장용 JPA Repository.
     */
    private final AverageDataRepository averageDataRepository;


    /**
     * 매일 00시 10분에 실행되어 전날의 평균값을 저장합니다.
     * <p>
     * InfluxDB의 원시 데이터를 기준으로 전날(00:00 ~ 23:59)의 평균값을 계산하며,
     * 계산된 결과는 MySQL 의 average_data 테이블에 저장됩니다.
     */
    @Scheduled(cron = "0 10 0 * * *")
    public void saveDailyAverage() {
        log.info("하루 평균 저장 시작");

        LocalDate targetDate = LocalDate.now().minusDays(1);
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        saveAverageRangeAllDomains(start, end, targetDate, false, "[운영]");
    }

    /**
     * 주어진 기간(start ~ end)에 대해 모든 회사 도메인(companyDomain)의
     * origin 및 measurement 별 평균값을 계산하고 저장합니다.
     *
     * @param start      평균 계산 시작 시간
     * @param end        평균 계산 종료 시간
     * @param averageDate 결과 기록용 기준 날짜
     * @param windowed   슬라이딩 윈도우 평균 여부 (false = 전체 범위 평균)
     * @param logPrefix  로그 식별용 접두사
     */
    private void saveAverageRangeAllDomains(LocalDateTime start,
                                            LocalDateTime end,
                                            LocalDate averageDate,
                                            boolean windowed,
                                            String logPrefix) {

        List<String> companyDomains = timeSeriesDataService.getTagValues("companyDomain", Map.of());

        for (String companyDomain : companyDomains) {
            List<String> origins = timeSeriesDataService.getOriginList(companyDomain);

            for (String origin : origins) {
                Map<String, String> filters = new HashMap<>();
                filters.put("companyDomain", companyDomain);
                filters.put("origin", origin);

                List<String> measurements = timeSeriesDataService.getMeasurementList(filters);

                for (String measurement : measurements) {
                    Double avg = averageService.getAverageSensorValue(origin, measurement, filters, null, start, end, windowed);

                    if (avg != null) {
                        AverageData entity = AverageData.builder()
                                .sensorType(origin)
                                .companyDomain(companyDomain)
                                .measurement(measurement)
                                .field("value")
                                .averageValue(avg)
                                .averageDate(averageDate)
                                .averageStart(start)
                                .averageEnd(end)
                                .build();

                        averageDataRepository.save(entity);
                        log.debug("쿼리 범위: {} ~ {}", start, end);
                        log.info("{} 저장 완료: {}", logPrefix, entity);
                    } else {
                        log.warn("{} 평균 없음 - domain={}, origin={}, measurement={}, filters={}",
                                logPrefix, companyDomain, origin, measurement, filters);
                    }
                }
            }
        }

        log.info("{} 전체 도메인 평균 저장 완료", logPrefix);
    }
}

