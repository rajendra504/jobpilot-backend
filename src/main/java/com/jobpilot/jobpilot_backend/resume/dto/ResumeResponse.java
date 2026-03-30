package com.jobpilot.jobpilot_backend.resume.dto;

import java.time.LocalDateTime;

public record ResumeResponse(
        Long id,
        String originalFilename,
        String contentType,
        Long fileSize,
        boolean primary,
        String textPreview,
        boolean textExtracted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}