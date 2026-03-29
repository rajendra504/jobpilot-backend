-- ═══════════════════════════════════════════════════════════
--  V6 — browser_sessions table
--
--  Stores serialized Playwright browser cookies for each user
--  per portal. This solves the OTP/2FA problem on LinkedIn:
--
--  First login: user does it manually once via /api/sessions/init
--  Subsequent scrapes: bot reuses the saved cookies — no OTP needed
--
--  cookies_json is AES-256 encrypted (same EncryptionService)
--  expires_at tells the scraper when to force a fresh login
-- ═══════════════════════════════════════════════════════════

CREATE TABLE browser_sessions (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    portal          VARCHAR(50)     NOT NULL,
    cookies_json    TEXT            NOT NULL,      -- AES-256 encrypted serialized cookies
    expires_at      DATETIME        NOT NULL,       -- session considered stale after this
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_browser_sessions_user_portal (user_id, portal),
    CONSTRAINT fk_browser_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;