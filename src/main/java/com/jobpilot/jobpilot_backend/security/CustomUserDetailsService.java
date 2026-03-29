package com.jobpilot.jobpilot_backend.security;

import com.jobpilot.jobpilot_backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads a User from DB and wraps it in UserPrincipal.
 * This is the bridge Spring Security calls during authentication.
 * Injected into SecurityConfig directly — NOT into JwtAuthFilter
 * to avoid circular dependency.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(UserPrincipal::of)                        // wrap entity → UserPrincipal
                .orElseThrow(() ->
                        new UsernameNotFoundException("No user registered with email: " + email));
    }
}