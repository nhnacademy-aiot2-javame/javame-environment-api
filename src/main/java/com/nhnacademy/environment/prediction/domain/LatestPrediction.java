package com.nhnacademy.environment.prediction.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "latest_predictions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LatestPrediction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_domain")
    private String companyDomain;

    @Column(name = "server_no")
    private String serverNo;

    @Column(name = "target_time")
    private LocalDateTime targetTime;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "predicted_value")
    private Double predictedValue;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "device_id")
    private String deviceId;
}