package com.jobpilot.jobpilot_backend.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned after successful registration.
 * NO token — user must log in explicitly.
 * This is correct REST convention:
 *   register = create account
 *   login    = authenticate and receive token
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {

    private Long userId;
    private String fullName;
    private String email;
    private String message;
}