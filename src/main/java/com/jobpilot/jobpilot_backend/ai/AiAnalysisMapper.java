package com.jobpilot.jobpilot_backend.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.jobpilot_backend.ai.dto.AnalysisResult;
import com.jobpilot.jobpilot_backend.ai.dto.AiAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalysisMapper {

    private final ObjectMapper objectMapper;

    public AiAnalysisResponse toResponse(AiAnalysis analysis) {
        return AiAnalysisResponse.builder()
                .id(analysis.getId())
                .jobListingId(analysis.getJobListing().getId())
                .jobTitle(analysis.getJobListing().getJobTitle())
                .companyName(analysis.getJobListing().getCompanyName())
                .matchScore(analysis.getMatchScore())
                .decision(analysis.getDecision())
                .decisionReason(analysis.getDecisionReason())
                .missingSkills(parseMissingSkills(analysis.getMissingSkillsJson()))
                .coverLetter(analysis.getCoverLetter())
                .resumeSnippet(analysis.getResumeSnippet())
                .applicationAnswers(parseAnswers(analysis.getApplicationAnswersJson()))
                .status(analysis.getStatus())
                .errorMessage(analysis.getErrorMessage())
                .promptTokensUsed(analysis.getPromptTokensUsed())
                .createdAt(analysis.getCreatedAt())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    public List<String> parseMissingSkills(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse missing_skills_json: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<AnalysisResult.ApplicationAnswer> parseAnswers(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse application_answers_json: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return null; }
    }
}