package com.jobpilot.jobpilot_backend.auth;

import com.jobpilot.jobpilot_backend.auth.dto.*;
import com.jobpilot.jobpilot_backend.exception.ResourceNotFoundException;
import com.jobpilot.jobpilot_backend.security.JwtService;
import com.jobpilot.jobpilot_backend.security.UserPrincipal;
import com.jobpilot.jobpilot_backend.user.User;
import com.jobpilot.jobpilot_backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository       userRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Register a new user.
     * Returns account info + success message — NO token.
     * User must call /login separately to get their token.
     */
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "An account with email '" + request.getEmail() + "' already exists.");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .isActive(true)
                .build();

        User saved = userRepository.save(user);

        return RegisterResponse.builder()
                .userId(saved.getId())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .message("Account created successfully. Please log in.")
                .build();
    }

    /**
     * Authenticate user and return JWT token.
     * AuthenticationManager throws BadCredentialsException automatically
     * if email/password don't match — caught by GlobalExceptionHandler.
     */
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + request.getEmail()));

        UserPrincipal principal = UserPrincipal.of(user);
        String token = jwtService.generateToken(principal);

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}