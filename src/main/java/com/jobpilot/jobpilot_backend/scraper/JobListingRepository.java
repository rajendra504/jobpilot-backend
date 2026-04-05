package com.jobpilot.jobpilot_backend.scraper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JobListingRepository extends JpaRepository<JobListing, Long> {

    Page<JobListing> findByUserId(Long userId, Pageable pageable);

    Page<JobListing> findByUserIdAndStatus(Long userId, String status, Pageable pageable);

    Page<JobListing> findByUserIdAndPortal(Long userId, String portal, Pageable pageable);

    Page<JobListing> findByUserIdAndStatusAndPortal(Long userId, String status,
                                                    String portal, Pageable pageable);

    boolean existsByUserIdAndPortalAndJobUrl(Long userId, String portal, String jobUrl);

    long countByUserIdAndStatus(Long userId, String status);

    Optional<JobListing> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT j FROM JobListing j WHERE j.user.id = :userId AND j.status = 'NEW' ORDER BY j.scrapedAt DESC")
    Page<JobListing> findNewJobsForUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * Find all jobs eligible to be processed by the application runner.
     *
     * Eligible statuses:
     *   NEW      — just scraped, AI analysis will run on this pass
     *   ANALYSED — was analysed in a previous run but runner did not reach it (limit hit)
     *   FAILED   — previous apply attempt failed; retry on next run
     *
     * Excludes: APPLIED, SKIPPED, MANUAL, APPLYING
     * (APPLYING is a transient state — if app crashed mid-apply, we retry it)
     */
    @Query("""
        SELECT j FROM JobListing j
        WHERE j.user.id = :userId
        AND j.status IN ('NEW', 'ANALYSED', 'FAILED', 'APPLYING')
        ORDER BY j.scrapedAt DESC
    """)
    Page<JobListing> findEligibleForRunner(@Param("userId") Long userId, Pageable pageable);
}