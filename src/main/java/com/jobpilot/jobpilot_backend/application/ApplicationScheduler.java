package com.jobpilot.jobpilot_backend.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationScheduler {

    private final ApplicationRunnerService runnerService;

    @Scheduled(cron = "${app.runner.cron:0 30 3 * * *}")
    public void scheduledDailyRun() {
        log.info("=== Scheduled application runner starting ===");
        try {
            runnerService.runForAllAutoApplyUsers();
            log.info("=== Scheduled application runner completed ===");
        } catch (Exception e) {
            log.error("=== Application runner encountered an error: {} ===", e.getMessage(), e);
        }
    }
}