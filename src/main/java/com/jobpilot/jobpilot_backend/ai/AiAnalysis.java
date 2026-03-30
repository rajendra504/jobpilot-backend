package com.jobpilot.jobpilot_backend.ai;

import com.jobpilot.jobpilot_backend.scraper.JobListing;
import com.jobpilot.jobpilot_backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_listing_id", nullable = false)
    private JobListing jobListing;


    @Column(name = "match_score", nullable = false)
    private int matchScore;                         // 0–100

    @Column(name = "decision", nullable = false, length = 10)
    private String decision;                        // "APPLY" | "SKIP"

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "missing_skills_json", columnDefinition = "TEXT")
    private String missingSkillsJson;               // JSON array: ["Kafka","Docker"]


    @Column(name = "cover_letter", columnDefinition = "LONGTEXT")
    private String coverLetter;

    @Column(name = "resume_snippet", columnDefinition = "TEXT")
    private String resumeSnippet;                   // 3–5 optimised bullet points

    @Column(name = "application_answers_json", columnDefinition = "TEXT")
    private String applicationAnswersJson;          // [{question, answer}, ...]


    @Column(name = "prompt_tokens_used")
    private Integer promptTokensUsed;


    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";              // PENDING | DONE | FAILED

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;


    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
}
