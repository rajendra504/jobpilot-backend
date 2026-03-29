package com.jobpilot.jobpilot_backend.profile.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbound DTO — never exposes encrypted passwords or internal IDs.
 * Portal credentials are returned as a list of portal names only (no passwords).
 */
public record UserProfileResponse(

        Long userId,
        String fullName,
        String email,

        // Personal details
        String phone,
        String location,
        String linkedinUrl,
        String githubUrl,
        String portfolioUrl,
        String summary,

        // Parsed collections
        List<String> skills,
        List<String> languages,
        List<UserProfileRequest.EducationDto> education,
        List<UserProfileRequest.ExperienceDto> experience,
        List<QaPairDto> qaBank,

        // Only the portal NAMES — passwords are never returned
        List<String> connectedPortals,

        // Profile completeness score (0–100) for the dashboard progress bar
        int completenessScore,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}