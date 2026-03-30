package com.jobpilot.jobpilot_backend.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalCredentialDto(

        @NotBlank(message = "Portal name is required")
        String portal,

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {}