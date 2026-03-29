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

    boolean existsByUserIdAndPortalAndJobUrl(Long userId, String portal, String jobUrl);

    long countByUserIdAndStatus(Long userId, String status);

    @Query("SELECT j FROM JobListing j WHERE j.user.id = :userId AND j.status = 'NEW' ORDER BY j.scrapedAt DESC")
    Page<JobListing> findNewJobsForUser(@Param("userId") Long userId, Pageable pageable);

    Optional<JobListing> findByIdAndUserId(Long id, Long userId);

    Page<JobListing> findByUserIdAndStatusAndPortal(Long userId, String status,
                                                    String portal, Pageable pageable);
}