CREATE TABLE job_listings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    portal          VARCHAR(50)     NOT NULL,
    job_title       VARCHAR(255)    NOT NULL,
    company_name    VARCHAR(255)    NOT NULL,
    location        VARCHAR(255),
    job_url         VARCHAR(1000)   NOT NULL,
    description     LONGTEXT,
    salary_range    VARCHAR(100),
    experience_required VARCHAR(100),
    job_type        VARCHAR(50),
    is_easy_apply   BOOLEAN         NOT NULL DEFAULT FALSE,
    status          VARCHAR(30)     NOT NULL DEFAULT 'NEW',
    scraped_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at      DATETIME,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_listing_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_portal_url (user_id, portal, job_url(500))
);

CREATE INDEX idx_listings_user_status ON job_listings(user_id, status);
CREATE INDEX idx_listings_portal      ON job_listings(portal);
CREATE INDEX idx_listings_scraped_at  ON job_listings(scraped_at);