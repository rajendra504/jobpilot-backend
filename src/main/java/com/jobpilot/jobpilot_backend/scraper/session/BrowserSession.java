package com.jobpilot.jobpilot_backend.scraper.session;

import com.jobpilot.jobpilot_backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Persists Playwright browser cookies per user per portal.
 *
 * WHY: LinkedIn requires OTP/2FA on automated logins. Once the user
 * logs in manually (via /api/sessions/init endpoint), we save the
 * resulting cookies. Future scrapes reuse these cookies so LinkedIn
 * thinks it's the same browser session — no OTP triggered.
 *
 * Cookies are AES-256 encrypted before storage (same EncryptionService).
 */
@Entity
@Table(name = "browser_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrowserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String portal;           // "linkedin" | "naukri"

    @Column(name = "cookies_json", nullable = false, columnDefinition = "TEXT")
    private String cookiesJson;       // AES-256 encrypted serialized cookies

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // treat session as stale after this date

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}