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
import java.util.ArrayList;
import java.util.List;
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
        LocalDateTime startTime = now.minusHours(hoursBack);
        LocalDateTime endTime = now.plusHours(hoursForward);

        // 1. InfluxDB에서 과거~현재 데이터 조회
        List<TimeSeriesDataPoint> historicalData = getHistoricalCpuData(companyDomain, deviceId, startTime, now);

        // 2. MySQL에서 예측 데이터 조회
        List<TimeSeriesDataPoint> predictedData = getPredictedData(companyDomain, deviceId, "cpu", now, endTime);

        // 3. 데이터 병합
        return ResourcePredictionDto.builder()
                .resourceType("cpu")
                .historicalData(historicalData)
                .predictedData(predictedData)
                .splitTime(now)
                .build();
    }

    /**
     * 메모리 사용률 데이터
     */
    public ResourcePredictionDto getMemoryPredictionData(String companyDomain, String deviceId, int hoursBack, int hoursForward) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusHours(hoursBack);
        LocalDateTime endTime = now.plusHours(hoursForward);

        List<TimeSeriesDataPoint> historicalData = getHistoricalMemoryData(companyDomain, deviceId, startTime, now);
        List<TimeSeriesDataPoint> predictedData = getPredictedData(companyDomain, deviceId, "memory", now, endTime);

        return ResourcePredictionDto.builder()
                .resourceType("memory")
                .historicalData(historicalData)
                .predictedData(predictedData)
                .splitTime(now)
                .build();
    }

    /**
     * 디스크 사용률 데이터
     */
    public ResourcePredictionDto getDiskPredictionData(String companyDomain, String deviceId, int hoursBack, int hoursForward) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusHours(hoursBack);
        LocalDateTime endTime = now.plusHours(hoursForward);

        List<TimeSeriesDataPoint> historicalData = getHistoricalDiskData(companyDomain, deviceId, startTime, now);
        List<TimeSeriesDataPoint> predictedData = getPredictedData(companyDomain, deviceId, "disk", now, endTime);

        return ResourcePredictionDto.builder()
                .resourceType("disk")
                .historicalData(historicalData)
                .predictedData(predictedData)
                .splitTime(now)
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
            |> aggregateWindow(every: 5m, fn: mean, createEmpty: false)
            |> yield(name: "mean")
            """, influxBucket, startTime.atZone(ZoneId.systemDefault()).toInstant(),
                endTime.atZone(ZoneId.systemDefault()).toInstant(), companyDomain, deviceId);

        try {
            List<FluxTable> tables = queryApi.query(fluxQuery, influxOrg);
            List<TimeSeriesDataPoint> dataPoints = new ArrayList<>();

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Double idleValue = (Double) record.getValue();
                    if (idleValue != null) {
                        // CPU 사용률 = 100 - idle
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
            log.error("CPU 과거 데이터 조회 실패: {}", e.getMessage());
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
            |> aggregateWindow(every: 5m, fn: mean, createEmpty: false)
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
     * MySQL에서 예측 데이터 조회
     */
    private List<TimeSeriesDataPoint> getPredictedData(String companyDomain, String deviceId,
                                                       String resourceType, LocalDateTime startTime, LocalDateTime endTime) {
        List<LatestPrediction> predictions = predictionRepository.findPredictions(
                companyDomain, deviceId, resourceType, startTime, endTime);

        return predictions.stream()
                .map(p -> TimeSeriesDataPoint.builder()
                        .timestamp(p.getTargetTime())
                        .value(p.getPredictedValue())
                        .confidenceScore(p.getConfidenceScore())
                        .build())
                .collect(Collectors.toList());
    }
}