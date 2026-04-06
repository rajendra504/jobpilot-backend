package com.jobpilot.jobpilot_backend.scraper.portals;

import com.jobpilot.jobpilot_backend.preferences.JobPreferences;
import com.jobpilot.jobpilot_backend.scraper.PortalScraper;
import com.jobpilot.jobpilot_backend.scraper.dto.ScrapedJobDto;
import com.jobpilot.jobpilot_backend.scraper.util.StealthUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class NaukriScraper implements PortalScraper {

    private static final String BASE_URL   = "https://www.naukri.com";
    private static final int    MAX_PAGES  = 3;
    private static final int    TIMEOUT_MS = 30_000;

    @Override
    public String portalKey() { return "naukri"; }

    @Override
    public List<ScrapedJobDto> scrape(Page page, String username,
                                      String password, JobPreferences prefs) {

        List<ScrapedJobDto> allJobs = new ArrayList<>();
        page.setDefaultTimeout(TIMEOUT_MS);


        try {
            page.navigate(BASE_URL, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(TIMEOUT_MS));
        } catch (Exception e) {
            log.warn("[Naukri] Warmup failed: {}", e.getMessage());
        }

        StealthUtil.randomMouseMove(page);
        StealthUtil.humanDelay(3000, 5000);

        List<String> roles = resolveRoles(prefs);
        String location = resolveLocation(prefs);

        for (String role : roles) {
            scrapeRole(page, role.trim(), location.trim(), allJobs);
            StealthUtil.humanDelay(4000, 8000);
        }

        log.info("[Naukri] Total collected: {}", allJobs.size());
        return allJobs;
    }

    private void scrapeRole(Page page, String role, String location,
                            List<ScrapedJobDto> sink) {

        String searchUrl = buildSearchUrl(role, location);
        log.info("[Naukri] Searching: {}", searchUrl);


        try {
            page.navigate(searchUrl, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(TIMEOUT_MS));
        } catch (Exception e) {
            log.warn("[Naukri] Navigation failed: {}", e.getMessage());
            return;
        }

        try {
            String title = page.title();
            if (title.contains("Access Denied") || title.contains("Robot")) {
                log.error("[Naukri] BLOCKED — IP flagged. title={}", title);
                return;
            }
        } catch (Exception ignored) {}

        // Optional extra check
        try {
            String html = page.content().toLowerCase();
            if (html.contains("captcha") || html.contains("blocked")) {
                log.error("[Naukri] CAPTCHA/BLOCK detected");
                return;
            }
        } catch (Exception ignored) {}

        StealthUtil.randomMouseMove(page);
        StealthUtil.humanDelay(3000, 5000);
        StealthUtil.slowScroll(page);

        if (!waitForCards(page)) {
            log.error("[Naukri] No job cards found for '{}'", role);
            return;
        }

        for (int p = 0; p < MAX_PAGES; p++) {

            log.info("[Naukri] Page {}", p + 1);

            List<ElementHandle> cards = page.querySelectorAll(
                    ".srp-jobtuple-wrapper, div.jobTuple, article.jobTuple, .cust-job-tuple, [class*='jobtuple']"
            );

            if (cards.isEmpty()) break;

            for (ElementHandle card : cards) {
                try {
                    ScrapedJobDto job = extractJob(page, card);
                    if (job != null) sink.add(job);
                } catch (Exception ignored) {}
            }

            ElementHandle nextBtn = page.querySelector(
                    "a[aria-label='Next'], a[title='Next'], button[aria-label='Next page']"
            );

            if (nextBtn == null) {
                log.info("[Naukri] No more pages");
                break;
            }

            try {
                nextBtn.click();

                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                StealthUtil.humanDelay(3000, 5000);

                if (!waitForCards(page)) {
                    log.warn("[Naukri] No cards after next");
                    break;
                }

            } catch (Exception e) {
                log.warn("[Naukri] Next click failed: {}", e.getMessage());
                break;
            }
        }
    }

    private ScrapedJobDto extractJob(Page page, ElementHandle card) {
        try {

            String title = textOf(card,
                    "a.title", "a.jobTitle", "[class*='title'] a");

            String company = textOf(card,
                    ".comp-name", ".company-name", "[class*='comp']");

            String loc = textOf(card,
                    ".locWdth", ".location", "[class*='loc']");

            String exp = textOf(card,
                    ".exp", "[class*='exp']");

            String salary = textOf(card,
                    ".sal", "[class*='sal']");

            ElementHandle linkEl = card.querySelector("a[href]");
            if (linkEl == null) return null;

            String href = linkEl.getAttribute("href");
            if (href == null) return null;

            String jobUrl = href.startsWith("http") ? href : BASE_URL + href;

            return ScrapedJobDto.builder()
                    .portal("naukri")
                    .jobTitle(clean(title))
                    .companyName(clean(company))
                    .location(clean(loc))
                    .salaryRange(clean(salary))
                    .experienceRequired(clean(exp))
                    .jobUrl(jobUrl)
                    .jobType("Full-time")
                    .isEasyApply(false)
                    .build();

        } catch (Exception e) {
            return null;
        }
    }

    private boolean waitForCards(Page page) {
        try {
            page.waitForSelector(
                    ".srp-jobtuple-wrapper, div.jobTuple, article.jobTuple, .cust-job-tuple",
                    new Page.WaitForSelectorOptions().setTimeout(20_000)
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

    private String buildSearchUrl(String role, String location) {
        return BASE_URL + "/" +
                role.toLowerCase().replace(" ", "-") +
                "-jobs-in-" +
                location.toLowerCase().replace(" ", "-");
    }

    private List<String> resolveRoles(JobPreferences prefs) {
        if (prefs.getDesiredRoles() == null || prefs.getDesiredRoles().isEmpty())
            return List.of("java developer");
        return prefs.getDesiredRoles();
    }

    private String resolveLocation(JobPreferences prefs) {
        if (prefs.getPreferredLocations() == null || prefs.getPreferredLocations().isEmpty())
            return "Hyderabad";
        return prefs.getPreferredLocations().get(0);
    }

    private String clean(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }
}