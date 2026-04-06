package com.jobpilot.jobpilot_backend.scraper.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

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
            System.setProperty("PLAYWRIGHT_BROWSERS_PATH", "/ms-playwright");
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(headless)
                            .setArgs(List.of(
                                    "--no-sandbox",
                                    "--disable-setuid-sandbox",
                                    "--disable-dev-shm-usage",
                                    "--disable-blink-features=AutomationControlled",
                                    "--disable-infobars",
                                    "--disable-extensions",
                                    "--disable-gpu",
                                    "--window-size=1920,1080",
                                    "--start-maximized",
                                    // Hide headless mode from sites that check for it
                                    "--disable-features=IsolateOrigins,site-per-process",
                                    "--flag-switches-begin",
                                    "--disable-site-isolation-trials",
                                    "--flag-switches-end"
                            ))
                            .setChromiumSandbox(false)
            );
            log.info("Playwright browser started.");
        }
        return browser;
    }

    public Browser.NewContextOptions stealthContextOptions() {
        return new Browser.NewContextOptions()
                .setUserAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/124.0.0.0 Safari/537.36"
                )
                .setViewportSize(1920, 1080)
                .setLocale("en-US")
                .setTimezoneId("Asia/Kolkata")
                .setProxy(new Proxy("http://user:pass@proxy-host:port"))
                .setExtraHTTPHeaders(java.util.Map.of(
                        "Accept-Language", "en-US,en;q=0.9",
                        "Accept-Encoding", "gzip, deflate, br",
                        "sec-ch-ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"",
                        "sec-ch-ua-mobile", "?0",
                        "sec-ch-ua-platform", "\"Windows\""
                ));
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