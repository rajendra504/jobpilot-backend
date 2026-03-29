package com.jobpilot.jobpilot_backend.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned after successful login.
 * Angular stores the token in memory (not localStorage)
 * and sends it as: Authorization: Bearer <token>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String tokenType;
    private Long userId;
    private String fullName;
    private String email;
    private String role;
}