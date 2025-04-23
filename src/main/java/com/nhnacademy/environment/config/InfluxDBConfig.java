package com.nhnacademy.environment.config;

import com.influxdb.client.QueryApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;

/**
 * InfluxDB 연결을 위한 Bean 설정 클래스.
 */
@Configuration
@Slf4j
public class InfluxDBConfig {

    @Value("${influxdb.url}")
    private String url;

    @Value("${influxdb.token}")
    private String token;

    @Value("${influxdb.org}")
    private String orgValue;

    @Value("${influxdb.bucket}")
    private String bucket;

    /**
     * InfluxDBClient Bean 생성.
     * @return InfluxDBClient 인스턴스
     */
    @Bean
    public InfluxDBClient influxDBClient() {
        log.info("[InfluxDB 연결 정보]");
        log.info("▶ URL    : {}", url);
        log.info("▶ Token  : {}", token);
        log.info("▶ Org    : {}", orgValue);
        log.info("▶ Bucket : {}", bucket);
        return InfluxDBClientFactory.create(url, token.toCharArray());
    }

    @Bean(name = "influxBucket")
    public String influxBucket() {
        return bucket;
    }

    // Bean 이름 변경!
    @Bean(name = "influxOrganization") // <<< 이름 변경!
    public String influxOrganization() {
        return orgValue;
    }

    @Bean
    public QueryApi queryApi(InfluxDBClient influxDBClient) {
        return influxDBClient.getQueryApi();
    }
}
