package com.jobpilot.jobpilot_backend.scraper.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Manages the Playwright browser lifecycle as a Spring singleton.
 *
 * A single Playwright + Browser instance is shared across all scraper calls.
 * Each scraping session creates a new BrowserContext (isolated tab session)
 * and closes it when done — so portal sessions never leak across users.
 *
 * Headless mode:
 *   - true  (default, production): no visible browser window — runs on Render server
 *   - false (set playwright.headless=false in dev): visible Chrome window for debugging
 */
@Component
@Slf4j
public class PlaywrightConfig {

    @Value("${app.playwright.headless:true}")
    private boolean headless;

    @Getter
    private Playwright playwright;

    @Getter
    private Browser browser;

    /**
     * Lazily initializes Playwright + Browser on first use.
     * Thread-safe via synchronized — scraper calls may come from @Scheduled + manual triggers.
     */
    public synchronized Browser getBrowser() {
        if (browser == null || !browser.isConnected()) {
            log.info("Initializing Playwright browser (headless={})", headless);
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(headless)
                            .setArgs(java.util.List.of(
                                    "--no-sandbox",
                                    "--disable-setuid-sandbox",
                                    "--disable-dev-shm-usage",  // important for Linux servers
                                    "--disable-blink-features=AutomationControlled" // avoid bot detection
                            ))
            );
            log.info("Playwright browser started.");
        }
        return browser;
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down Playwright browser.");
        if (browser != null && browser.isConnected()) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}