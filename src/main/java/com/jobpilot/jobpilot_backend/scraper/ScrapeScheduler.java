package com.jobpilot.jobpilot_backend.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScrapeScheduler {

    private final JobScraperService scraperService;

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