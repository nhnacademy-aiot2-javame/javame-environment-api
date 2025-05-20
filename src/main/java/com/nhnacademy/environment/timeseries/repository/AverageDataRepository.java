package com.nhnacademy.environment.timeseries.repository;

import com.nhnacademy.environment.timeseries.domain.AverageData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AverageDataRepository extends JpaRepository<AverageData, Long> {
}
