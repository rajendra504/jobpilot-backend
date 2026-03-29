CREATE TABLE job_preferences (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL UNIQUE,
    desired_roles JSON         NOT NULL DEFAULT (JSON_ARRAY()),
    preferred_locations JSON  NOT NULL DEFAULT (JSON_ARRAY()),
    job_types     JSON         NOT NULL DEFAULT (JSON_ARRAY()),
    min_salary    DECIMAL(12,2),
    max_salary    DECIMAL(12,2),
    currency      VARCHAR(10)  NOT NULL DEFAULT 'INR',
    experience_level VARCHAR(50),
    preferred_industries JSON NOT NULL DEFAULT (JSON_ARRAY()),
    notice_period_days INT      NOT NULL DEFAULT 0,
    open_to_remote  BOOLEAN    NOT NULL DEFAULT TRUE,
    open_to_hybrid  BOOLEAN    NOT NULL DEFAULT TRUE,
    open_to_relocation BOOLEAN NOT NULL DEFAULT FALSE,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);