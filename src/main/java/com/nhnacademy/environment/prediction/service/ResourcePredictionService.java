package com.nhnacademy.environment.prediction.service;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.environment.prediction.domain.LatestPrediction;
import com.nhnacademy.environment.prediction.dto.ResourcePredictionDto;
import com.nhnacademy.environment.prediction.dto.TimeSeriesDataPoint;
import com.nhnacademy.environment.prediction.repository.LatestPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourcePredictionService {

    private final LatestPredictionRepository predictionRepository;
    private final QueryApi queryApi;

    @Value("${influxdb.org}")
    private String influxOrg;

    @Value("${influxdb.bucket}")
    private String influxBucket;

    /**
     * CPU 사용률 데이터 (과거 + 현재 + 예측)
     */
    public ResourcePredictionDto getCpuPredictionData(String companyDomain, String deviceId, int hoursBack, int hoursForward) {
        LocalDateTime now = LocalDateTime.now();

        // 30분 단위로 정렬된 시작 시간 계산
        LocalDateTime alignedNow = now.truncatedTo(ChronoUnit.HOURS)
                .plusMinutes((now.getMinute() / 30) * 30);

        LocalDateTime startTime = alignedNow.minusHours(hoursBack);

        log.info("===== CPU 예측 데이터 조회 시작 =====");
        log.info("요청 파라미터: companyDomain={}, deviceId={}", companyDomain, deviceId);
        log.info("시간 범위: {} ~ 현재({})", startTime, alignedNow);

        // 1. InfluxDB에서 과거~현재 데이터 조회 (30분 단위 집계)
        List<TimeSeriesDataPoint> historicalData = getHistoricalCpuData(companyDomain, deviceId, startTime, alignedNow);

        // 2. MySQL에서 예측 데이터 조회 (있는 그대로)
        LocalDateTime predictionEndTime = alignedNow.plusHours(hoursForward);
        List<TimeSeriesDataPoint> predictedData = getPredictedData(companyDomain, deviceId, "cpu", alignedNow, predictionEndTime);

        log.info("과거 데이터: {} 건", historicalData.size());
        log.info("예측 데이터: {} 건", predictedData.size());

        return ResourcePredictionDto.builder()
                .resourceType("cpu")
                .historicalData(historicalData)
                .predictedData(predictedData)
                .splitTime(alignedNow)
                .build();
    }

    /**
     * 메모리 사용률 데이터
     */
    public ResourcePredictionDto getMemoryPredictionData(String companyDomain, String deviceId, int hoursBack, int hoursForward) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime alignedNow = now.truncatedTo(ChronoUnit.HOURS)
                .plusMinutes((now.getMinute() / 30) * 30);

        LocalDateTime startTime = alignedNow.minusHours(hoursBack);
        LocalDateTime endTime = alignedNow.plusHours(hoursForward);

        List<TimeSeriesDataPoint> historicalData = getHistoricalMemoryData(companyDomain, deviceId, startTime, alignedNow);
        List<TimeSeriesDataPoint> predictedData = getPredictedData(companyDomain, deviceId, "mem", alignedNow, endTime);

        return ResourcePredictionDto.builder()
                .resourceType("memory")
                .historicalData(historicalData)
                .predictedData(predictedData)
                .splitTime(alignedNow)
                .build();
    }

    /**
     * 디스크 사용률 데이터
     */
    public ResourcePredictionDto getDiskPredictionData(String companyDomain, String deviceId, int hoursBack, int hoursForward) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime alignedNow = now.truncatedTo(ChronoUnit.HOURS)
                .plusMinutes((now.getMinute() / 30) * 30);

        LocalDateTime startTime = alignedNow.minusHours(hoursBack);
        LocalDateTime endTime = alignedNow.plusHours(hoursForward);

        List<TimeSeriesDataPoint> historicalData = getHistoricalDiskData(companyDomain, deviceId, startTime, alignedNow);
        List<TimeSeriesDataPoint> predictedData = getPredictedData(companyDomain, deviceId, "disk", alignedNow, endTime);

        return ResourcePredictionDto.builder()
                .resourceType("disk")
                .historicalData(historicalData)
                .predictedData(predictedData)
                .splitTime(alignedNow)
                .build();
    }

    /**
     * InfluxDB에서 과거 CPU 데이터 조회
     */
    private List<TimeSeriesDataPoint> getHistoricalCpuData(String companyDomain, String deviceId,
                                                           LocalDateTime startTime, LocalDateTime endTime) {
        String fluxQuery = String.format("""
            from(bucket: "%s")
            |> range(start: %s, stop: %s)
            |> filter(fn: (r) => r["companyDomain"] == "%s")
            |> filter(fn: (r) => r["deviceId"] == "%s")
            |> filter(fn: (r) => r["location"] == "server_resource_data")
            |> filter(fn: (r) => r["gatewayId"] == "cpu")
            |> filter(fn: (r) => r["measurement"] == "usage_idle")
            |> aggregateWindow(every: 30m, fn: mean, createEmpty: false)
            |> yield(name: "mean")
            """, influxBucket, startTime.atZone(ZoneId.systemDefault()).toInstant(),
                endTime.atZone(ZoneId.systemDefault()).toInstant(), companyDomain, deviceId);

        log.debug("InfluxDB Flux Query (30분 집계):\n{}", fluxQuery);

        try {
            List<FluxTable> tables = queryApi.query(fluxQuery, influxOrg);
            List<TimeSeriesDataPoint> dataPoints = new ArrayList<>();

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Double idleValue = (Double) record.getValue();
                    if (idleValue != null) {
                        double usage = 100.0 - idleValue;
                        Instant time = (Instant) record.getTime();

                        dataPoints.add(TimeSeriesDataPoint.builder()
                                .timestamp(LocalDateTime.ofInstant(time, ZoneId.systemDefault()))
                                .value(usage)
                                .build());
                    }
                }
            }

            return dataPoints;
        } catch (Exception e) {
            log.error("CPU 과거 데이터 조회 실패: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * InfluxDB에서 과거 메모리 데이터 조회
     */
    private List<TimeSeriesDataPoint> getHistoricalMemoryData(String companyDomain, String deviceId,
                                                              LocalDateTime startTime, LocalDateTime endTime) {
        return getHistoricalResourceData(companyDomain, deviceId, "mem", "used_percent", startTime, endTime);
    }

    /**
     * InfluxDB에서 과거 디스크 데이터 조회
     */
    private List<TimeSeriesDataPoint> getHistoricalDiskData(String companyDomain, String deviceId,
                                                            LocalDateTime startTime, LocalDateTime endTime) {
        return getHistoricalResourceData(companyDomain, deviceId, "disk", "used_percent", startTime, endTime);
    }

    /**
     * InfluxDB 공통 조회 메서드
     */
    private List<TimeSeriesDataPoint> getHistoricalResourceData(String companyDomain, String deviceId,
                                                                String gatewayId, String measurement,
                                                                LocalDateTime startTime, LocalDateTime endTime) {
        String fluxQuery = String.format("""
            from(bucket: "%s")
            |> range(start: %s, stop: %s)
            |> filter(fn: (r) => r["companyDomain"] == "%s")
            |> filter(fn: (r) => r["deviceId"] == "%s")
            |> filter(fn: (r) => r["location"] == "server_resource_data")
            |> filter(fn: (r) => r["gatewayId"] == "%s")
            |> filter(fn: (r) => r["measurement"] == "%s")
            |> aggregateWindow(every: 30m, fn: mean, createEmpty: false)
            |> yield(name: "mean")
            """, influxBucket, startTime.atZone(ZoneId.systemDefault()).toInstant(),
                endTime.atZone(ZoneId.systemDefault()).toInstant(), companyDomain, deviceId, gatewayId, measurement);

        try {
            List<FluxTable> tables = queryApi.query(fluxQuery, influxOrg);
            List<TimeSeriesDataPoint> dataPoints = new ArrayList<>();

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Double value = (Double) record.getValue();
                    if (value != null) {
                        Instant time = (Instant) record.getTime();

                        dataPoints.add(TimeSeriesDataPoint.builder()
                                .timestamp(LocalDateTime.ofInstant(time, ZoneId.systemDefault()))
                                .value(value)
                                .build());
                    }
                }
            }

            return dataPoints;
        } catch (Exception e) {
            log.error("{} 과거 데이터 조회 실패: {}", gatewayId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * MySQL에서 예측 데이터 조회 - 있는 그대로만 반환
     */
    private List<TimeSeriesDataPoint> getPredictedData(String companyDomain, String deviceId,
                                                       String resourceType, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("MySQL 예측 데이터 조회: companyDomain={}, deviceId={}, resourceType={}, {} ~ {}",
                companyDomain, deviceId, resourceType, startTime, endTime);

        List<LatestPrediction> predictions = predictionRepository.findPredictions(
                companyDomain, deviceId, resourceType, startTime, endTime);

        log.info("MySQL 조회 결과: {} 건의 예측 데이터 (resourceType={})", predictions.size(), resourceType);

        // ★★★ 디버그: 실제 DB에 있는 resourceType 값들 확인 ★★★
        if (predictions.isEmpty() && "memory".equals(resourceType)) {
            log.warn("'memory'로 조회 실패. 'mem'으로 재시도...");
            predictions = predictionRepository.findPredictions(
                    companyDomain, deviceId, "mem", startTime, endTime);
            log.info("'mem'으로 재조회 결과: {} 건", predictions.size());
        }

        return predictions.stream()
                .map(p -> TimeSeriesDataPoint.builder()
                        .timestamp(p.getTargetTime())
                        .value(p.getPredictedValue())
                        .confidenceScore(p.getConfidenceScore())
                        .build())
                .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                .collect(Collectors.toList());
    }

    /**
     * 디버그 정보 조회 메서드
     */
    public Map<String, Object> getDebugInfo(String companyDomain, String deviceId) {
        Map<String, Object> debugInfo = new HashMap<>();

        // 1. InfluxDB 데이터 존재 여부 확인
        String fluxQuery = String.format("""
        from(bucket: "%s")
        |> range(start: -24h)
        |> filter(fn: (r) => r["companyDomain"] == "%s")
        |> filter(fn: (r) => r["deviceId"] == "%s")
        |> filter(fn: (r) => r["location"] == "server_resource_data")
        |> group()
        |> count()
        """, influxBucket, companyDomain, deviceId);

        try {
            List<FluxTable> tables = queryApi.query(fluxQuery, influxOrg);
            long influxCount = 0;
            if (!tables.isEmpty() && !tables.get(0).getRecords().isEmpty()) {
                influxCount = ((Number) tables.get(0).getRecords().get(0).getValue()).longValue();
            }
            debugInfo.put("influxDataCount", influxCount);
        } catch (Exception e) {
            debugInfo.put("influxError", e.getMessage());
        }

        // 2. MySQL 예측 데이터 존재 여부 확인 (각 리소스 타입별)
        Map<String, Integer> predictionCounts = new HashMap<>();
        for (String resourceType : Arrays.asList("cpu", "memory", "disk")) {
            List<LatestPrediction> predictions = predictionRepository.findPredictions(
                    companyDomain, deviceId, resourceType,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1));
            predictionCounts.put(resourceType, predictions.size());
        }
        debugInfo.put("mysqlPredictionCounts", predictionCounts);

        // 3. 사용 가능한 companyDomain과 deviceId 목록
        String availableQuery = String.format("""
        from(bucket: "%s")
        |> range(start: -1h)
        |> filter(fn: (r) => r["location"] == "server_resource_data")
        |> keep(columns: ["companyDomain", "deviceId"])
        |> group(columns: ["companyDomain", "deviceId"])
        |> first()
        |> limit(n: 20)
        """, influxBucket);

        List<Map<String, String>> availableData = new ArrayList<>();
        try {
            List<FluxTable> tables = queryApi.query(availableQuery, influxOrg);
            Set<String> uniquePairs = new HashSet<>();

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    String domain = String.valueOf(record.getValueByKey("companyDomain"));
                    String device = String.valueOf(record.getValueByKey("deviceId"));
                    String pair = domain + "|" + device;

                    if (!uniquePairs.contains(pair)) {
                        uniquePairs.add(pair);
                        Map<String, String> item = new HashMap<>();
                        item.put("companyDomain", domain);
                        item.put("deviceId", device);
                        availableData.add(item);
                    }
                }
            }
        } catch (Exception e) {
            log.error("사용 가능한 데이터 조회 실패", e);
        }

        debugInfo.put("availableData", availableData);
        debugInfo.put("requestedCompanyDomain", companyDomain);
        debugInfo.put("requestedDeviceId", deviceId);
        debugInfo.put("currentTime", LocalDateTime.now());

        return debugInfo;
    }

    /**
     * 특정 시간대의 예측 신뢰도 조회
     */
    public Double getPredictionConfidence(String companyDomain, String deviceId,
                                          String resourceType, LocalDateTime targetTime) {
        List<LatestPrediction> predictions = predictionRepository.findPredictions(
                companyDomain, deviceId, resourceType,
                targetTime.minusMinutes(15),
                targetTime.plusMinutes(15));

        if (!predictions.isEmpty()) {
            return predictions.get(0).getConfidenceScore();
        }
        return null;
    }
}