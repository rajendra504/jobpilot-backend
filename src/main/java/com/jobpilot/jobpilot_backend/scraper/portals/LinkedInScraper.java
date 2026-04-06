package com.jobpilot.jobpilot_backend.scraper.portals;

import com.jobpilot.jobpilot_backend.preferences.JobPreferences;
import com.jobpilot.jobpilot_backend.scraper.PortalScraper;
import com.jobpilot.jobpilot_backend.scraper.dto.ScrapedJobDto;
import com.jobpilot.jobpilot_backend.scraper.util.StealthUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LinkedInScraper implements PortalScraper {

    private static final String BASE_URL  = "https://www.linkedin.com";
    private static final String JOBS_URL  = BASE_URL + "/jobs/search/";
    private static final int    MAX_PAGES = 3;
    private static final int    TIMEOUT_MS = 30_000;

    @Override
    public String portalKey() { return "linkedin"; }

    @Override
    public List<ScrapedJobDto> scrape(Page page, String username, String password, JobPreferences prefs) {
        List<ScrapedJobDto> allJobs = new ArrayList<>();
        page.setDefaultTimeout(TIMEOUT_MS);

        log.info("[LinkedIn] Initializing Scraper...");

        safeNavigate(page, BASE_URL);
        StealthUtil.humanDelay(3000, 5000);
        StealthUtil.randomMouseMove(page);

        log.info("[LinkedIn] After warmup, URL: {}", page.url());

        if (page.url().contains("login") || page.url().contains("authwall")) {
            log.warn("[LinkedIn] Session not authenticated — need to re-init session via POST /api/sessions/init?portal=linkedin");
            return allJobs;
        }

        List<String> roles    = resolveRoles(prefs);
        String       location = resolveLocation(prefs);

        for (String role : roles) {
            scrapeRole(page, role, location, allJobs);
            StealthUtil.humanDelay(5000, 9000);
        }
        return allJobs;
    }

    private void scrapeRole(Page page, String role, String location, List<ScrapedJobDto> sink) {
        for (int p = 0; p < MAX_PAGES; p++) {
            int offset = p * 25;
            String url = buildUrl(role, location, offset);
            log.info("[LinkedIn] Scraping Role: {} | Page: {}", role, p + 1);

            if (!safeNavigate(page, url)) {
                log.warn("[LinkedIn] Navigation failed for role '{}' page {}", role, p + 1);
                return;
            }

            StealthUtil.humanDelay(3000, 5000);
            log.info("[LinkedIn] After navigation, URL: {}", page.url());

            // Detect auth wall
            if (isAuthWall(page)) {
                log.warn("[LinkedIn] Auth wall detected for role '{}' — session expired or bot-detected", role);
                return;
            }

            String pageUrl = page.url();
            if (pageUrl.contains("challenge") || pageUrl.contains("captcha") || pageUrl.contains("verify")) {
                log.warn("[LinkedIn] Challenge/CAPTCHA page detected for role '{}' — server IP blocked", role);
                return;
            }

            if (!waitForCards(page)) {
                try {
                    log.warn("[LinkedIn] No job cards found for '{}'. URL: {} | Title: '{}'",
                            role, page.url(), page.title());
                } catch (Exception ignored) {
                    log.warn("[LinkedIn] No job cards found for '{}'", role);
                }
                return;
            }

            StealthUtil.slowScroll(page);
            StealthUtil.humanDelay(2000, 3000);

            List<ElementHandle> cards = page.querySelectorAll(
                    "div.job-card-container, li.jobs-search-results__list-item, " +
                            ".base-card, [data-job-id], li[class*='result']"
            );

            log.info("[LinkedIn] Found {} cards for role: {}", cards.size(), role);

            for (ElementHandle card : cards) {
                try {
                    ScrapedJobDto job = extractJob(page, card);
                    if (job != null) sink.add(job);
                } catch (Exception e) {
                    log.debug("[LinkedIn] Card error: {}", e.getMessage());
                }
                StealthUtil.humanDelay(1000, 2500);
            }

            StealthUtil.humanDelay(3000, 5000);
        }
    }

    private ScrapedJobDto extractJob(Page page, ElementHandle card) {
        try {

            String title = textOf(card,
                    "a.job-card-list__title",
                    "a.job-card-container__link",
                    ".base-search-card__title",
                    "h3.base-search-card__title",
                    "a[data-control-name='jobcard_title']",
                    ".job-card-list__title--link"
            );

            String company = textOf(card,
                    ".artdeco-entity-lockup__subtitle",
                    ".job-card-container__primary-description",
                    ".base-search-card__subtitle",
                    "h4.base-search-card__subtitle",
                    ".job-card-container__company-name"
            );

            String locationVal = textOf(card,
                    ".job-card-container__metadata-item",
                    ".artdeco-entity-lockup__caption",
                    ".base-search-card__metadata",
                    "span.job-search-card__location",
                    ".job-card-container__metadata-wrapper li span"
            );

            String cardText = "";
            try { cardText = card.innerText(); } catch (Exception ignored) {}
            boolean isEasyApply = cardText.contains("Easy Apply");

            ElementHandle linkEl = card.querySelector(
                    "a.job-card-list__title, a.job-card-container__link, " +
                            "a.base-card__full-link, a[data-control-name='jobcard_title']"
            );
            String href = linkEl != null ? linkEl.getAttribute("href") : null;

            if (title == null && linkEl != null) {
                title = linkEl.textContent();
            }
            if (title == null || href == null) return null;

            String jobUrl = href.startsWith("http") ? href : BASE_URL + href;
            if (jobUrl.contains("?")) jobUrl = jobUrl.substring(0, jobUrl.indexOf("?"));

            String description = "";
            String experience  = null;
            String salary      = null;

            Page detail = null;
            try {
                detail = page.context().newPage();
                detail.setDefaultTimeout(20_000);

                if (safeNavigate(detail, jobUrl)) {
                    StealthUtil.humanDelay(2000, 4000);

                    ElementHandle descEl = detail.querySelector(
                            "[data-testid='expandable-text-box']," +
                                    ".jobs-description__content," +
                                    ".show-more-less-html__markup," +
                                    ".jobs-box__html-content"
                    );
                    if (descEl != null) description = descEl.innerText();

                    List<ElementHandle> insights = detail.querySelectorAll(
                            ".jobs-unified-top-card__job-insight," +
                                    ".job-details-jobs-unified-top-card__job-insight," +
                                    "[class*='job-insight']"
                    );
                    for (ElementHandle insight : insights) {
                        try {
                            String text = insight.innerText();
                            if (text == null) continue;
                            if (text.matches(".*[₹$].*|.*LPA.*|.*CTC.*|.*Salary.*")) salary = text.trim();
                            if (text.toLowerCase().contains("yr") || text.toLowerCase().contains("year")
                                    || text.toLowerCase().contains("experience")) experience = text.trim();
                        } catch (Exception ignored) {}
                    }

                    if (salary == null) salary = regexSearch(description, "(?i)salary[:\\s]+([^\\n]+)");
                    if (experience == null) experience = regexSearch(description, "(?i)(\\d+\\+?\\s*(?:-|to)?\\s*\\d*\\s*years?)");
                }
            } catch (Exception e) {
                log.debug("[LinkedIn] Detail page error for {}: {}", jobUrl, e.getMessage());
            } finally {
                if (detail != null) { try { detail.close(); } catch (Exception ignored) {} }
            }

            return ScrapedJobDto.builder()
                    .portal("linkedin")
                    .jobTitle(clean(title))
                    .companyName(clean(company))
                    .location(clean(locationVal != null ? locationVal : "Not specified"))
                    .salaryRange(clean(salary))
                    .experienceRequired(clean(experience))
                    .jobUrl(jobUrl)
                    .jobType("Full-time")
                    .isEasyApply(isEasyApply)
                    .description(clean(description))
                    .build();

        } catch (Exception e) {
            log.debug("[LinkedIn] Card extraction error: {}", e.getMessage());
            return null;
        }
    }

    private String regexSearch(String text, String patternStr) {
        if (text == null) return null;
        Matcher m = Pattern.compile(patternStr).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private boolean safeNavigate(Page page, String url) {
        try {
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(TIMEOUT_MS));
            return true;
        } catch (Exception e) {
            log.debug("[LinkedIn] Navigation failed for {}: {}", url, e.getMessage());
            return false;
        }
    }

    private boolean waitForCards(Page page) {
        try {
            page.waitForSelector(
                    "div.job-card-container, li.jobs-search-results__list-item, " +
                            ".base-card, [data-job-id]",
                    new Page.WaitForSelectorOptions().setTimeout(15_000)
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAuthWall(Page page) {
        String url = page.url();
        if (url.contains("login") || url.contains("checkpoint") || url.contains("authwall"))
            return true;
        try {
            return page.querySelector(".authwall-join-form, #join-form, [data-id='authwall']") != null;
        } catch (Exception e) { return false; }
    }

    private String buildUrl(String role, String location, int start) {
        try {
            return JOBS_URL
                    + "?keywords=" + java.net.URLEncoder.encode(role, "UTF-8")
                    + "&location=" + java.net.URLEncoder.encode(location, "UTF-8")
                    + "&start=" + start
                    + "&f_TPR=r604800"; // past week — improves relevance
        } catch (Exception e) {
            return JOBS_URL;
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

    private List<String> resolveRoles(JobPreferences prefs) {
        if (prefs.getDesiredRoles() == null || prefs.getDesiredRoles().isEmpty())
            return List.of("java developer");
        return prefs.getDesiredRoles().stream()
                .filter(r -> r != null && !r.isBlank()).toList();
    }

    private String resolveLocation(JobPreferences prefs) {
        if (prefs.getPreferredLocations() == null || prefs.getPreferredLocations().isEmpty())
            return "Hyderabad, India";
        String loc = prefs.getPreferredLocations().get(0);
        return (loc == null || loc.isBlank()) ? "Hyderabad, India" : loc.trim();
    }

    private String clean(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }
}