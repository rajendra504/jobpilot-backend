package com.jobpilot.jobpilot_backend.scraper.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Internal data transfer object used by portal scrapers to pass
 * raw scraped data to JobScraperService. Never exposed via REST.
 */
@Data
@Builder
public class ScrapedJobDto {

    private String portal;
    private String jobTitle;
    private String companyName;
    private String location;
    private String jobUrl;
    private String description;
    private String salaryRange;
    private String experienceRequired;
    private String jobType;
    private Boolean isEasyApply;
}