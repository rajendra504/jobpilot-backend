package com.jobpilot.jobpilot_backend.preferences.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class JobPreferencesResponse {

    private Long id;
    private Long userId;

    private List<String> desiredRoles;
    private List<String> preferredLocations;
    private List<String> jobTypes;

    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private String currency;

    private String experienceLevel;
    private List<String> preferredIndustries;

    private Integer noticePeriodDays;

    private Boolean openToRemote;
    private Boolean openToHybrid;
    private Boolean openToRelocation;

    private Boolean active;
    private Integer dailyApplyLimit;
    private Boolean autoApplyEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}