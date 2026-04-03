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
    private static final int MAX_PAGES    = 1;
    private static final int TIMEOUT_MS   = 60000;

    @Override
    public String portalKey() { return "linkedin"; }

    @Override
    public List<ScrapedJobDto> scrape(Page page, String username, String password, JobPreferences prefs) {
        List<ScrapedJobDto> allJobs = new ArrayList<>();
        page.setDefaultTimeout(TIMEOUT_MS);

        log.info("[LinkedIn] Initializing Scraper...");
        safeNavigate(page, BASE_URL);
        StealthUtil.humanDelay(2000, 4000);

        List<String> roles = resolveRoles(prefs);
        String location    = resolveLocation(prefs);

        for (String role : roles) {
            scrapeRole(page, role, location, allJobs);
            StealthUtil.humanDelay(4000, 7000);
        }
        return allJobs;
    }

    private void scrapeRole(Page page, String role, String location, List<ScrapedJobDto> sink) {
        for (int p = 0; p < MAX_PAGES; p++) {
            int offset = p * 25;
            String url = buildUrl(role, location, offset);
            log.info("[LinkedIn] Scraping Role: {} | Page: {}", role, p + 1);

            if (!safeNavigate(page, url)) return;
            if (isAuthWall(page)) return;
            if (!waitForCards(page)) return;

            StealthUtil.slowScroll(page);
            List<ElementHandle> cards = page.querySelectorAll("div.job-card-container");

            int scrapedCount = 0;
            for (ElementHandle card : cards) {
                try {
                    ScrapedJobDto job = extractJob(page, card);
                    if (job != null) {
                        sink.add(job);
                        scrapedCount++;
                        if (scrapedCount >= 3) break; // TESTING LIMIT
                    }
                } catch (Exception e) {
                    log.error("[LinkedIn] Error extracting job card: {}", e.getMessage());
                }
                StealthUtil.humanDelay(2000, 4000);
            }
            if (scrapedCount >= 3) break;
        }
    }

    private ScrapedJobDto extractJob(Page page, ElementHandle card) {
        try {
            String title = textOf(card, "a.job-card-list__title", "a.job-card-container__link");
            String company = textOf(card, ".artdeco-entity-lockup__subtitle", ".job-card-container__primary-description");

            // ✅ UPDATED LOCATION (taken from first method - card level, more reliable)
            String locationVal = textOf(card,
                    ".job-card-container__metadata-item",
                    ".artdeco-entity-lockup__caption",
                    "ul.job-card-container__metadata-wrapper li span",
                    "span.job-search-card__location");

            // Easy Apply (Logic preserved)
            String cardText = card.innerText();
            boolean isEasyApply = cardText != null && cardText.contains("Easy Apply");

            ElementHandle linkEl = card.querySelector("a.job-card-list__title, a.job-card-container__link");
            String href = linkEl != null ? linkEl.getAttribute("href") : null;

            if (title == null || href == null) return null;

            String jobUrl = href.startsWith("http") ? href : BASE_URL + href;

            String description = "";
            String experience = null;
            String salary = null;

            Page detail = null;
            try {
                detail = page.context().newPage();

                if (safeNavigate(detail, jobUrl)) {
                    StealthUtil.humanDelay(3000, 5000);

                    // Description
                    ElementHandle descEl = detail.querySelector("[data-testid='expandable-text-box'], .jobs-description__content");
                    description = (descEl != null) ? descEl.innerText() : "";

                    // Salary & Experience (Insights)
                    List<ElementHandle> insights = detail.querySelectorAll(".jobs-unified-top-card__job-insight, .jobs-description__job-insight-text");

                    for (ElementHandle insight : insights) {
                        String text = insight.innerText();
                        if (text == null) continue;

                        if (text.matches(".*[₹\\$]|LPA|CTC|Salary.*")) {
                            salary = text.trim();
                        }

                        if (text.toLowerCase().contains("yr") ||
                                text.toLowerCase().contains("year") ||
                                text.toLowerCase().contains("experience")) {
                            experience = text.trim();
                        }
                    }

                    // Fallback using regex on description
                    if (salary == null) {
                        salary = regexSearch(description, "(?i)salary[:\\s]+([^\\n]+)");
                    }

                    if (experience == null) {
                        experience = regexSearch(description, "(?i)(\\d+\\+?\\s*(?:-|to)?\\s*\\d*\\s*years?)");
                    }

                    if (locationVal == null) {
                        locationVal = "Not specified";
                    }
                }

            } finally {
                if (detail != null) detail.close();
            }

            return ScrapedJobDto.builder()
                    .portal("linkedin")
                    .jobTitle(clean(title))
                    .companyName(clean(company))
                    .location(clean(locationVal)) //
                    .salaryRange(clean(salary))
                    .experienceRequired(clean(experience))
                    .jobUrl(jobUrl)
                    .jobType("Full-time")
                    .isEasyApply(isEasyApply)
                    .description(clean(description))
                    .build();

        } catch (Exception e) {
            return null;
        }
    }

    private String regexSearch(String text, String patternStr) {
        if (text == null) return null;
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private boolean safeNavigate(Page page, String url) {
        try {
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(TIMEOUT_MS));
            return true;
        } catch (Exception e) { return false; }
    }

    private boolean waitForCards(Page page) {
        try {
            page.waitForSelector("div.job-card-container", new Page.WaitForSelectorOptions().setTimeout(15000));
            return true;
        } catch (Exception e) { return false; }
    }

    private boolean isAuthWall(Page page) {
        return page.url().contains("login") || page.url().contains("checkpoint");
    }

    private String buildUrl(String role, String location, int start) {
        try {
            return JOBS_URL + "?keywords=" + java.net.URLEncoder.encode(role, "UTF-8")
                    + "&location=" + java.net.URLEncoder.encode(location, "UTF-8") + "&start=" + start;
        } catch (Exception e) { return JOBS_URL; }
    }

    private String textOf(ElementHandle root, String... selectors) {
        for (String sel : selectors) {
            ElementHandle el = root.querySelector(sel);
            if (el != null) return el.textContent().trim();
        }
        return null;
    }

    private List<String> resolveRoles(JobPreferences prefs) {
        return (prefs.getDesiredRoles() == null || prefs.getDesiredRoles().isEmpty()) ? List.of("java developer") : prefs.getDesiredRoles();
    }

    private String resolveLocation(JobPreferences prefs) {
        return (prefs.getPreferredLocations() == null || prefs.getPreferredLocations().isEmpty()) ? "Hyderabad" : prefs.getPreferredLocations().get(0);
    }

    private String clean(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }
}