package com.jobpilot.jobpilot_backend.security;

import com.jobpilot.jobpilot_backend.user.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapter between our User entity and Spring Security's UserDetails contract.
 *
 * WHY this class exists:
 *  - User entity  = database concern only (fields, JPA mappings, persistence)
 *  - UserPrincipal = security concern only (authorities, credentials for Spring)
 *  - Separation means changing JPA annotations never touches security logic
 *  - Standard production pattern — User entity stays a plain @Entity
 */
public class UserPrincipal implements UserDetails {

    @Getter private final Long id;
    @Getter private final String email;
    @Getter private final String fullName;

    private final String passwordHash;
    private final String role;
    private final boolean active;

    private UserPrincipal(User user) {
        this.id           = user.getId();
        this.email        = user.getEmail();
        this.fullName     = user.getFullName();
        this.passwordHash = user.getPasswordHash();
        this.role         = user.getRole();
        this.active       = user.isActive();
    }

    /** Factory method — only way to create a UserPrincipal */
    public static UserPrincipal of(User user) {
        return new UserPrincipal(user);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override public String getPassword()   { return passwordHash; }
    @Override public String getUsername()   { return email; }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return active; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return active; }
}