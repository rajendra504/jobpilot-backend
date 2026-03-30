-- ═══════════════════════════════════════════════════════════════
--  V7 — widen text columns in job_listings
--
--  LinkedIn returns location strings like:
--  "Hyderabad, Telangana, India (On-site) · Actively recruiting"
--  which exceed the original VARCHAR(100) on location.
--
--  Also widen company_name and job_title defensively —
--  scraped data from real portals is always longer than expected.
--  job_url gets TEXT because LinkedIn URLs can be very long.
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE job_listings
    MODIFY COLUMN location        VARCHAR(500) NULL,
    MODIFY COLUMN company_name    VARCHAR(255) NULL,
    MODIFY COLUMN job_title       VARCHAR(300) NOT NULL,
    MODIFY COLUMN job_url         TEXT         NOT NULL,
    MODIFY COLUMN salary_range    VARCHAR(200) NULL,
    MODIFY COLUMN experience_required VARCHAR(200) NULL;