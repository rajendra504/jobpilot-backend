package com.jobpilot.jobpilot_backend.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record QaPairDto(

        @NotBlank(message = "Question is required")
        String question,

        @NotBlank(message = "Answer is required")
        String answer
) {}