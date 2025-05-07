package com.nhnacademy.environment.util;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * InfluxDB 관련 FluxRecord 유틸리티 함수 모음.
 * <p>
 * - 공통적인 태그 값 추출 및 distinct 컬럼 조회 로직을 포함합니다.
 */
@Slf4j
public class InfluxUtil {

    /**
     * 주어진 tag key에 해당하는 값을 안전하게 추출합니다.
     *
     * <p>InfluxDB의 FluxRecord에서 특정 key에 해당하는 값을 조회할 때,
     * 값이 존재하지 않거나 null일 수 있기 때문에 이 메서드를 통해 null-safe하게
     * String 값으로 변환하여 사용합니다.
     *
     * @param record Flux 쿼리 결과의 단일 레코드
     * @param key    추출할 태그 또는 필드 이름
     * @return 해당 키의 문자열 값 (없으면 빈 문자열 반환)
     */
    public static String getTagValue(FluxRecord record, String key) {
        Object value = record.getValueByKey(key);
        return value != null ? value.toString() : "";
    }

    /**
     * Flux 쿼리를 실행하고 특정 컬럼의 distinct 값을 추출합니다.
     *
     * @param queryApi   InfluxDB Query API
     * @param fluxQuery  실행할 Flux 쿼리 문자열
     * @param org        조직 이름 (influxOrg)
     * @param columnName 추출할 컬럼명
     * @return 중복 제거된 컬럼 값 리스트
     */
    public static List<String> extractDistinctValues(QueryApi queryApi, String fluxQuery, String org, String columnName) {
        try {
            List<FluxTable> tables = queryApi.query(fluxQuery, org);
            return tables.stream()
                    .flatMap(table -> table.getRecords().stream())
                    .map(record -> (String) record.getValueByKey(columnName))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("extractDistinctValues 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
