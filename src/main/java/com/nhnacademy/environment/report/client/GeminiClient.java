package com.nhnacademy.environment.report.client;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.stereotype.Component;

@Component
public class GeminiClient {

    private final Client client;

    public GeminiClient() {
        this.client = new Client();
    }

    public String generateSummary(String prompt) {
        // "gemini-2.0-flash-001" 모델명에 맞게 변경 (예제와 동일하게)
        GenerateContentResponse response = client.models.generateContent("gemini-2.0-flash-001", prompt, null);
        return response.text();
    }
}
