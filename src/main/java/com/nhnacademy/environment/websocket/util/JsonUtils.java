// src/main/java/com/nhnacademy/environment/websocket/util/JsonUtils.java
package com.nhnacademy.environment.websocket.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

@Component
public class JsonUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())  // Java 8 시간 타입 지원
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // ISO 8601 형식으로 출력

    public static String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) throws Exception {
        return objectMapper.readValue(json, clazz);
    }
}
