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

    @Value("${app.gemini.model:gemini-2.5-flash}")
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
                        .temperature(0.3)
                        .maxOutputTokens(2000)
                        .responseMimeType("application/json")   // forces structured JSON output
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
                throw new GeminiException("Gemini API returned HTTP " + response.statusCode()
                        + ": " + response.body());
            }

            GeminiResponse geminiResponse =
                    objectMapper.readValue(response.body(), GeminiResponse.class);

            if (geminiResponse.getCandidates() == null || geminiResponse.getCandidates().isEmpty()) {
                throw new GeminiException("Gemini returned no candidates");
            }

            Candidate candidate = geminiResponse.getCandidates().get(0);

            if (candidate.getContent() == null
                    || candidate.getContent().getParts() == null
                    || candidate.getContent().getParts().isEmpty()) {
                throw new GeminiException("Gemini candidate has no content parts");
            }

            String text = candidate.getContent().getParts().get(0).getText();

            if (text == null || text.isBlank()) {
                throw new GeminiException("Gemini returned empty text content");
            }

            if (geminiResponse.getUsageMetadata() != null) {
                log.info("[Gemini] Tokens used: {}", geminiResponse.getUsageMetadata().getTotalTokenCount());
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

        String cleaned = rawJson
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("```\\s*$", "")
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        try {
            return objectMapper.readValue(cleaned, AnalysisResult.class);

        } catch (Exception e) {

            log.error("[Gemini] Initial parse failed. Attempting repair...");
            log.error("[Gemini RAW]: {}", cleaned);

            try {

                String repaired = cleaned
                        .replaceAll(",\\s*]", "]")        // remove trailing commas
                        .replaceAll("\\[\\s*,", "[")      // remove leading commas
                        .replaceAll(",\\s*,", ",")        // double commas fix
                        .replaceAll("\"\\s*\"", "\"\"");  // fix empty broken strings

                return objectMapper.readValue(repaired, AnalysisResult.class);

            } catch (Exception ex) {
                log.error("[Gemini] Repair also failed.");

                return fallbackResult();
            }
        }
    }
    private AnalysisResult fallbackResult() {
        AnalysisResult result = new AnalysisResult();

        result.setMatchScore(0);
        result.setDecision("SKIP");
        result.setDecisionReason("AI parsing failed");
        result.setMissingSkills(List.of());
        result.setCoverLetter("");
        result.setResumeSnippet("");
        result.setApplicationAnswers(List.of());

        return result;
    }

    public static class GeminiException extends Exception {
        public GeminiException(String message)                  { super(message); }
        public GeminiException(String message, Throwable cause) { super(message, cause); }
    }
}