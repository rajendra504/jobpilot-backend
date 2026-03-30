package com.jobpilot.jobpilot_backend.profile.dto;

import java.time.LocalDateTime;
import java.util.List;

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

        List<String> connectedPortals,

        int completenessScore,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}