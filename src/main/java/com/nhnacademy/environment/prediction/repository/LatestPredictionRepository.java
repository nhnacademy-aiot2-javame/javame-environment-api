package com.nhnacademy.environment.prediction.repository;


import com.nhnacademy.environment.prediction.domain.LatestPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LatestPredictionRepository extends JpaRepository<LatestPrediction, Long> {

    @Query("SELECT lp FROM LatestPrediction lp WHERE lp.companyDomain = :companyDomain " +
            "AND lp.deviceId = :deviceId AND lp.resourceType = :resourceType " +
            "AND lp.targetTime BETWEEN :startTime AND :endTime " +
            "ORDER BY lp.targetTime ASC")
    List<LatestPrediction> findPredictions(
            @Param("companyDomain") String companyDomain,
            @Param("deviceId") String deviceId,
            @Param("resourceType") String resourceType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}