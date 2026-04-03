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

//package com.jobpilot.jobpilot_backend.ai.dto;
//
//import lombok.AllArgsConstructor;
//import lombok.Builder;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//import java.time.LocalDateTime;
//import java.util.List;

/**
 * Outbound DTO returned by AiEngineService.
 *
 * IMPORTANT: The ApplicationRunnerService accesses these fields directly:
 *   analysisResponse.jobListingId()
 *   analysisResponse.matchScore()
 *   analysisResponse.decision()
 *   analysisResponse.status()
 *
 * If your existing AiAnalysisResponse uses different field names,
 * update ApplicationRunnerService to match — or keep this as the canonical version.
 *
 * This is a Java record — all fields accessed via no-arg accessor methods.
 */

//@Builder
//public record AiAnalysisResponse(
//        Long id,
//        Long jobListingId,
//        String jobTitle,
//        String companyName,
//        Integer matchScore,
//        String decision,
//        String decisionReason,
//        List<String> missingSkills,
//        String coverLetter,
//        String resumeSnippet,
////        List<Object> applicationAnswers,
//        List<AnalysisResult.ApplicationAnswer> applicationAnswers,
//        String status,
//        String errorMessage,
//        Integer promptTokensUsed,
//        LocalDateTime createdAt,
//        LocalDateTime updatedAt
//) {}