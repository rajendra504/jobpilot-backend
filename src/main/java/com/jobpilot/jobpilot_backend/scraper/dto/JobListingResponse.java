package com.jobpilot.jobpilot_backend.scraper.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class JobListingResponse {

    private Long id;
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
    private String status;
    private LocalDateTime scrapedAt;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
}