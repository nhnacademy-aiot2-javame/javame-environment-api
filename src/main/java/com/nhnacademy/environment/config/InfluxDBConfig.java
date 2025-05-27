package com.nhnacademy.environment.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * InfluxDB 연결 설정을 위한 Spring Configuration 클래스입니다.
 * <p>
 * - InfluxDBClient Bean 등록<br>
 * - QueryApi, bucket, organization 관련 Bean 등록
 */
@Configuration
@Slf4j
public class InfluxDBConfig {

    /**
     * InfluxDB 서버 URL. (예: http://localhost:8086)
     */
    @Value("${influxdb.url}")
    private String url;

    /**
     * InfluxDB 인증용 토큰.
     */
    @Value("${influxdb.token}")
    private String token;

    /**
     * InfluxDB 조직 이름.
     */
    @Value("${influxdb.org}")
    private String orgValue;

    /**
     * InfluxDB 버킷 이름.
     */
    @Value("${influxdb.bucket}")
    private String bucket;

    /**
     * InfluxDBClient Bean 생성 메서드.
     *
     * @return InfluxDBClient 인스턴스
     */
    @Bean
    public InfluxDBClient influxDBClient() {
        log.info("[InfluxDB 연결 성공]");
        return InfluxDBClientFactory.create(url, token.toCharArray());
    }

    /**
     * InfluxDB 버킷 이름을 Bean 으로 제공합니다.
     *
     * @return 버킷 이름 문자열
     */
    @Bean(name = "influxBucket")
    public String influxBucket() {
        return bucket;
    }

    /**
     * InfluxDB 조직 이름을 Bean 으로 제공합니다.
     *
     * @return 조직 이름 문자열
     */
    @Bean(name = "influxOrganization")
    public String influxOrganization() {
        return orgValue;
    }

    /**
     * InfluxDB 쿼리 API Bean 생성.
     *
     * @param influxDBClient InfluxDBClient Bean
     * @return QueryApi 인스턴스
     */
    @Bean
    public QueryApi queryApi(InfluxDBClient influxDBClient) {
        return influxDBClient.getQueryApi();
    }
}
