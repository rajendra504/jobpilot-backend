package com.jobpilot.jobpilot_backend.scraper;

import com.jobpilot.jobpilot_backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_listings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_portal_url",
                columnNames = {"user_id", "portal", "job_url"}
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "portal", nullable = false, length = 50)
    private String portal;

    @Column(name = "job_title", nullable = false, length = 255)
    private String jobTitle;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "location", length = 300)
    private String location;

    @Column(name = "job_url", nullable = false, length = 1000)
    private String jobUrl;

    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "salary_range", length = 100)
    private String salaryRange;

    @Column(name = "experience_required", length = 100)
    private String experienceRequired;

    @Column(name = "job_type", length = 50)
    private String jobType;

    @Column(name = "is_easy_apply")
    @Builder.Default
    private Boolean isEasyApply = false;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "NEW";

    @Column(name = "scraped_at")
    private LocalDateTime scrapedAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (scrapedAt == null) scrapedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}