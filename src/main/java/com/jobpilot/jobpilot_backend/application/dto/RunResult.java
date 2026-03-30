package com.jobpilot.jobpilot_backend.application.dto;

import java.util.List;

public record RunResult(
        int totalAnalysed,
        int applied,
        int skipped,
        int failed,
        int manualRequired,
        int limitReached,
        List<String> appliedJobs,
        List<String> failedJobs,
        List<String> manualJobs
) {
    public static RunResult empty(String reason) {
        return new RunResult(0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
    }
}