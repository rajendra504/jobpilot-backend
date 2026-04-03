package com.jobpilot.jobpilot_backend.scraper.portals;

import com.jobpilot.jobpilot_backend.preferences.JobPreferences;
import com.jobpilot.jobpilot_backend.scraper.PortalScraper;
import com.jobpilot.jobpilot_backend.scraper.dto.ScrapedJobDto;
import com.jobpilot.jobpilot_backend.scraper.util.StealthUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Naukri.com job scraper.
 *
 * Session strategy  : cookies injected into BrowserContext by JobScraperService BEFORE this runs.
 * Pagination engine : Next-button click — Naukri uses server-side pagination.
 * Description       : fetched from each job detail page.
 *
 * SALARY/EXPERIENCE FIX:
 * Naukri cards render experience and salary as SEPARATE <li> elements inside
 * a <ul class="tags-gt"> list — NOT as a combined string.
 * The old .salary selector was matching a wrapper that contained both, causing
 * experience to bleed into salaryRange.
 * Fix: target each <li> individually by position or by icon data-testid.
 *
 * Human simulation  : slow scroll on listing pages + per-job delay on detail tabs.
 * Anti-block        : home-page warm-up, waitForCards() guard, tab always closed in finally.
 */
@Slf4j
@Component
public class NaukriScraper implements PortalScraper {

    private static final String BASE_URL   = "https://www.naukri.com";
    private static final int    MAX_PAGES  = 1;
    private static final int    TIMEOUT_MS = 60_000;

    // ─── Entry point ──────────────────────────────────────────────────────────

    @Override
    public String portalKey() { return "naukri"; }

    @Override
    public List<ScrapedJobDto> scrape(Page page, String username,
                                      String password, JobPreferences prefs) {

        List<ScrapedJobDto> allJobs = new ArrayList<>();

        page.setDefaultTimeout(TIMEOUT_MS);

        // Warm-up: home page first — establishes cookies, avoids cold-start block
        log.info("[Naukri] Warming up via home page");
        page.navigate(BASE_URL);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        StealthUtil.humanDelay(2000, 4000);

        List<String> roles = resolveRoles(prefs);
        String location    = resolveLocation(prefs);

        log.info("[Naukri] Scraping {} role(s) in '{}'", roles.size(), location);

        for (String role : roles) {
            log.info("[Naukri] → Role: '{}'", role);
            scrapeRole(page, role.trim(), location.trim(), allJobs);
            StealthUtil.humanDelay(4000, 7000); // human gap between role searches
        }

        log.info("[Naukri] Total collected: {} jobs", allJobs.size());
        return allJobs;
    }

    // ─── Per-role listing loop ────────────────────────────────────────────────

    /* ================================================================================
    OLD CODE (Commented out for production use later)
    ================================================================================
    private void scrapeRole(Page page, String role, String location,
                            List<ScrapedJobDto> sink) {

        String searchUrl = buildSearchUrl(role, location);
        log.info("[Naukri] Search URL: {}", searchUrl);

        page.navigate(searchUrl);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        if (!waitForCards(page)) {
            log.error("[Naukri] No cards found for '{}' — blocked or structure changed", role);
            return;
        }

        StealthUtil.humanDelay(2000, 4000);

        for (int p = 0; p < MAX_PAGES; p++) {

            log.info("[Naukri] Scraping listing page {} for '{}'", p + 1, role);

            StealthUtil.slowScroll(page);
            StealthUtil.humanDelay(1500, 3000);

            List<ElementHandle> cards = page.querySelectorAll(
                    "div.jobTuple, article.jobTuple, .cust-job-tuple"
            );

            log.info("[Naukri] Page {} → {} cards for '{}'", p + 1, cards.size(), role);
            if (cards.isEmpty()) break;

            for (ElementHandle card : cards) {
                try {
                    ScrapedJobDto job = extractJob(page, card);
                    if (job != null) sink.add(job);
                } catch (Exception ignored) {}

                // Per-job delay — rapid tab opens look like a bot
                StealthUtil.humanDelay(1500, 3000);
            }

            ElementHandle nextBtn = page.querySelector("a.fright, a[title='Next']");
            if (nextBtn == null) {
                log.info("[Naukri] No next button — end of results for '{}'", role);
                break;
            }

            nextBtn.click();
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            if (!waitForCards(page)) {
                log.warn("[Naukri] Cards not found after Next click — stopping");
                break;
            }

            StealthUtil.humanDelay(3000, 5000);
        }
    }
    ================================================================================
    */

    // ================================================================================
    // NEW CODE (Testing Limit: 3 jobs per role)
    // ================================================================================
    private void scrapeRole(Page page, String role, String location,
                            List<ScrapedJobDto> sink) {

        String searchUrl = buildSearchUrl(role, location);
        log.info("[Naukri] Search URL: {}", searchUrl);

        page.navigate(searchUrl);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        if (!waitForCards(page)) {
            log.error("[Naukri] No cards found for '{}' — blocked or structure changed", role);
            return;
        }

        StealthUtil.humanDelay(2000, 4000);

        int scrapedCount = 0; // Added counter for testing limits

        for (int p = 0; p < MAX_PAGES; p++) {

            log.info("[Naukri] Scraping listing page {} for '{}'", p + 1, role);

            StealthUtil.slowScroll(page);
            StealthUtil.humanDelay(1500, 3000);

            List<ElementHandle> cards = page.querySelectorAll(
                    "div.jobTuple, article.jobTuple, .cust-job-tuple"
            );

            log.info("[Naukri] Page {} → {} cards for '{}'", p + 1, cards.size(), role);
            if (cards.isEmpty()) break;

            for (ElementHandle card : cards) {
                try {
                    ScrapedJobDto job = extractJob(page, card);
                    if (job != null) {
                        sink.add(job);
                        scrapedCount++; // Increment test counter

                        // Limit to 3 jobs for testing purposes
                        if (scrapedCount >= 3) {
                            log.info("[Naukri] Testing limit reached: 3 jobs for role '{}'", role);
                            break;
                        }
                    }
                } catch (Exception ignored) {}

                StealthUtil.humanDelay(1500, 3000);
            }

            // Break out of pagination loop if we hit the limit
            if (scrapedCount >= 3) {
                break;
            }

            ElementHandle nextBtn = page.querySelector("a.fright, a[title='Next']");
            if (nextBtn == null) {
                log.info("[Naukri] No next button — end of results for '{}'", role);
                break;
            }

            nextBtn.click();
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            if (!waitForCards(page)) {
                log.warn("[Naukri] Cards not found after Next click — stopping");
                break;
            }

            StealthUtil.humanDelay(3000, 5000);
        }
    }
    // ================================================================================

    // ─── Card + detail page extraction ───────────────────────────────────────

    private ScrapedJobDto extractJob(Page page, ElementHandle card) {
        try {
            // ── Title ─────────────────────────────────────────────────────────
            String title = textOf(card, "a.title", "a.jobTitle");

            // ── Company ───────────────────────────────────────────────────────
            String company = textOf(card, ".comp-name");

            // ── Location ──────────────────────────────────────────────────────
            String loc = textOf(card, ".locWdth");

            // ── EXPERIENCE — TARGET THE SPECIFIC LI, NOT THE WRAPPER ──────────
            String exp = textOf(card,
                    ".exp",                        // primary: dedicated experience element
                    ".experience",
                    "li.exp",
                    "[class*='exp']:first-child"
            );

            // ── SALARY — TARGET THE SPECIFIC LI, NOT THE WRAPPER ─────────────
            String salary = textOf(card,
                    ".sal",                         // primary: dedicated salary element
                    ".salary",
                    "li.salary",
                    "[class*='sal']:not([class*='exp'])"
            );

            // ── Safety net: if they're the same string, one selector hit the wrapper ──
            if (exp != null && salary != null && exp.equals(salary)) {
                String[] parts = exp.split("\\|");
                if (parts.length >= 2) {
                    exp    = parts[0].trim();
                    salary = parts[1].trim();
                } else {
                    salary = null;
                }
            }

            // ── Additional safety: if exp looks like a salary, swap them ──────
            if (exp != null && looksLikeSalary(exp) && (salary == null || salary.isBlank())) {
                salary = exp;
                exp    = null;
            }
            if (salary != null && looksLikeExperience(salary) && (exp == null || exp.isBlank())) {
                exp    = salary;
                salary = null;
            }

            // ── Job URL ───────────────────────────────────────────────────────
            ElementHandle linkEl = card.querySelector("a.title, a.jobTitle");
            String href = linkEl != null ? linkEl.getAttribute("href") : null;

            if (title == null || href == null) return null;

            String jobUrl = href.startsWith("http") ? href : BASE_URL + href;

            // ── Detail page: fetch description + salary fallback ──────────────
            String description = "";
            Page detail = null;
            try {
                detail = page.context().newPage();
                detail.navigate(jobUrl);
                detail.waitForLoadState(LoadState.DOMCONTENTLOADED);
                StealthUtil.humanDelay(1500, 3000);

                detail.mouse().wheel(0, 400);
                StealthUtil.humanDelay(600, 1200);

                description = coalesce(
                        textFromPage(detail, ".job-desc"),
                        textFromPage(detail, ".job-description"),
                        textFromPage(detail, "[class*='job-desc']"),
                        textFromPage(detail, ".descript"),
                        ""
                );

                // Salary fallback from detail page if card had none
                if (salary == null || salary.isBlank()) {
                    String detailSalary = coalesce(
                            textFromPage(detail, ".sal"),
                            textFromPage(detail, ".salary"),
                            textFromPage(detail, "[class*='sal']")
                    );
                    if (detailSalary != null && !looksLikeExperience(detailSalary)) {
                        salary = detailSalary;
                    }
                }

            } catch (Exception e) {
                log.debug("[Naukri] Detail page error for {}: {}", jobUrl, e.getMessage());
            } finally {
                if (detail != null) {
                    try { detail.close(); } catch (Exception ignored) {}
                }
            }

            return ScrapedJobDto.builder()
                    .portal("naukri")
                    .jobTitle(clean(title))
                    .companyName(clean(company != null ? company : "Unknown"))
                    .location(clean(loc))
                    .salaryRange(clean(salary))
                    .experienceRequired(clean(exp))
                    .jobUrl(jobUrl)
                    .jobType("Full-time")
                    .isEasyApply(false)
                    .description(clean(description))
                    .build();

        } catch (Exception e) {
            log.debug("[Naukri] Card extraction error: {}", e.getMessage());
            return null;
        }
    }

    // ─── Classification helpers ───────────────────────────────────────────────

    private boolean looksLikeSalary(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase();
        return lower.contains("lpa")
                || lower.contains("lakh")
                || lower.contains("lac")
                || lower.contains("ctc")
                || lower.contains("pa")
                || lower.contains("₹")
                || lower.contains("not disclosed")
                || lower.contains("salary");
    }

    private boolean looksLikeExperience(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase();
        return lower.contains("yr")
                || lower.contains("year")
                || lower.contains("fresher")
                || lower.matches(".*\\d+\\s*-\\s*\\d+.*");
    }

    // ─── Standard helpers ─────────────────────────────────────────────────────

    private String buildSearchUrl(String role, String location) {
        String r = role.trim().toLowerCase().replace(" ", "-");
        String l = location.trim().toLowerCase().replace(" ", "-");
        return BASE_URL + "/" + r + "-jobs-in-" + l;
    }

    private boolean waitForCards(Page page) {
        try {
            page.waitForSelector(
                    "div.jobTuple, article.jobTuple, .cust-job-tuple",
                    new Page.WaitForSelectorOptions().setTimeout(15_000)
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String textOf(ElementHandle root, String... selectors) {
        for (String sel : selectors) {
            try {
                ElementHandle el = root.querySelector(sel);
                if (el != null) {
                    String txt = el.textContent();
                    if (txt != null && !txt.isBlank()) return txt.trim();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String textFromPage(Page page, String selector) {
        try {
            ElementHandle el = page.querySelector(selector);
            if (el != null) {
                String txt = el.textContent();
                if (txt != null && !txt.isBlank()) return txt.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    @SafeVarargs
    private <T extends String> T coalesce(T... values) {
        for (T v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private List<String> resolveRoles(JobPreferences prefs) {
        if (prefs.getDesiredRoles() == null || prefs.getDesiredRoles().isEmpty())
            return List.of("java developer");
        List<String> roles = prefs.getDesiredRoles().stream()
                .filter(r -> r != null && !r.isBlank())
                .toList();
        return roles.isEmpty() ? List.of("java developer") : roles;
    }

    private String resolveLocation(JobPreferences prefs) {
        if (prefs.getPreferredLocations() == null || prefs.getPreferredLocations().isEmpty())
            return "Hyderabad";
        String loc = prefs.getPreferredLocations().get(0);
        return (loc == null || loc.isBlank()) ? "Hyderabad" : loc.trim();
    }

    private String clean(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }
}