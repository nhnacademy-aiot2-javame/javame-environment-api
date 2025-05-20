package com.nhnacademy.environment.timeseries.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * InfluxDB의 센서 또는 서버 데이터를 하루 단위로 평균 계산하여
 * MySQL 의 `average_data` 테이블에 저장하기 위한 엔티티 클래스입니다.
 * <p>
 * 이 클래스는 주로 자정 기준 배치 작업에서 생성되며,
 * 회사 도메인, 측정 항목, 필드 기준으로 평균값을 계산하고 저장합니다.
 */
@Entity
@Table(name = "average_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AverageData {

    /**
     * 평균 데이터의 기본 키 (자동 증가).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "average_id")
    private Long id;

    /**
     * 센서 종류 (예: "sensor", "server").
     */
    @Column(name = "sensor_type", nullable = false, length = 20)
    private String sensorType;

    /**
     * 소속 회사 도메인 (예: "nhnacademy.com").
     */
    @Column(name = "company_domain", nullable = false, length = 50)
    private String companyDomain;

    /**
     * InfluxDB의 측정값 이름 (예: "cpu", "temperature").
     */
    @Column(name = "measurement", nullable = false, length = 50)
    private String measurement;

    /**
     * 측정값 내의 필드 이름 (예: "usage_idle", "value").
     */
    @Column(name = "field", nullable = false, length = 50)
    private String field;

    /**
     * 계산된 평균값.
     */
    @Column(name = "average_value", nullable = false)
    private Double averageValue;

    /**
     * 평균값이 계산된 날짜 (보통 전날 기준).
     */
    @Column(name = "average_date", nullable = false)
    private LocalDate averageDate;

    /**
     * 평균 계산 시작 시간 (일반적으로 00:00:00).
     */
    @Column(name = "average_start", nullable = false)
    private LocalDateTime averageStart;

    /**
     * 평균 계산 종료 시간 (일반적으로 다음 날 00:00:00).
     */
    @Column(name = "average_end", nullable = false)
    private LocalDateTime averageEnd;

    /**
     * 레코드가 생성된 시간 (자동 생성됨).
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
