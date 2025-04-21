package com.nhnacademy.environment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;

/**
 * InfluxDB 연결을 위한 Bean 설정 클래스.
 */
@Configuration
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
        return InfluxDBClientFactory.create(url, token.toCharArray(), orgValue, bucket);
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
}
