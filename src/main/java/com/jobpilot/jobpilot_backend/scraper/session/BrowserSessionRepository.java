package com.jobpilot.jobpilot_backend.scraper.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrowserSessionRepository extends JpaRepository<BrowserSession, Long> {

    Optional<BrowserSession> findByUserIdAndPortal(Long userId, String portal);

    void deleteByUserIdAndPortal(Long userId, String portal);
}