package com.nhnacademy.environment.config.translation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Configuration
public class TranslationConfig {
    @Bean
    public Map<String, String> translationMap() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("translation.json")) {
            if (inputStream == null) throw new IllegalStateException("translation.json not found");
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(inputStream, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException("Failed to load translation.json", e);
        }
    }
}
