package com.jobpilot.jobpilot_backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.jobpilot_backend.ai.dto.AnalysisResult;
import com.jobpilot.jobpilot_backend.ai.dto.GeminiDtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Value("${app.gemini.model:gemini-1.5-flash}") // Ensure you are using a stable model version
    private String model;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public String sendPrompt(String prompt) throws GeminiException {
        GeminiRequest request = GeminiRequest.builder()
                .contents(List.of(
                        Content.builder()
                                .parts(List.of(Part.builder().text(prompt).build()))
                                .build()
                ))
                .generationConfig(GenerationConfig.builder()
                        .temperature(0.2) // Slightly lower temperature for more consistent JSON
                        .maxOutputTokens(4096) // INCREASED: Prevents truncation
                        .responseMimeType("application/json")
                        .build())
                .build();

        try {
            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(API_URL, model, apiKey)))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Gemini API error: status={} body={}", response.statusCode(), response.body());
                throw new GeminiException("Gemini API returned HTTP " + response.statusCode());
            }

            GeminiResponse geminiResponse =
                    objectMapper.readValue(response.body(), GeminiResponse.class);

            if (geminiResponse.getCandidates() == null || geminiResponse.getCandidates().isEmpty()) {
                throw new GeminiException("Gemini returned no candidates");
            }

            Candidate candidate = geminiResponse.getCandidates().get(0);

            // Log finish reason for debugging truncation
            if (candidate.getFinishReason() != null && !candidate.getFinishReason().equalsIgnoreCase("STOP")) {
                log.warn("[Gemini] Warning: Generation finished with reason: {}", candidate.getFinishReason());
            }

            if (candidate.getContent() == null || candidate.getContent().getParts() == null || candidate.getContent().getParts().isEmpty()) {
                throw new GeminiException("Gemini candidate has no content parts");
            }

            String text = candidate.getContent().getParts().get(0).getText();

            if (text == null || text.isBlank()) {
                throw new GeminiException("Gemini returned empty text content");
            }

            return text.trim();

        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            throw new GeminiException("Gemini HTTP call failed: " + e.getMessage(), e);
        }
    }

    public AnalysisResult sendAndParse(String prompt) throws GeminiException {
        String rawJson = sendPrompt(prompt);

        // REFINED CLEANING: Only remove markdown wrappers if present.
        // Do NOT replace all whitespace/newlines as it can break JSON string values.
        String cleaned = rawJson
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();

        try {
            return objectMapper.readValue(cleaned, AnalysisResult.class);
        } catch (Exception e) {
            log.error("[Gemini] Parse failed. Error: {}", e.getMessage());
            log.error("[Gemini RAW Snippet (End)]: {}", cleaned.substring(Math.max(0, cleaned.length() - 100)));

            // If the JSON is still truncated, we return the fallback 0-score result
            return fallbackResult();
        }
    }

    private AnalysisResult fallbackResult() {
        return AnalysisResult.builder()
                .matchScore(0)
                .decision("SKIP")
                .decisionReason("AI parsing failed (Truncated JSON)")
                .missingSkills(List.of())
                .coverLetter("")
                .resumeSnippet("")
                .applicationAnswers(List.of())
                .build();
    }

    public static class GeminiException extends Exception {
        public GeminiException(String message) { super(message); }
        public GeminiException(String message, Throwable cause) { super(message, cause); }
    }
}