-- ═══════════════════════════════════════════════════
--  V1 — users table
--  Flyway runs this automatically on startup
--  NEVER edit this file after deploying
--  To change schema: create V2__xxx.sql
-- ═══════════════════════════════════════════════════

CREATE TABLE users (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(100)    NOT NULL,
    email         VARCHAR(150)    NOT NULL,
    password_hash VARCHAR(255)    NOT NULL,
    role          VARCHAR(20)     NOT NULL DEFAULT 'ROLE_USER',
    is_active     TINYINT(1)      NOT NULL DEFAULT 1,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;