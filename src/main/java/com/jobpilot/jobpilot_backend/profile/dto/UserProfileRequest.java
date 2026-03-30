package com.jobpilot.jobpilot_backend.profile.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

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


    public record EducationDto(
            String institution,
            String degree,
            String fieldOfStudy,
            String startYear,
            String endYear,
            Double gpa
    ) {}

    public record ExperienceDto(
            String company,
            String title,
            String location,
            String startDate,
            String endDate,
            boolean current,
            String description
    ) {}
}