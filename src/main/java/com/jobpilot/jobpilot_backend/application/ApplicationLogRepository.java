package com.jobpilot.jobpilot_backend.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationLogRepository extends JpaRepository<ApplicationLog, Long> {

    Optional<ApplicationLog> findByUserIdAndJobListingId(Long userId, Long jobListingId);

    boolean existsByUserIdAndJobListingId(Long userId, Long jobListingId);

    /**
     * Used by runner to check if a job was already APPLIED (not just logged in any status).
     * This prevents re-applying to a job that previously failed or was manual.
     */
    boolean existsByUserIdAndJobListingIdAndStatus(Long userId, Long jobListingId, String status);

    List<ApplicationLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ApplicationLog> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    @Query("""
        SELECT COUNT(a) FROM ApplicationLog a
        WHERE a.user.id = :userId
        AND a.status = 'APPLIED'
        AND DATE(a.appliedAt) = CURRENT_DATE
    """)
    int countAppliedTodayForUser(@Param("userId") Long userId);

    @Query("SELECT a.status, COUNT(a) FROM ApplicationLog a WHERE a.user.id = :userId GROUP BY a.status")
    List<Object[]> countByStatusForUser(@Param("userId") Long userId);
}