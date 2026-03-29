-- ============================================================
-- V3: Resumes Table
-- Stores uploaded resume metadata + extracted plain text
-- (used by the AI engine for tailoring)
-- ============================================================

CREATE TABLE resumes (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,

    -- Original filename as uploaded by the user
    original_filename   VARCHAR(255)    NOT NULL,

    -- Path on disk (or Cloudinary URL in production)
    file_path           VARCHAR(500)    NOT NULL,

    -- MIME type: application/pdf or application/vnd.openxmlformats-officedocument.wordprocessingml.document
    content_type        VARCHAR(100)    NOT NULL,

    -- File size in bytes
    file_size           BIGINT          NOT NULL,

    -- Plain text extracted by Apache Tika (sent to Gemini for tailoring)
    extracted_text      LONGTEXT,

    -- Whether this is the active resume used for auto-apply
    is_primary          BOOLEAN         NOT NULL DEFAULT FALSE,

    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_resume_user (user_id)
);