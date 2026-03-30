package com.jobpilot.jobpilot_backend.application;

import com.jobpilot.jobpilot_backend.scraper.JobListing;
import com.jobpilot.jobpilot_backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "application_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_listing_id", nullable = false)
    private JobListing jobListing;

    @Column(nullable = false, length = 50)
    private String portal;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "ai_decision", length = 10)
    private String aiDecision;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "cover_letter_used", columnDefinition = "LONGTEXT")
    private String coverLetterUsed;

    @Column(name = "resume_snippet_used", columnDefinition = "TEXT")
    private String resumeSnippetUsed;

    @Column(name = "manual_apply_url", length = 1000)
    private String manualApplyUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}