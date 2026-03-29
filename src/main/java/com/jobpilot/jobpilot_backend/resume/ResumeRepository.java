package com.jobpilot.jobpilot_backend.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Resume> findByIdAndUserId(Long id, Long userId);

    Optional<Resume> findByUserIdAndPrimaryTrue(Long userId);

    long countByUserId(Long userId);

    // Sets ALL resumes for a user to non-primary before setting a new primary
    @Modifying
    @Query("UPDATE Resume r SET r.primary = false WHERE r.user.id = :userId")
    void clearPrimaryForUser(@Param("userId") Long userId);
}