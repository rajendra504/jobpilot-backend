package com.jobpilot.jobpilot_backend.scraper.session;

import com.jobpilot.jobpilot_backend.common.ApiResponse;
import com.jobpilot.jobpilot_backend.scraper.config.PlaywrightConfig;
import com.jobpilot.jobpilot_backend.security.UserPrincipal;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages one-time manual login sessions for portal scraping.
 *
 * ROOT CAUSE OF PREVIOUS BUG:
 *  Spring Boot handles each HTTP request on a different thread from the pool.
 *  Plain instance fields (activeContext, activeUserId) set on thread exec-3
 *  are NOT guaranteed to be visible on thread exec-4, exec-6 etc.
 *  The /confirm endpoint always saw null because it ran on a different thread.
 *
 * FIX:
 *  Use ConcurrentHashMap<userId, ActiveSession> — shared safely across all
 *  threads. Each user's session is keyed by their userId, so multiple users
 *  can run /init simultaneously without interfering with each other.
 */
@Slf4j
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionInitController {

    private final BrowserSessionService sessionService;
    private final PlaywrightConfig      playwrightConfig;

    private final ConcurrentHashMap<Long, ActiveSession> pendingSessions
            = new ConcurrentHashMap<>();

    private record ActiveSession(BrowserContext context, String portal) {}

    @PostMapping("/init")
    public ResponseEntity<ApiResponse<Map<String, String>>> initSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String portal) {

        Long userId = principal.getId();
        log.info("Session init requested: portal={} userId={}", portal, userId);

        closePendingSession(userId);

        Browser browser = playwrightConfig.getBrowser();

        BrowserContext context = browser.newContext();
        Page page = context.newPage();
        page.setDefaultTimeout(120_000);

        String loginUrl = getLoginUrl(portal);
        page.navigate(loginUrl);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        pendingSessions.put(userId, new ActiveSession(context, portal.toLowerCase()));

        log.info("Session stored in pendingSessions for userId={}. Map size={}",
                userId, pendingSessions.size());

        String instruction = switch (portal.toLowerCase()) {
            case "linkedin" -> "LinkedIn login page is open in Chromium. " +
                    "Log in manually (accept OTP on your phone if prompted). " +
                    "Once you see your LinkedIn feed, call POST /api/sessions/confirm?portal=linkedin";
            case "naukri"   -> "Naukri login page is open. Log in manually. " +
                    "Once logged in, call POST /api/sessions/confirm?portal=naukri";
            default         -> "Browser opened at " + loginUrl +
                    ". Log in manually, then call POST /api/sessions/confirm?portal=" + portal;
        };

        return ResponseEntity.ok(ApiResponse.success(
                "Browser opened for manual login.",
                Map.of("instruction", instruction,
                        "portal",      portal,
                        "loginUrl",    loginUrl,
                        "nextStep",    "POST /api/sessions/confirm?portal=" + portal)
        ));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Map<String, String>>> confirmSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String portal) {

        Long userId = principal.getId();
        log.info("Session confirm requested: portal={} userId={}", portal, userId);
        log.info("Current pendingSessions map keys: {}", pendingSessions.keySet());

        ActiveSession pending = pendingSessions.get(userId);

        if (pending == null) {
            log.warn("No pending session found for userId={}. Map size={}",
                    userId, pendingSessions.size());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(
                            "No active init session found for your account. " +
                                    "Please call POST /api/sessions/init?portal=" + portal + " first.")
            );
        }

        if (!portal.equalsIgnoreCase(pending.portal())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Portal mismatch. You initiated for '"
                            + pending.portal() + "' but confirmed for '" + portal + "'."));
        }

        try {

            sessionService.saveSession(userId, portal, pending.context());

            pendingSessions.remove(userId);
            try { pending.context().close(); } catch (Exception ignored) {}

            log.info("Session confirmed and saved: portal={} userId={}", portal, userId);

            return ResponseEntity.ok(ApiResponse.success(
                    "Session saved successfully! Future scrapes will reuse this session automatically.",
                    Map.of("portal",        portal,
                            "status",        "active",
                            "validForDays",  "25",
                            "nextStep",      "POST /api/jobs/scrape")
            ));

        } catch (Exception e) {
            log.error("Failed to save session: {}", e.getMessage(), e);
            pendingSessions.remove(userId);
            return ResponseEntity.internalServerError().body(
                    ApiResponse.error("Failed to save session: " + e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSessionStatus(
            @AuthenticationPrincipal UserPrincipal principal) {

        Long userId = principal.getId();

        boolean linkedinActive = sessionService.hasValidSession(userId, "linkedin");
        boolean naukriActive   = sessionService.hasValidSession(userId, "naukri");
        boolean hasPending     = pendingSessions.containsKey(userId);

        return ResponseEntity.ok(ApiResponse.success(
                "Session status retrieved.",
                Map.of("linkedin",       linkedinActive,
                        "naukri",         naukriActive,
                        "pendingInit",    hasPending)
        ));
    }

    @DeleteMapping("/{portal}")
    public ResponseEntity<ApiResponse<Void>> clearSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String portal) {

        Long userId = principal.getId();
        sessionService.clearSession(userId, portal);
        closePendingSession(userId);   // also clear any in-progress init

        return ResponseEntity.ok(ApiResponse.success(
                portal + " session cleared. You will be prompted to log in again on next scrape.",
                null));
    }

    private String getLoginUrl(String portal) {
        return switch (portal.toLowerCase()) {
            case "linkedin" -> "https://www.linkedin.com/login";
            case "naukri"   -> "https://www.naukri.com/nlogin/login";
            case "indeed"   -> "https://secure.indeed.com/account/login";
            default         -> "https://www." + portal + ".com/login";
        };
    }

    private void closePendingSession(Long userId) {
        ActiveSession existing = pendingSessions.remove(userId);
        if (existing != null) {
            try { existing.context().close(); } catch (Exception ignored) {}
            log.info("Closed previous pending session for userId={}", userId);
        }
    }
}