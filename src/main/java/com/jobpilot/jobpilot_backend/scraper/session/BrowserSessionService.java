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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrowserSessionService {

    private static final int SESSION_VALID_DAYS = 25;

    private final BrowserSessionRepository sessionRepository;
    private final UserRepository           userRepository;
    private final EncryptionService        encryptionService;
    private final ObjectMapper             objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSession(Long userId, String portal, BrowserContext context) {
        List<Cookie> cookies = context.cookies();

        if (cookies.isEmpty()) {
            log.warn("No cookies to save for portal={} userId={}", portal, userId);
            return;
        }

        try {
            String cookiesJson   = objectMapper.writeValueAsString(cookies);
            String encryptedJson = encryptionService.encrypt(cookiesJson);
            String normalizedPortal = portal.toLowerCase();

            Optional<BrowserSession> existing =
                    sessionRepository.findByUserIdAndPortal(userId, normalizedPortal);

            BrowserSession session;

            if (existing.isPresent()) {
                // UPDATE in place — no duplicate key possible
                session = existing.get();
                session.setCookiesJson(encryptedJson);
                session.setExpiresAt(LocalDateTime.now().plusDays(SESSION_VALID_DAYS));
                // @UpdateTimestamp on updatedAt handles the timestamp automatically
            } else {
                // INSERT — only when row genuinely does not exist yet
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

                session = BrowserSession.builder()
                        .user(user)
                        .portal(normalizedPortal)
                        .cookiesJson(encryptedJson)
                        .expiresAt(LocalDateTime.now().plusDays(SESSION_VALID_DAYS))
                        .build();
            }

            sessionRepository.save(session);
            log.info("Saved {} cookies for portal={} userId={}", cookies.size(), portal, userId);

        } catch (Exception e) {
            // Do NOT rethrow — a failed cookie refresh must not abort the whole scrape
            log.error("Failed to save browser session for portal={} userId={}: {}",
                    portal, userId, e.getMessage(), e);
        }
    }

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
            log.info("Session expired for portal={} userId={} — needs re-login", portal, userId);
            return false;
        }

        try {
            String decryptedJson = encryptionService.decrypt(session.getCookiesJson());
            List<Cookie> cookies = objectMapper.readValue(decryptedJson, new TypeReference<>() {});
            context.addCookies(cookies);
            context.addInitScript(
                    "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
            );
            log.info("Loaded {} cookies for portal={} userId={}", cookies.size(), portal, userId);
            return true;
        } catch (Exception e) {
            log.error("Failed to load session cookies for portal={} userId={}: {}",
                    portal, userId, e.getMessage());
            return false;
        }
    }

    @Transactional(readOnly = true)
    public boolean hasValidSession(Long userId, String portal) {
        return sessionRepository.findByUserIdAndPortal(userId, portal.toLowerCase())
                .map(s -> !s.isExpired())
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearSession(Long userId, String portal) {
        sessionRepository.deleteByUserIdAndPortal(userId, portal.toLowerCase());
        log.info("Cleared session for portal={} userId={}", portal, userId);
    }
}