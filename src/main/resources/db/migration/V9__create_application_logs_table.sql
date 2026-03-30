--  V9 — application_logs table
--
--  Schema history:
--  V1 users | V2 profiles | V3 resumes | V4 preferences
--  V5 listings | V6 sessions | V7 alter listings | V8 ai_analyses
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE application_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_listing_id BIGINT NOT NULL,
    portal VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ai_decision VARCHAR(10),
    match_score INT,
    applied_at DATETIME NULL,
    failure_reason TEXT NULL,
    cover_letter_used LONGTEXT NULL,
    resume_snippet_used TEXT NULL,
    manual_apply_url VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_app_log_user_job (user_id, job_listing_id),

    CONSTRAINT fk_app_log_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT fk_app_log_job
        FOREIGN KEY (job_listing_id) REFERENCES job_listings(id) ON DELETE CASCADE,

    INDEX idx_app_log_status (user_id, status),
    INDEX idx_app_log_applied_at (applied_at)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;