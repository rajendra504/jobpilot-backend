package com.jobpilot.jobpilot_backend.scraper.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PlaywrightConfig {

    @Value("${app.playwright.headless:true}")
    private boolean headless;

    @Getter
    private Playwright playwright;

    @Getter
    private Browser browser;

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