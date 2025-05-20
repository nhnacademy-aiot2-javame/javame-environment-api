package com.nhnacademy.environment.timeseries.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 차트 시각화를 위한 데이터 전송 객체입니다.
 * <p>
 * - {@code labels}: X축에 표시할 시간 또는 카테고리 라벨 목록<br>
 * - {@code values}: Y축에 표시할 측정값 목록<br>
 * - {@code title}: 차트 제목 또는 데이터의 식별자
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataDto {

    /**
     * X축 라벨 목록. (예: 시간대, 측정 항목 이름 등)
     */
    private List<String> labels;

    /**
     * Y축 값 목록. (예: 온도, CPU 사용률 등)
     */
    private List<Double> values;

    /**
     * 차트 제목 또는 데이터 식별용 문자열.
     */
    private String title;
}
