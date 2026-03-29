package com.jobpilot.jobpilot_backend.preferences;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JobPreferencesRepository extends JpaRepository<JobPreferences, Long> {

    Optional<JobPreferences> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}