package com.jobpilot.jobpilot_backend.application.dto;

import java.time.LocalDateTime;

public record ApplicationLogResponse(
        Long id,
        Long jobListingId,
        String jobTitle,
        String companyName,
        String portal,
        String status,
        String aiDecision,
        Integer matchScore,
        LocalDateTime appliedAt,
        String failureReason,
        String manualApplyUrl,
        LocalDateTime createdAt
) {}