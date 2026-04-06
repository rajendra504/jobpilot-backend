package com.jobpilot.jobpilot_backend.scraper.async;

import com.jobpilot.jobpilot_backend.scraper.JobScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScrapeAsyncService {

    private final JobScraperService scraperService;

    @Async("scraperExecutor")
    public void launchAsync(Long userId) {
        log.info("[ScrapeAsync] Background scrape thread started for userId={}", userId);
        try {
            scraperService.triggerScrapeForUser(userId);
            log.info("[ScrapeAsync] Background scrape finished for userId={}", userId);
        } catch (Exception e) {
            log.error("[ScrapeAsync] Scrape failed for userId={}: {}", userId, e.getMessage(), e);
        }
    }
}