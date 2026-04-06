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
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

        String loginUrl = getLoginUrl(portal);

        launchBrowserAsync(userId, portal, loginUrl);

        String instruction = switch (portal.toLowerCase()) {
            case "linkedin" -> "LinkedIn login page is opening in the server's Chromium browser. " +
                    "Wait ~10 seconds for it to load. On hosted deployments (Render) the browser is headless " +
                    "— you cannot see it. Once ready, click 'Confirm & Save Session' to capture the cookies.";
            case "naukri"   -> "Naukri login page is opening in the server's Chromium browser. " +
                    "Wait ~10 seconds, then click 'Confirm & Save Session'.";
            default         -> "Browser is opening at " + loginUrl +
                    ". Wait ~10 seconds, then call POST /api/sessions/confirm?portal=" + portal;
        };

        return ResponseEntity.ok(ApiResponse.success(
                "Browser is being opened. Wait a few seconds, then confirm.",
                Map.of("instruction", instruction,
                        "portal",      portal,
                        "loginUrl",    loginUrl,
                        "nextStep",    "POST /api/sessions/confirm?portal=" + portal)
        ));
    }


    @Async
    public void launchBrowserAsync(Long userId, String portal, String loginUrl) {
        try {
            Browser browser = playwrightConfig.getBrowser();
            BrowserContext context = browser.newContext();
            Page page = context.newPage();
            page.setDefaultTimeout(120_000);

            log.info("[Async] Navigating to {} for userId={}", loginUrl, userId);
            page.navigate(loginUrl);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            pendingSessions.put(userId, new ActiveSession(context, portal.toLowerCase()));

            log.info("[Async] Session stored in pendingSessions for userId={}. Map size={}",
                    userId, pendingSessions.size());
        } catch (Exception e) {
            log.error("[Async] Failed to open browser for userId={} portal={}: {}", userId, portal, e.getMessage());
            // Don't put anything in pendingSessions — /confirm will return a clear error
        }
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
            log.warn("No pending session found for userId={}. Map size={}", userId, pendingSessions.size());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(
                            "No active browser session found. Either the browser is still loading " +
                                    "(wait a few more seconds and try again), or it failed to start. " +
                                    "Please call POST /api/sessions/init?portal=" + portal + " again."));
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
        closePendingSession(userId);

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