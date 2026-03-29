package com.jobpilot.jobpilot_backend.scraper.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.jobpilot_backend.security.EncryptionService;
import com.jobpilot.jobpilot_backend.user.User;
import com.jobpilot.jobpilot_backend.user.UserRepository;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Manages browser session cookies for portal scraping.
 *
 * FLOW:
 * 1. User calls POST /api/sessions/init?portal=linkedin
 *    → Playwright opens a visible browser window
 *    → User logs in manually (handles OTP, CAPTCHA etc.)
 *    → After 60 seconds (or when we detect login), cookies are saved
 *    → Saved and encrypted in browser_sessions table
 *
 * 2. On every subsequent scrape:
 *    → loadIntoContext() injects the saved cookies before navigating
 *    → LinkedIn sees the same session — no OTP triggered
 *    → Session valid for 30 days (LinkedIn default) or until cookies expire
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrowserSessionService {

    private static final int SESSION_VALID_DAYS = 25;  // refresh 5 days before LinkedIn 30-day expiry

    private final BrowserSessionRepository sessionRepository;
    private final UserRepository           userRepository;
    private final EncryptionService        encryptionService;
    private final ObjectMapper             objectMapper;

    /**
     * Save all cookies from a BrowserContext into the DB.
     * Called after manual login completes.
     */
    @Transactional
    public void saveSession(Long userId, String portal, BrowserContext context) {
        List<Cookie> cookies = context.cookies();

        if (cookies.isEmpty()) {
            log.warn("No cookies to save for portal={} userId={}", portal, userId);
            return;
        }

        try {
            String cookiesJson   = objectMapper.writeValueAsString(cookies);
            String encryptedJson = encryptionService.encrypt(cookiesJson);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            // Delete existing session for this portal (upsert pattern)
            sessionRepository.deleteByUserIdAndPortal(userId, portal.toLowerCase());

            BrowserSession session = BrowserSession.builder()
                    .user(user)
                    .portal(portal.toLowerCase())
                    .cookiesJson(encryptedJson)
                    .expiresAt(LocalDateTime.now().plusDays(SESSION_VALID_DAYS))
                    .build();

            sessionRepository.save(session);
            log.info("Saved {} cookies for portal={} userId={}", cookies.size(), portal, userId);

        } catch (Exception e) {
            log.error("Failed to save browser session: {}", e.getMessage(), e);
        }
    }

    /**
     * Load saved cookies into a BrowserContext.
     * Call this BEFORE navigating to the portal — LinkedIn will recognise the session.
     * Returns true if cookies were loaded, false if no valid session exists.
     */
    @Transactional(readOnly = true)
    public boolean loadIntoContext(Long userId, String portal, BrowserContext context) {
        Optional<BrowserSession> sessionOpt =
                sessionRepository.findByUserIdAndPortal(userId, portal.toLowerCase());

        if (sessionOpt.isEmpty()) {
            log.info("No saved session for portal={} userId={}", portal, userId);
            return false;
        }

        BrowserSession session = sessionOpt.get();

        if (session.isExpired()) {
            log.info("Saved session expired for portal={} userId={} — needs re-login", portal, userId);
            return false;
        }

        try {
            String decryptedJson = encryptionService.decrypt(session.getCookiesJson());
            List<Cookie> cookies = objectMapper.readValue(decryptedJson, new TypeReference<>() {});
            context.addCookies(cookies);
            log.info("Loaded {} cookies for portal={} userId={}", cookies.size(), portal, userId);
            return true;
        } catch (Exception e) {
            log.error("Failed to load browser session cookies: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if a valid non-expired session exists.
     */
    @Transactional(readOnly = true)
    public boolean hasValidSession(Long userId, String portal) {
        return sessionRepository.findByUserIdAndPortal(userId, portal.toLowerCase())
                .map(s -> !s.isExpired())
                .orElse(false);
    }

    /**
     * Delete a session (forces re-login on next scrape).
     */
    @Transactional
    public void clearSession(Long userId, String portal) {
        sessionRepository.deleteByUserIdAndPortal(userId, portal.toLowerCase());
        log.info("Cleared session for portal={} userId={}", portal, userId);
    }
}