package com.jobpilot.jobpilot_backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResponse {

    private Long id;
    private Long jobListingId;
    private String jobTitle;
    private String companyName;


    private int matchScore;
    private String decision;
    private String decisionReason;
    private List<String> missingSkills;


    private String coverLetter;
    private String resumeSnippet;
    private List<AnalysisResult.ApplicationAnswer> applicationAnswers;


    private String status;
    private String errorMessage;
    private Integer promptTokensUsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}