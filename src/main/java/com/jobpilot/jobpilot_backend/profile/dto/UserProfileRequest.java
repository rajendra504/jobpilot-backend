package com.jobpilot.jobpilot_backend.profile.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Inbound DTO for creating or updating the user profile.
 * All fields are optional — a PATCH-style partial update is supported.
 */
public record UserProfileRequest(

        @Pattern(regexp = "^[+]?[0-9\\s\\-().]{7,20}$", message = "Invalid phone number format")
        String phone,

        @Size(max = 100)
        String location,

        @Size(max = 255)
        String linkedinUrl,

        @Size(max = 255)
        String githubUrl,

        @Size(max = 255)
        String portfolioUrl,

        String summary,

        List<String> skills,

        List<String> languages,

        List<EducationDto> education,

        List<ExperienceDto> experience,

        List<QaPairDto> qaBank
) {

    // ── Nested DTOs kept close to their parent request ────────

    public record EducationDto(
            String institution,
            String degree,
            String fieldOfStudy,
            String startYear,
            String endYear,           // null if currently studying
            Double gpa
    ) {}

    public record ExperienceDto(
            String company,
            String title,
            String location,
            String startDate,         // "2021-06"
            String endDate,           // null if current
            boolean current,
            String description
    ) {}
}