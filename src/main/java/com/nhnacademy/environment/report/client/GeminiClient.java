package com.nhnacademy.environment.report.client;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class GeminiClient {

    private final Client client;

    public GeminiClient() {
        this.client = new Client();
    }

    public String generateSummary(Map<String, Object> preparedData, String userPrompt) {
        try {
            // preparedData에서 요약 정보 추출
            String summary = (String) preparedData.get("summary");
            String gatewayId = (String) preparedData.get("gatewayId");
            String measurement = (String) preparedData.get("measurement");
            Integer totalDataCount = (Integer) preparedData.get("totalDataCount");

            // AI에게 전달할 프롬프트 구성
            StringBuilder aiPrompt = new StringBuilder();
            aiPrompt.append("다음 시스템 모니터링 데이터를 분석하여 한국어로 상세한 리포트를 작성해주세요.\n\n");
            aiPrompt.append("**사용자 요청:** ").append(userPrompt).append("\n\n");

            if (summary != null) {
                aiPrompt.append("**분석 데이터:**\n").append(summary).append("\n\n");
            }

            aiPrompt.append("**요청사항:**\n");
            aiPrompt.append("1. 데이터 트렌드와 패턴 분석\n");
            aiPrompt.append("2. 현재 상태에 대한 평가\n");
            aiPrompt.append("3. 주의사항이나 권장사항 제시\n");
            aiPrompt.append("4. 마크다운 형식으로 구조화된 리포트 작성\n");

            log.info("Gemini API 호출 시작 - 프롬프트 길이: {} 자", aiPrompt.length());

            GenerateContentResponse response = client.models.generateContent("gemini-2.0-flash-001", aiPrompt.toString(), null);
            String result = response.text();

            log.info("Gemini API 응답 완료 - 응답 길이: {} 자", result.length());
            return result;

        } catch (Exception e) {
            log.error("Gemini API 호출 실패", e);
            return "AI 분석을 수행할 수 없습니다. 시스템 관리자에게 문의하세요.";
        }
    }

    // ★★★ 기존 메서드 (1개 파라미터) - 호환성 유지 ★★★
    public String generateSummary(String prompt) {
        try {
            log.info("Gemini API 단순 호출 - 프롬프트: {}", prompt);
            GenerateContentResponse response = client.models.generateContent("gemini-2.0-flash-001", prompt, null);
            return response.text();
        } catch (Exception e) {
            log.error("Gemini API 단순 호출 실패", e);
            return "AI 분석을 수행할 수 없습니다.";
        }
    }
}
