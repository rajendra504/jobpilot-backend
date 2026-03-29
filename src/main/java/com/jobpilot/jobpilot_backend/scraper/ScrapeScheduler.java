package com.jobpilot.jobpilot_backend.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers automatic job scraping on a schedule.
 *
 * Default schedule: every day at 7:00 AM IST (1:30 AM UTC).
 * Override with app.scraper.cron in application.yml.
 *
 * To disable scheduling entirely (e.g., in test profile):
 *   app.scraper.enabled=false   → set the cron to "-" disables the task
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScrapeScheduler {

    private final JobScraperService scraperService;

    /**
     * Runs at 7:00 AM IST every day.
     * Cron: second minute hour day month weekday
     * "0 30 1 * * *" = 1:30 AM UTC = 7:00 AM IST
     */
    @Scheduled(cron = "${app.scraper.cron:0 30 1 * * *}")
    public void scheduledDailyScrape() {
        log.info("=== Scheduled daily scrape started ===");
        try {
            scraperService.scrapeForAllUsers();
            log.info("=== Scheduled daily scrape completed ===");
        } catch (Exception e) {
            log.error("=== Scheduled daily scrape encountered an error: {} ===", e.getMessage(), e);
        }
    }
}