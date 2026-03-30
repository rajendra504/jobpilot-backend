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

@Slf4j
@Component
public class LinkedInScraper implements PortalScraper {

    private static final String BASE_URL  = "https://www.linkedin.com";
    private static final String JOBS_URL  = BASE_URL + "/jobs/search/";
    private static final int    MAX_PAGES = 3;  // 25 results/page → 75 max per role

    // ─── Entry point ──────────────────────────────────────────────────────────

    @Override
    public String portalKey() { return "linkedin"; }

    @Override
    public List<ScrapedJobDto> scrape(Page page, String username,
                                      String password, JobPreferences prefs) {

        List<ScrapedJobDto> allJobs = new ArrayList<>();

        List<String> roles = resolveRoles(prefs);
        String location    = resolveLocation(prefs);

        log.info("[LinkedIn] Scraping {} role(s) in '{}'", roles.size(), location);

        for (String role : roles) {
            log.info("[LinkedIn] → Role: '{}'", role);
            scrapeRole(page, role.trim(), location.trim(), allJobs);
            StealthUtil.humanDelay(4000, 7000);
        }

        log.info("[LinkedIn] Total collected: {} jobs", allJobs.size());
        return allJobs;
    }

    private void scrapeRole(Page page, String role, String location,
                            List<ScrapedJobDto> sink) {
        for (int p = 0; p < MAX_PAGES; p++) {

            int    offset = p * 25;
            String url    = buildListingUrl(role, location, offset);

            log.info("[LinkedIn] Listing page {} → {}", p + 1, url);
            page.navigate(url);

            if (isAuthWall(page)) {
                log.warn("[LinkedIn] Auth wall on page {} for '{}' — stopping", p + 1, role);
                break;
            }

            if (!waitForListingCards(page)) {
                log.warn("[LinkedIn] No cards on page {} for '{}' — stopping", p + 1, role);
                break;
            }

            // Human simulation on listing page
            StealthUtil.slowScroll(page);
            StealthUtil.humanDelay(1500, 3000);

            List<ElementHandle> cards = page.querySelectorAll(
                    "ul.jobs-search-results__list li, div.job-card-container"
            );

            log.info("[LinkedIn] Page {} → {} cards for '{}'", p + 1, cards.size(), role);
            if (cards.isEmpty()) break;

            for (ElementHandle card : cards) {
                try {
                    ScrapedJobDto job = extractJob(page, card);
                    if (job != null) sink.add(job);
                } catch (Exception ignored) {}

                StealthUtil.humanDelay(2500, 4500);
            }

            StealthUtil.humanDelay(4000, 7000);
        }
    }

    private ScrapedJobDto extractJob(Page page, ElementHandle card) {
        try {

            String title = textOf(card,
                    "a.job-card-list__title--link",
                    "a.job-card-list__title",
                    "a.job-card-container__link",
                    ".job-card-list__title");

            String company = textOf(card,
                    ".job-card-container__primary-description",
                    ".artdeco-entity-lockup__subtitle",
                    ".job-card-container__company-name");

            String location = textOf(card,
                    ".job-card-container__metadata-item",
                    ".job-card-container__metadata-wrapper li",
                    ".artdeco-entity-lockup__caption");

            ElementHandle linkEl = card.querySelector(
                    "a.job-card-list__title--link, " +
                            "a.job-card-list__title, " +
                            "a.job-card-container__link"
            );
            String href = linkEl != null ? linkEl.getAttribute("href") : null;
            if (title == null || href == null) return null;

            String jobUrl = href.startsWith("http") ? href : BASE_URL + href;
            if (jobUrl.contains("?")) jobUrl = jobUrl.substring(0, jobUrl.indexOf('?'));

            boolean easyApply = false;
            try { easyApply = card.innerText().contains("Easy Apply"); }
            catch (Exception ignored) {}

            String description = "";
            String experience  = null;
            String salary      = null;
            String jobType     = "Full-time";

            Page detail = null;
            try {
                detail = page.context().newPage();
                log.debug("[LinkedIn] Opening detail: {}", jobUrl);

                detail.navigate(jobUrl);
                detail.waitForLoadState(LoadState.DOMCONTENTLOADED);
                StealthUtil.humanDelay(2500, 4000);

                // Scroll to trigger lazy-loaded content sections
                detail.mouse().wheel(0, 600);
                StealthUtil.humanDelay(800, 1500);
                detail.mouse().wheel(0, 600);
                StealthUtil.humanDelay(500, 1000);


                description = coalesce(
                        textFromPage(detail,
                                "span[data-testid='expandable-text-box']"),
                        textFromPage(detail,
                                "div[data-testid='job-details-description']"),
                        textFromPage(detail,
                                ".jobs-description__content"),
                        textFromPage(detail,
                                ".description__text"),
                        ""
                );

                experience = coalesce(
                        textFromPage(detail,
                                "li[data-testid='job-details-seniority-level'] span:last-child"),
                        textFromPage(detail,
                                "span[data-testid='job-details-seniority-level']"),
                        textFromPage(detail,
                                ".job-details-jobs-unified-top-card__job-insight span"),
                        textFromPage(detail,
                                ".jobs-unified-top-card__job-insight span")
                );

                salary = coalesce(
                        textFromPage(detail,
                                "li[data-testid='job-details-salary'] span:last-child"),
                        textFromPage(detail,
                                "span[data-testid='job-details-salary']"),
                        textFromPage(detail,
                                ".job-details-jobs-unified-top-card__salary-info"),
                        textFromPage(detail,
                                ".jobs-unified-top-card__salary-info")
                );

                String detailWorkplace = coalesce(
                        textFromPage(detail,
                                "li[data-testid='job-details-workplace-type'] span:last-child"),
                        textFromPage(detail,
                                "span[data-testid='job-details-workplace-type']"),
                        textFromPage(detail,
                                ".job-details-jobs-unified-top-card__workplace-type"),
                        textFromPage(detail,
                                ".jobs-unified-top-card__workplace-type")
                );
                if (detailWorkplace != null && !detailWorkplace.isBlank()) {
                    jobType = clean(detailWorkplace);
                }

            } catch (Exception e) {
                log.debug("[LinkedIn] Detail page error for {}: {}", jobUrl, e.getMessage());
            } finally {
                if (detail != null) {
                    try { detail.close(); } catch (Exception ignored) {}
                }
            }

            return ScrapedJobDto.builder()
                    .portal("linkedin")
                    .jobTitle(clean(title))
                    .companyName(clean(company != null ? company : "Unknown"))
                    .location(clean(location))
                    .salaryRange(clean(salary))
                    .experienceRequired(clean(experience))
                    .jobUrl(jobUrl)
                    .jobType(jobType)
                    .isEasyApply(easyApply)
                    .description(clean(description))
                    .build();

        } catch (Exception e) {
            log.debug("[LinkedIn] Card extraction error: {}", e.getMessage());
            return null;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String buildListingUrl(String role, String location, int start) {
        try {
            String r = java.net.URLEncoder.encode(role, "UTF-8");
            String l = java.net.URLEncoder.encode(location, "UTF-8");
            return JOBS_URL + "?keywords=" + r + "&location=" + l + "&start=" + start;
        } catch (Exception e) {
            return JOBS_URL + "?keywords=java+developer&location=Hyderabad&start=" + start;
        }
    }

    private boolean isAuthWall(Page page) {
        String url = page.url();
        return url.contains("/login")
                || url.contains("/authwall")
                || url.contains("/checkpoint")
                || url.contains("/uas/");
    }

    private boolean waitForListingCards(Page page) {
        try {
            page.waitForSelector(
                    "ul.jobs-search-results__list li, div.job-card-container",
                    new Page.WaitForSelectorOptions().setTimeout(10_000)
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