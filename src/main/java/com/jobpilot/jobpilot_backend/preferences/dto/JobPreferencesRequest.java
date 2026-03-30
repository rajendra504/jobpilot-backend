package com.jobpilot.jobpilot_backend.preferences.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class JobPreferencesRequest {

    @NotEmpty(message = "At least one desired role is required")
    private List<String> desiredRoles = new ArrayList<>();

    @NotEmpty(message = "At least one preferred location is required")
    private List<String> preferredLocations = new ArrayList<>();

    private List<String> jobTypes = new ArrayList<>();

    @DecimalMin(value = "0.0", inclusive = false, message = "Minimum salary must be positive")
    private BigDecimal minSalary;

    @DecimalMin(value = "0.0", inclusive = false, message = "Maximum salary must be positive")
    private BigDecimal maxSalary;

    @Size(max = 10, message = "Currency code must be at most 10 characters")
    private String currency = "INR";

    private Integer dailyApplyLimit;

    private Boolean autoApplyEnabled;

    private String experienceLevel;

    private List<String> preferredIndustries = new ArrayList<>();

    @Min(value = 0, message = "Notice period cannot be negative")
    @Max(value = 365, message = "Notice period cannot exceed 365 days")
    private Integer noticePeriodDays = 0;

    private Boolean openToRemote = true;

    private Boolean openToHybrid = true;

    private Boolean openToRelocation = false;
}