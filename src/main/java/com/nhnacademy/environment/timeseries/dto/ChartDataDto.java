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
     * 한글 label.
     */
    private List<String> labels;

    /**
     * 영문 value (실제로는 숫자 값이지만 아래에서 Map 형태로 변환).
     */
    private List<String> values;

    /** 차트 제목.*/
    private String title;

    /**
     * 파이차트 데이터(숫자).
     */
    private List<Double> data;
}
