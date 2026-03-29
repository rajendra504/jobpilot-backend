package com.jobpilot.jobpilot_backend.scraper.portals;

import com.jobpilot.jobpilot_backend.preferences.JobPreferences;
import com.jobpilot.jobpilot_backend.scraper.PortalScraper;
import com.jobpilot.jobpilot_backend.scraper.dto.ScrapedJobDto;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Naukri.com scraper — updated for 2025 login form.
 *
 * KEY CHANGE: Naukri updated their login page. The old selector
 * "input[placeholder='Enter your active Email ID / Username']"
 * no longer exists. We now use multiple fallback selectors and
 * wait for the page to fully settle before attempting login.
 */
@Slf4j
@Component
public class NaukriScraper implements PortalScraper {

    private static final String BASE_URL   = "https://www.naukri.com";
    private static final String LOGIN_URL  = "https://www.naukri.com/nlogin/login";
    private static final int    TIMEOUT_MS = 60_000;   // 60s — Naukri loads slowly

    @Override
    public String portalKey() {
        return "naukri";
    }

    @Override
    public List<ScrapedJobDto> scrape(Page page, String username,
                                      String password, JobPreferences prefs) {
        List<ScrapedJobDto> results = new ArrayList<>();

        try {
            log.info("Naukri scraper starting for user={}", username);

            // ── Step 1: Navigate to login ──────────────────────────────────────
            page.setDefaultTimeout(TIMEOUT_MS);
            page.navigate(LOGIN_URL);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Wait a beat for JS to hydrate the form
            page.waitForTimeout(3000);

            // ── Step 2: Find email field — multiple selectors tried in order ───
            // Naukri has changed selectors several times; we try them all
            Locator emailField = findEmailField(page);
            if (emailField == null) {
                log.error("Naukri: could not find email field — page may have changed structure");
                log.info("Current page URL: {}", page.url());
                log.info("Page title: {}", page.title());
                return results;
            }

            emailField.click();
            emailField.fill(username);
            page.waitForTimeout(800);

            // ── Step 3: Find password field ────────────────────────────────────
            Locator passwordField = findPasswordField(page);
            if (passwordField == null) {
                log.error("Naukri: could not find password field");
                return results;
            }

            passwordField.click();
            passwordField.fill(password);
            page.waitForTimeout(800);

            // ── Step 4: Submit login ───────────────────────────────────────────
            Locator loginButton = findLoginButton(page);
            if (loginButton == null) {
                log.error("Naukri: could not find login button");
                return results;
            }

            loginButton.click();

            // Wait for navigation after login
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(4000);

            // ── Step 5: Check if login succeeded ──────────────────────────────
            String currentUrl = page.url();
            log.info("Naukri post-login URL: {}", currentUrl);

            if (currentUrl.contains("login") || currentUrl.contains("nlogin")) {
                log.warn("Naukri: still on login page — credentials may be wrong or CAPTCHA shown");
                return results;
            }

            log.info("Naukri login successful. Starting job search.");

            // ── Step 6: Build search URL from preferences ──────────────────────
            String searchUrl = buildSearchUrl(prefs);
            log.info("Naukri search URL: {}", searchUrl);

            page.navigate(searchUrl);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(3000);

            // ── Step 7: Scrape job cards ───────────────────────────────────────
            results = scrapeJobCards(page, prefs);
            log.info("Naukri scraper collected {} jobs", results.size());

        } catch (Exception e) {
            log.error("Naukri scraping failed: {}", e.getMessage());
        }

        return results;
    }

    // ── Selector helpers — tries multiple known Naukri selectors ──────────────────

    private Locator findEmailField(Page page) {
        // Naukri has used all of these at different times
        String[] selectors = {
                "input[type='text'][placeholder*='Email']",
                "input[type='text'][placeholder*='email']",
                "input[type='text'][placeholder*='Username']",
                "input[type='email']",
                "#usernameField",
                "input[name='username']",
                "input[id*='email' i]",
                "input[id*='user' i]",
                // 2025 Naukri selector (as of Q1 2025)
                "input[placeholder='Enter your active Email ID / Username']",
                "div.loginForm input[type='text']:first-of-type"
        };

        for (String selector : selectors) {
            try {
                Locator loc = page.locator(selector).first();
                if (loc.isVisible()) {
                    log.info("Naukri: found email field with selector: {}", selector);
                    return loc;
                }
            } catch (Exception ignored) {}
        }

        // Last resort: dump what inputs exist on the page
        log.warn("Naukri: no email field found. Visible inputs on page:");
        try {
            page.locator("input").all().forEach(inp -> {
                try {
                    log.warn("  input type={} placeholder={} id={} name={}",
                            inp.getAttribute("type"),
                            inp.getAttribute("placeholder"),
                            inp.getAttribute("id"),
                            inp.getAttribute("name"));
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}

        return null;
    }

    private Locator findPasswordField(Page page) {
        String[] selectors = {
                "input[type='password']",
                "#passwordField",
                "input[name='password']",
                "input[placeholder*='password' i]",
                "input[placeholder*='Password']"
        };

        for (String selector : selectors) {
            try {
                Locator loc = page.locator(selector).first();
                if (loc.isVisible()) {
                    log.info("Naukri: found password field with selector: {}", selector);
                    return loc;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Locator findLoginButton(Page page) {
        String[] selectors = {
                "button[type='submit']",
                "button:has-text('Login')",
                "button:has-text('Sign In')",
                "input[type='submit']",
                ".loginButton",
                "div[data-ga-track*='login' i]",
                "button.blue-btn"
        };

        for (String selector : selectors) {
            try {
                Locator loc = page.locator(selector).first();
                if (loc.isVisible()) {
                    log.info("Naukri: found login button with selector: {}", selector);
                    return loc;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ── Search URL builder ────────────────────────────────────────────────────────

    private String buildSearchUrl(JobPreferences prefs) {
        // Parse the first desired role and location from preferences JSON
        String role     = extractFirstValue(prefs.getDesiredRoles(), "java developer");
        String location = extractFirstValue(prefs.getPreferredLocations(), "Hyderabad");

        // URL-encode spaces and special chars
        String encodedRole     = role.replace(" ", "%20");
        String encodedLocation = location.replace(" ", "%20");

        return BASE_URL + "/jobs-listings?k=" + encodedRole + "&l=" + encodedLocation;
    }

    private String extractFirstValue(List<String> values, String defaultValue) {
        if (values == null || values.isEmpty()) return defaultValue;
        String first = values.get(0);
        return (first == null || first.isBlank()) ? defaultValue : first.trim();
    }
//    private String extractFirstValue(String json, String defaultValue) {
//        if (json == null || json.isBlank()) return defaultValue;
//        try {
//            // Simple extraction — json is like ["Java Developer","Software Engineer"]
//            String cleaned = json.replaceAll("[\\[\\]\"]", "").trim();
//            String first = cleaned.contains(",") ? cleaned.substring(0, cleaned.indexOf(",")) : cleaned;
//            return first.isBlank() ? defaultValue : first.trim();
//        } catch (Exception e) {
//            return defaultValue;
//        }
//    }

    // ── Job card scraping ─────────────────────────────────────────────────────────

    private List<ScrapedJobDto> scrapeJobCards(Page page, JobPreferences prefs) {
        List<ScrapedJobDto> jobs = new ArrayList<>();

        try {
            // Wait for job cards to appear
            page.waitForSelector(".cust-job-tuple, .jobTuple, article.jobTupleHeader",
                    new Page.WaitForSelectorOptions().setTimeout(15000));

            // Try multiple known card selectors
            String[] cardSelectors = {
                    ".cust-job-tuple",
                    ".jobTuple",
                    "article.jobTupleHeader",
                    ".job-container"
            };

            List<ElementHandle> cards = new ArrayList<>();
            for (String sel : cardSelectors) {
                cards = page.querySelectorAll(sel);
                if (!cards.isEmpty()) {
                    log.info("Naukri: found {} job cards with selector: {}", cards.size(), sel);
                    break;
                }
            }

            for (ElementHandle card : cards) {
                try {
                    ScrapedJobDto job = extractJobFromCard(card, page);
                    if (job != null) jobs.add(job);
                } catch (Exception e) {
                    log.warn("Naukri: error parsing one job card: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.warn("Naukri: timeout waiting for job cards — page may have changed: {}",
                    e.getMessage());
        }

        return jobs;
    }

    private ScrapedJobDto extractJobFromCard(ElementHandle card, Page page) {
        try {
            String title   = textOrNull(card, ".title, .jobTitle, a.title, h2");
            String company = textOrNull(card, ".comp-name, .companyInfo, .company-name");
            String location = textOrNull(card, ".loc-wrap, .location, .locWdth");
            String salary  = textOrNull(card, ".salary-wrap, .sal-wrap, .sal");
            String exp     = textOrNull(card, ".exp-wrap, .exp");

            // Get the job URL from the title link
            String href = null;
            try {
                ElementHandle link = card.querySelector("a.title, a[title], .jobTitle a");
                if (link != null) href = link.getAttribute("href");
            } catch (Exception ignored) {}

            if (title == null || href == null) return null;

            String jobUrl = href.startsWith("http") ? href : "https://www.naukri.com" + href;

            return ScrapedJobDto.builder()
                    .portal("naukri")
                    .jobTitle(title.trim())
                    .companyName(company != null ? company.trim() : "Unknown")
                    .location(location != null ? location.trim() : "")
                    .jobUrl(jobUrl)
                    .salaryRange(salary != null ? salary.trim() : null)
                    .experienceRequired(exp != null ? exp.trim() : null)
                    .jobType("Full-time")
                    .isEasyApply(false)
                    .description("")
                    .build();

        } catch (Exception e) {
            return null;
        }
    }

    private String textOrNull(ElementHandle card, String selector) {
        try {
            String[] selectors = selector.split(",");
            for (String sel : selectors) {
                ElementHandle el = card.querySelector(sel.trim());
                if (el != null) {
                    String text = el.textContent();
                    if (text != null && !text.isBlank()) return text.trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}