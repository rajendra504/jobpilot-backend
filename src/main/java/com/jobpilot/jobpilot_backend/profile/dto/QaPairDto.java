package com.jobpilot.jobpilot_backend.profile.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A single question-answer pair in the user's Q&A bank.
 * These are pre-written answers the AI uses to fill job application forms.
 *
 * Example:
 *   question: "How many years of Java experience do you have?"
 *   answer:   "3 years of professional Java experience with Spring Boot"
 */
public record QaPairDto(

        @NotBlank(message = "Question is required")
        String question,

        @NotBlank(message = "Answer is required")
        String answer
) {}