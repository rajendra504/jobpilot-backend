package com.jobpilot.jobpilot_backend.scraper;

import com.jobpilot.jobpilot_backend.preferences.JobPreferences;
import com.jobpilot.jobpilot_backend.scraper.dto.ScrapedJobDto;
import com.microsoft.playwright.Page;

import java.util.List;

public interface PortalScraper {

    String portalKey();

    List<ScrapedJobDto> scrape(Page page, String username, String password,
                               JobPreferences preferences);
}