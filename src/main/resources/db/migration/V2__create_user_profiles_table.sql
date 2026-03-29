-- ============================================================
-- V2: User Profiles Table
-- Stores all personal + job-search data for auto-apply
-- ============================================================

CREATE TABLE user_profiles (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,

    -- Personal details
    phone           VARCHAR(20),
    location        VARCHAR(100),
    linkedin_url    VARCHAR(255),
    github_url      VARCHAR(255),
    portfolio_url   VARCHAR(255),
    summary         TEXT,

    -- Job search preferences (stored as JSON arrays)
    skills_json     TEXT,           -- ["Java", "Spring Boot", "Angular"]
    languages_json  TEXT,           -- ["English", "Hindi"]

    -- Education (JSON array of objects)
    education_json  TEXT,

    -- Work experience (JSON array of objects)
    experience_json TEXT,

    -- Pre-answered Q&A bank for job forms (JSON array)
    -- e.g. [{"question": "years of experience", "answer": "3"}]
    qa_bank_json    TEXT,

    -- Portal credentials (AES-256-GCM encrypted, JSON object)
    -- e.g. {"linkedin": {"username": "...", "encryptedPassword": "..."}}
    portal_credentials_json TEXT,

    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_user_profile (user_id),
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);