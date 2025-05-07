package com.nhnacademy.environment.timeseries.service;

import com.nhnacademy.environment.timeseries.domain.AverageData;
import com.nhnacademy.environment.timeseries.repository.AverageDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSeriesAverageBatchService {

    private final TimeSeriesAverageService averageService;
    private final TimeSeriesDataService timeSeriesDataService;
    private final AverageDataRepository averageDataRepository;

    /**
     * 매일 00시 10분: 전날 00:00 ~ 23:59 데이터 평균 저장 (운영)
     */
    @Scheduled(cron = "0 10 0 * * *")
    public void saveDailyAverage() {
        log.info("하루 평균 저장 배치 시작");

        LocalDate targetDate = LocalDate.now().minusDays(1);
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        saveAverageRangeAllDomains(start, end, targetDate, false, "[운영]");
    }

    /**
     * 매 1분마다 실행: 직전 1시간 평균값 저장 (테스트용)
     */
//    @Scheduled(cron = "0 * * * * *")
//    public void saveTestAverage() {
//        log.info("테스트 평균 저장 배치 시작");
//
//        LocalDateTime end = LocalDateTime.now();
//        LocalDateTime start = end.minusHours(1);
//        LocalDate averageDate = start.toLocalDate();
//
//        saveAverageRangeAllDomains(start, end, averageDate, false, "[테스트]");
//    }

    /**
     * 모든 도메인/오리진/측정값에 대해 평균값 저장
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
                        log.warn("{} 평균 없음 - domain={}, origin={}, measurement={}, filters={}", logPrefix, companyDomain, origin, measurement, filters);
                    }
                }
            }
        }

        log.info("{} 전체 도메인 평균 저장 배치 완료", logPrefix);
    }
}
