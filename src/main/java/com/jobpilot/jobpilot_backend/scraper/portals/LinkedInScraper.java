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
 * LinkedIn scraper — uses session cookies instead of credential login.
 *
 * WHY: LinkedIn triggers OTP/2FA and CAPTCHA for automated logins.
 * The solution: user logs in once manually via POST /api/sessions/init
 * → session cookies are saved. This scraper reuses those cookies.
 * LinkedIn sees the same browser session — no challenge triggered.
 *
 * NOTE: The username/password params are ignored by this scraper.
 * The BrowserContext already has cookies loaded by JobScraperService
 * before this scrape() method is called.
 */
@Slf4j
@Component
public class LinkedInScraper implements PortalScraper {

    private static final String JOBS_URL   = "https://www.linkedin.com/jobs/search/";
    private static final int    TIMEOUT_MS = 60_000;

    @Override
    public String portalKey() {
        return "linkedin";
    }

    @Override
    public List<ScrapedJobDto> scrape(Page page, String username,
                                      String password, JobPreferences prefs) {
        List<ScrapedJobDto> results = new ArrayList<>();

        try {
            page.setDefaultTimeout(TIMEOUT_MS);

            // ── Step 1: Navigate to LinkedIn jobs ─────────────────────────────
            // Cookies are already loaded into the BrowserContext before this call.
            // If the session is valid, LinkedIn will skip login entirely.
            String searchUrl = buildSearchUrl(prefs);
            log.info("LinkedIn navigating to: {}", searchUrl);

            page.navigate(searchUrl);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(3000);

            // ── Step 2: Check if we are logged in ─────────────────────────────
            String currentUrl = page.url();
            log.info("LinkedIn current URL after navigation: {}", currentUrl);

            if (currentUrl.contains("linkedin.com/login") ||
                    currentUrl.contains("linkedin.com/checkpoint")) {
                log.warn("LinkedIn: session expired or invalid — user needs to re-login.");
                log.warn("Call POST /api/sessions/init?portal=linkedin to create a fresh session.");
                return results;
            }

            // ── Step 3: Wait for job cards ─────────────────────────────────────
            log.info("LinkedIn: session valid. Waiting for job cards.");

            try {
                page.waitForSelector(
                        ".jobs-search__results-list, .scaffold-layout__list, ul.jobs-search-results__list",
                        new Page.WaitForSelectorOptions().setTimeout(20000)
                );
            } catch (Exception e) {
                log.warn("LinkedIn: job cards did not appear in time — page structure may have changed");
                return results;
            }

            page.waitForTimeout(2000);   // let lazy-loaded content render

            // ── Step 4: Scrape job cards ───────────────────────────────────────
            results = scrapeJobCards(page, prefs);
            log.info("LinkedIn scraper collected {} jobs", results.size());

        } catch (Exception e) {
            log.error("LinkedIn scraping failed: {}", e.getMessage());
        }

        return results;
    }

    private String buildSearchUrl(JobPreferences prefs) {
        String role     = extractFirstValue(prefs.getDesiredRoles(), "java developer");
        String location = extractFirstValue(prefs.getPreferredLocations(), "Hyderabad");

        String encodedRole     = role.replace(" ", "%20");
        String encodedLocation = location.replace(" ", "%20");

        return JOBS_URL + "?keywords=" + encodedRole
                + "&location=" + encodedLocation
                + "&f_AL=true"            // Easy Apply filter
                + "&sortBy=R";            // Most relevant
    }

//    private String extractFirstValue(String json, String defaultValue) {
//        if (json == null || json.isBlank()) return defaultValue;
//        try {
//            String cleaned = json.replaceAll("[\\[\\]\"]", "").trim();
//            String first = cleaned.contains(",")
//                    ? cleaned.substring(0, cleaned.indexOf(","))
//                    : cleaned;
//            return first.isBlank() ? defaultValue : first.trim();
//        } catch (Exception e) {
//            return defaultValue;
//        }
//    }
    private String extractFirstValue(List<String> values, String defaultValue) {
        if (values == null || values.isEmpty()) return defaultValue;
        String first = values.get(0);
        return (first == null || first.isBlank()) ? defaultValue : first.trim();
    }

    private List<ScrapedJobDto> scrapeJobCards(Page page, JobPreferences prefs) {
        List<ScrapedJobDto> jobs = new ArrayList<>();

        String[] cardSelectors = {
                "ul.jobs-search-results__list li",
                ".jobs-search__results-list li",
                ".scaffold-layout__list-container li",
                "div.job-card-container"
        };

        List<ElementHandle> cards = new ArrayList<>();
        for (String sel : cardSelectors) {
            cards = page.querySelectorAll(sel);
            if (!cards.isEmpty()) {
                log.info("LinkedIn: found {} job cards with selector '{}'", cards.size(), sel);
                break;
            }
        }

        if (cards.isEmpty()) {
            log.warn("LinkedIn: no job cards found. Page title: {}", page.title());
        }

        for (ElementHandle card : cards) {
            try {
                ScrapedJobDto job = extractJobFromCard(card);
                if (job != null) jobs.add(job);
            } catch (Exception e) {
                log.warn("LinkedIn: error parsing one card: {}", e.getMessage());
            }
        }

        return jobs;
    }

    private ScrapedJobDto extractJobFromCard(ElementHandle card) {
        try {
            String title   = textOrNull(card,
                    ".job-card-list__title, .jobs-unified-top-card__job-title, a.job-card-container__link");
            String company = textOrNull(card,
                    ".job-card-container__primary-description, .job-card-list__company-name");
            String location = textOrNull(card,
                    ".job-card-container__metadata-item, .job-card-list__footer-wrapper");

            String href = null;
            try {
                ElementHandle link = card.querySelector("a.job-card-container__link, a[data-control-name='job_card_title']");
                if (link != null) href = link.getAttribute("href");
            } catch (Exception ignored) {}

            if (title == null) return null;

            String jobUrl = href != null
                    ? (href.startsWith("http") ? href : "https://www.linkedin.com" + href)
                    : "https://www.linkedin.com/jobs/";

            boolean isEasyApply = card.textContent().contains("Easy Apply");

            return ScrapedJobDto.builder()
                    .portal("linkedin")
                    .jobTitle(title.trim())
                    .companyName(company != null ? company.trim() : "Unknown")
                    .location(location != null ? location.trim() : "")
                    .jobUrl(jobUrl)
                    .isEasyApply(isEasyApply)
                    .jobType("Full-time")
                    .description("")
                    .salaryRange(null)
                    .experienceRequired(null)
                    .build();

        } catch (Exception e) {
            return null;
        }
    }

    private String textOrNull(ElementHandle card, String selector) {
        try {
            String[] parts = selector.split(",");
            for (String s : parts) {
                ElementHandle el = card.querySelector(s.trim());
                if (el != null) {
                    String text = el.textContent();
                    if (text != null && !text.isBlank()) return text.trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}