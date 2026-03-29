package com.jobpilot.jobpilot_backend.scraper;

import com.jobpilot.jobpilot_backend.preferences.JobPreferences;
import com.jobpilot.jobpilot_backend.scraper.dto.ScrapedJobDto;
import com.microsoft.playwright.Page;

import java.util.List;

/**
 * Contract that every portal scraper must implement.
 * Each portal (LinkedIn, Naukri, etc.) has its own implementation class.
 *
 * JobScraperService iterates over all PortalScraper beans and calls scrape()
 * for each portal where the user has saved credentials.
 */
public interface PortalScraper {

    /**
     * Returns the portal key this scraper handles, e.g. "linkedin", "naukri".
     * Must match exactly what the user stores in portal_credentials JSON.
     */
    String portalKey();

    /**
     * Scrapes job listings matching the user's preferences.
     *
     * @param page        Playwright Page — already authenticated (logged in) by this method
     * @param username    Decrypted portal username
     * @param password    Decrypted portal password
     * @param preferences User's job preferences to build search queries
     * @return List of raw scraped job DTOs (not yet persisted)
     */
    List<ScrapedJobDto> scrape(Page page, String username, String password,
                               JobPreferences preferences);
}