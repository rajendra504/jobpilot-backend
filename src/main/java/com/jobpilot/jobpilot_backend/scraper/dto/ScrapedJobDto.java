package com.jobpilot.jobpilot_backend.scraper.dto;

import lombok.Builder;
import lombok.Data;

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