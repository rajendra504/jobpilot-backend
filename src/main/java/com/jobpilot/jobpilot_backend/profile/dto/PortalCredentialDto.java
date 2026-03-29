package com.jobpilot.jobpilot_backend.profile.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Used when the user submits credentials for a job portal (LinkedIn, Naukri, etc.).
 * The password is encrypted by EncryptionService before being stored in the DB.
 * The password field is WRITE-ONLY — it is never returned in any response.
 */
public record PortalCredentialDto(

        @NotBlank(message = "Portal name is required")
        String portal,       // e.g. "linkedin", "naukri"

        @NotBlank(message = "Username is required")
        String username,     // email or username used on that portal

        @NotBlank(message = "Password is required")
        String password      // plain-text from client — encrypted before storage
) {}