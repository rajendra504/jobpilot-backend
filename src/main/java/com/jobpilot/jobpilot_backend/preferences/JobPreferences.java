package com.jobpilot.jobpilot_backend.preferences;

import com.jobpilot.jobpilot_backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_roles", columnDefinition = "json")
    @Builder.Default
    private List<String> desiredRoles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_locations", columnDefinition = "json")
    @Builder.Default
    private List<String> preferredLocations = new ArrayList<>();


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_types", columnDefinition = "json")
    @Builder.Default
    private List<String> jobTypes = new ArrayList<>();

    @Column(name = "min_salary", precision = 12, scale = 2)
    private BigDecimal minSalary;

    @Column(name = "max_salary", precision = 12, scale = 2)
    private BigDecimal maxSalary;

    @Column(name = "currency", length = 10)
    @Builder.Default
    private String currency = "INR";


    @Column(name = "experience_level", length = 50)
    private String experienceLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_industries", columnDefinition = "json")
    @Builder.Default
    private List<String> preferredIndustries = new ArrayList<>();

    @Column(name = "notice_period_days")
    @Builder.Default
    private Integer noticePeriodDays = 0;

    @Column(name = "open_to_remote")
    @Builder.Default
    private Boolean openToRemote = true;

    @Column(name = "open_to_hybrid")
    @Builder.Default
    private Boolean openToHybrid = true;

    @Column(name = "open_to_relocation")
    @Builder.Default
    private Boolean openToRelocation = false;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    /**
     * Maximum number of job applications the runner will submit per day for this user.
     * Default 10 — set lower (e.g. 3–5) while testing to avoid accidental bulk applies.
     */
    @Column(name = "daily_apply_limit")
    @Builder.Default
    private Integer dailyApplyLimit = 5;

    /**
     * Master on/off switch for the scheduled auto-apply runner.
     * When false, runForAllAutoApplyUsers() skips this user entirely.
     * User must explicitly enable this after reviewing scraped jobs.
     */
    @Column(name = "auto_apply_enabled")
    @Builder.Default
    private Boolean autoApplyEnabled = false;

    // ─────────────────────────────────────────────────────────────────────────


    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
