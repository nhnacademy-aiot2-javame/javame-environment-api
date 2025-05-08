package com.nhnacademy.environment.util;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InfluxUtilTest {

    private QueryApi queryApi;

    @BeforeEach
    void setUp() {
        queryApi = mock(QueryApi.class);
    }

    @Test
    @DisplayName("getTagValue: 값이 존재할 경우 정상 반환")
    void testGetTagValueWithValue() {
        FluxRecord record = mock(FluxRecord.class);
        when(record.getValueByKey("location")).thenReturn("Seoul");

        String result = InfluxUtil.getTagValue(record, "location");

        assertThat(result).isEqualTo("Seoul");
    }

    @Test
    @DisplayName("getTagValue: null 일 경우 빈 문자열 반환")
    void testGetTagValueWithNull() {
        FluxRecord record = mock(FluxRecord.class);
        when(record.getValueByKey("location")).thenReturn(null);

        String result = InfluxUtil.getTagValue(record, "location");

        assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("extractDistinctValues: 정상 케이스")
    void testExtractDistinctValues() {
        FluxRecord record1 = mock(FluxRecord.class);
        FluxRecord record2 = mock(FluxRecord.class);
        when(record1.getValueByKey("location")).thenReturn("Seoul");
        when(record2.getValueByKey("location")).thenReturn("Busan");

        FluxTable table = mock(FluxTable.class);
        when(table.getRecords()).thenReturn(List.of(record1, record2));
        when(queryApi.query(anyString(), anyString())).thenReturn(List.of(table));

        List<String> result = InfluxUtil.extractDistinctValues(queryApi, "dummy", "org", "location");

        assertThat(result).containsExactlyInAnyOrder("Seoul", "Busan");
    }

    @Test
    @DisplayName("extractDistinctValues: 예외 발생 시 빈 리스트 반환")
    void testExtractDistinctValuesWithException() {
        when(queryApi.query(anyString(), anyString())).thenThrow(new RuntimeException("쿼리 실패"));

        List<String> result = InfluxUtil.extractDistinctValues(queryApi, "dummy", "org", "location");

        assertThat(result).isEmpty();
    }
}
