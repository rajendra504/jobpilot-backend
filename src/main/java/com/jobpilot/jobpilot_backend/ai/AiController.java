package com.jobpilot.jobpilot_backend.ai;

import com.jobpilot.jobpilot_backend.ai.dto.AiAnalysisResponse;
import com.jobpilot.jobpilot_backend.common.ApiResponse;
import com.jobpilot.jobpilot_backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiEngineService aiEngineService;

    @PostMapping("/analyse/{jobId}")
    public ResponseEntity<ApiResponse<AiAnalysisResponse>> analyseJob(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long jobId) {

        AiAnalysisResponse result = aiEngineService.analyseJob(principal.getId(), jobId);

        String message = "APPLY".equals(result.getDecision())
                ? String.format("Job analysed. Score: %d/100 — APPLY recommended.",
                result.getMatchScore())
                : String.format("Job analysed. Score: %d/100 — SKIP recommended: %s",
                result.getMatchScore(), result.getDecisionReason());

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @PostMapping("/analyse/batch")
    public ResponseEntity<ApiResponse<List<AiAnalysisResponse>>> analyseAllNew(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<AiAnalysisResponse> results =
                aiEngineService.analyseAllNewJobs(principal.getId());

        long applyCount = results.stream()
                .filter(r -> "APPLY".equals(r.getDecision())).count();

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Batch analysis complete. %d jobs analysed, %d recommended to APPLY.",
                        results.size(), applyCount),
                results));
    }

    @GetMapping("/analyses/{jobId}")
    public ResponseEntity<ApiResponse<AiAnalysisResponse>> getAnalysis(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long jobId) {

        AiAnalysisResponse result = aiEngineService.getAnalysis(principal.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success("Analysis retrieved.", result));
    }

    @GetMapping("/analyses")
    public ResponseEntity<ApiResponse<Page<AiAnalysisResponse>>> getAllAnalyses(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String decision,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "15") int size) {

        Page<AiAnalysisResponse> results =
                aiEngineService.getAllAnalysesPaged(principal.getId(), decision, page, size);

        return ResponseEntity.ok(ApiResponse.success(
                results.getTotalElements() + " analysis/analyses retrieved.", results));
    }
}