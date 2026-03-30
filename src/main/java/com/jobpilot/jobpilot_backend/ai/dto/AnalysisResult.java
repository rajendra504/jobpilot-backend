package com.jobpilot.jobpilot_backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisResult {

    @JsonProperty("matchScore")
    private Integer matchScore;

    @JsonProperty("decision")
    private String decision;

    @JsonProperty("decisionReason")
    private String decisionReason;

    @JsonProperty("missingSkills")
    private List<String> missingSkills;

    @JsonProperty("coverLetter")
    private String coverLetter;

    @JsonProperty("resumeSnippet")
    private String resumeSnippet;

    @JsonProperty("applicationAnswers")
    private List<ApplicationAnswer> applicationAnswers;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApplicationAnswer {
        @JsonProperty("question")
        private String question;
        @JsonProperty("answer")
        private String answer;
    }
}