-- ═══════════════════════════════════════════════════════════════
--  V8 — ai_analyses table
--
--  Stores the result of every Gemini analysis for a job listing.
--  One row per (user_id, job_listing_id) pair.
--
--  Why V8? V7 widened job_listings columns.
--  Your schema history: V1 users, V2 profiles, V3 resumes,
--  V4 preferences, V5 listings, V6 sessions, V7 alter listings.
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE ai_analyses (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT          NOT NULL,
    job_listing_id          BIGINT          NOT NULL,

    -- Decision
    match_score             INT             NOT NULL DEFAULT 0,   -- 0-100
    decision                VARCHAR(10)     NOT NULL,             -- APPLY | SKIP
    decision_reason         TEXT            NULL,

    -- Missing skills (JSON array: ["Kafka","Docker"])
    missing_skills_json     TEXT            NULL,

    -- Generated content
    cover_letter            LONGTEXT        NULL,
    resume_snippet          TEXT            NULL,     -- optimised bullet points, not full resume
    application_answers_json TEXT           NULL,     -- JSON array of {question, answer}

    -- Prompt tokens used (for monitoring free tier limits)
    prompt_tokens_used      INT             NULL,

    -- Status
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',  -- PENDING | DONE | FAILED
    error_message           TEXT            NULL,

    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_analysis_user_job (user_id, job_listing_id),
    CONSTRAINT fk_ai_analysis_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_analysis_job
        FOREIGN KEY (job_listing_id) REFERENCES job_listings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ai_analyses_decision ON ai_analyses(decision);
CREATE INDEX idx_ai_analyses_score    ON ai_analyses(match_score);