-- ================================================================
-- V10: Add auto-apply and daily limit fields to job_preferences
--
-- dailyApplyLimit : how many applications the runner sends per day
-- autoApplyEnabled: master switch — runner only processes this user
--                   when this flag is TRUE
-- ================================================================

ALTER TABLE job_preferences
    ADD COLUMN daily_apply_limit   INT     NOT NULL DEFAULT 10    AFTER active,
    ADD COLUMN auto_apply_enabled  BOOLEAN NOT NULL DEFAULT FALSE  AFTER daily_apply_limit;