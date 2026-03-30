package com.jobpilot.jobpilot_backend.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    Optional<AiAnalysis> findByUserIdAndJobListingId(Long userId, Long jobListingId);

    boolean existsByUserIdAndJobListingId(Long userId, Long jobListingId);

    List<AiAnalysis> findByUserIdAndDecision(Long userId, String decision);

    List<AiAnalysis> findByUserIdOrderByMatchScoreDesc(Long userId);

}