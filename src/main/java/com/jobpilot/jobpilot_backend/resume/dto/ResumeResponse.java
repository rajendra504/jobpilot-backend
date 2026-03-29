package com.jobpilot.jobpilot_backend.resume.dto;

import java.time.LocalDateTime;

/**
 * Outbound DTO — never returns the full extracted text (can be large).
 * The extractedText is used internally by the AI engine only.
 * textPreview returns the first 300 characters for dashboard display.
 */
public record ResumeResponse(
        Long id,
        String originalFilename,
        String contentType,
        Long fileSize,
        boolean primary,
        String textPreview,        // first 300 chars of extracted text
        boolean textExtracted,     // true if Tika parsed successfully
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}