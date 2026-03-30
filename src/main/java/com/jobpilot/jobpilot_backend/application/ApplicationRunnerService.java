package com.jobpilot.jobpilot_backend.application;

import com.jobpilot.jobpilot_backend.ai.AiAnalysis;
import com.jobpilot.jobpilot_backend.ai.AiAnalysisRepository;
import com.jobpilot.jobpilot_backend.ai.AiEngineService;
import com.jobpilot.jobpilot_backend.ai.dto.AiAnalysisResponse;
import com.jobpilot.jobpilot_backend.application.applier.PortalApplier;
import com.jobpilot.jobpilot_backend.application.dto.ApplicationLogResponse;
import com.jobpilot.jobpilot_backend.application.dto.RunResult;
import com.jobpilot.jobpilot_backend.preferences.JobPreferences;
import com.jobpilot.jobpilot_backend.preferences.JobPreferencesRepository;
import com.jobpilot.jobpilot_backend.scraper.JobListing;
import com.jobpilot.jobpilot_backend.scraper.JobListingRepository;
import com.jobpilot.jobpilot_backend.scraper.config.PlaywrightConfig;
import com.jobpilot.jobpilot_backend.scraper.session.BrowserSessionService;
import com.jobpilot.jobpilot_backend.scraper.util.StealthUtil;
import com.jobpilot.jobpilot_backend.user.User;
import com.jobpilot.jobpilot_backend.user.UserRepository;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationRunnerService {

    private final ApplicationLogRepository  logRepository;
    private final JobListingRepository      listingRepository;
    private final AiAnalysisRepository      analysisRepository;
    private final AiEngineService           aiEngineService;
    private final JobPreferencesRepository  prefsRepository;
    private final UserRepository            userRepository;
    private final BrowserSessionService     sessionService;
    private final PlaywrightConfig          playwrightConfig;
    private final List<PortalApplier>       portalAppliers;   // all @Component PortalApplier beans

    public RunResult runForUser(Long userId) {
        log.info("=== Application runner starting for userId={} ===", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Optional<JobPreferences> prefsOpt = prefsRepository.findByUserId(userId);
        int dailyLimit = prefsOpt.map(JobPreferences::getDailyApplyLimit).orElse(10);
        int appliedToday = logRepository.countAppliedTodayForUser(userId);

        if (appliedToday >= dailyLimit) {
            log.info("userId={} has reached daily limit of {} — skipping run", userId, dailyLimit);
            return new RunResult(0, 0, 0, 0, 0,
                    dailyLimit - appliedToday, List.of(), List.of(), List.of());
        }

        int remainingSlots = dailyLimit - appliedToday;

        log.info("[Runner] Batch AI analysis for userId={}", userId);
        List<AiAnalysisResponse> analyses;
        try {
            analyses = aiEngineService.analyseAllNewJobs(userId);
        } catch (Exception e) {
            log.error("[Runner] AI batch failed for userId={}: {}", userId, e.getMessage());
            analyses = List.of();
        }

        List<JobListing> newJobs = listingRepository.findByUserIdAndStatus(
                userId, "NEW",
                org.springframework.data.domain.PageRequest.of(0, 100,
                        org.springframework.data.domain.Sort.by("scrapedAt").descending())
        ).getContent();

        for (JobListing job : newJobs) {
            job.setStatus("ANALYSED");
        }
        listingRepository.saveAll(newJobs);

        Map<Long, AiAnalysis> analysisMap = analysisRepository.findByUserIdAndDecision(userId, "APPLY")
                .stream()
                .filter(a -> "DONE".equals(a.getStatus()))
                .collect(Collectors.toMap(
                        a -> a.getJobListing().getId(),
                        a -> a
                ));

        List<JobListing> toApply = newJobs.stream()
                .filter(j -> analysisMap.containsKey(j.getId()))
                .filter(j -> !logRepository.existsByUserIdAndJobListingId(userId, j.getId()))
                .sorted(Comparator.comparingInt(
                        j -> -analysisMap.get(j.getId()).getMatchScore()
                ))
                .limit(remainingSlots)
                .toList();

        List<JobListing> toSkip = newJobs.stream()
                .filter(j -> !analysisMap.containsKey(j.getId()))
                .toList();

        for (JobListing job : toSkip) {
            persistLog(user, job, "SKIPPED", "SKIP",
                    analysisMap.containsKey(job.getId()) ? analysisMap.get(job.getId()).getMatchScore() : 0,
                    null, null, null, "AI decision: SKIP");
            job.setStatus("SKIPPED");
        }
        listingRepository.saveAll(toSkip);

        Map<String, PortalApplier> applierMap = portalAppliers.stream()
                .collect(Collectors.toMap(PortalApplier::portalKey, a -> a));

        List<String> appliedJobs = new ArrayList<>();
        List<String> failedJobs  = new ArrayList<>();
        List<String> manualJobs  = new ArrayList<>();

        for (JobListing job : toApply) {
            AiAnalysis analysis = analysisMap.get(job.getId());
            PortalApplier applier = applierMap.get(job.getPortal());

            if (applier == null) {
                log.warn("[Runner] No applier for portal='{}' job={}", job.getPortal(), job.getId());
                persistLog(user, job, "MANUAL", "APPLY", analysis.getMatchScore(),
                        analysis.getCoverLetter(), analysis.getResumeSnippet(),
                        job.getJobUrl(), "No applier registered for portal: " + job.getPortal());
                job.setStatus("MANUAL");
                listingRepository.save(job);
                manualJobs.add(job.getJobTitle() + " at " + job.getCompanyName());
                continue;
            }

            Browser browser = playwrightConfig.getBrowser();
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1280, 900));

            StealthUtil.applyStealth(context);
            boolean sessionLoaded = sessionService.loadIntoContext(userId, job.getPortal(), context);

            if (!sessionLoaded) {
                log.warn("[Runner] No valid session for portal='{}' userId={} — marking MANUAL",
                        job.getPortal(), userId);
                context.close();
                persistLog(user, job, "MANUAL", "APPLY", analysis.getMatchScore(),
                        analysis.getCoverLetter(), analysis.getResumeSnippet(),
                        job.getJobUrl(), "Session expired — manual login required");
                job.setStatus("MANUAL");
                listingRepository.save(job);
                manualJobs.add(job.getJobTitle() + " at " + job.getCompanyName());
                continue;
            }

            Page page = context.newPage();
            String jobLabel = job.getJobTitle() + " at " + job.getCompanyName();

            try {
                log.info("[Runner] Applying: '{}' via {}", jobLabel, job.getPortal());
                persistLog(user, job, "APPLYING", "APPLY", analysis.getMatchScore(),
                        analysis.getCoverLetter(), analysis.getResumeSnippet(), null, null);

                PortalApplier.ApplyResult result = applier.apply(page, job, analysis);

                switch (result.outcome()) {
                    case APPLIED -> {
                        persistLog(user, job, "APPLIED", "APPLY", analysis.getMatchScore(),
                                analysis.getCoverLetter(), analysis.getResumeSnippet(), null, null);
                        job.setStatus("APPLIED");
                        job.setAppliedAt(LocalDateTime.now());
                        appliedJobs.add(jobLabel);
                        log.info("[Runner] ✓ Applied: '{}'", jobLabel);
                    }
                    case FAILED -> {
                        persistLog(user, job, "FAILED", "APPLY", analysis.getMatchScore(),
                                analysis.getCoverLetter(), analysis.getResumeSnippet(), null, result.details());
                        job.setStatus("FAILED");
                        failedJobs.add(jobLabel + " — " + result.details());
                        log.warn("[Runner] ✗ Failed: '{}' — {}", jobLabel, result.details());
                    }
                    case MANUAL -> {
                        persistLog(user, job, "MANUAL", "APPLY", analysis.getMatchScore(),
                                analysis.getCoverLetter(), analysis.getResumeSnippet(),
                                result.details(), "Requires manual apply");
                        job.setStatus("MANUAL");
                        manualJobs.add(jobLabel);
                        log.info("[Runner] → Manual required: '{}'", jobLabel);
                    }
                }

                listingRepository.save(job);

            } catch (Exception e) {
                log.error("[Runner] Unexpected error applying to '{}': {}", jobLabel, e.getMessage(), e);
                persistLog(user, job, "FAILED", "APPLY", analysis.getMatchScore(),
                        null, null, null, "Unexpected error: " + e.getMessage());
                job.setStatus("FAILED");
                listingRepository.save(job);
                failedJobs.add(jobLabel);
            } finally {
                try { page.close(); } catch (Exception ignored) {}
                try { context.close(); } catch (Exception ignored) {}
            }

            StealthUtil.humanDelay(5000, 10000);
        }

        int totalAnalysed = analyses.size();
        int limitReached  = Math.max(0, toApply.size() - remainingSlots);

        log.info("=== Runner done for userId={}: applied={} failed={} manual={} skipped={} ===",
                userId, appliedJobs.size(), failedJobs.size(), manualJobs.size(), toSkip.size());

        return new RunResult(
                totalAnalysed,
                appliedJobs.size(),
                toSkip.size(),
                failedJobs.size(),
                manualJobs.size(),
                limitReached,
                appliedJobs,
                failedJobs,
                manualJobs
        );
    }

    public void runForAllAutoApplyUsers() {
        List<JobPreferences> activeUsers = prefsRepository.findAllWithAutoApplyEnabled();
        log.info("[Runner Scheduler] Running for {} auto-apply users", activeUsers.size());

        for (JobPreferences prefs : activeUsers) {
            Long userId = prefs.getUser().getId();
            try {
                runForUser(userId);
            } catch (Exception e) {
                log.error("[Runner Scheduler] Failed for userId={}: {}", userId, e.getMessage(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ApplicationLogResponse> getLogs(Long userId) {
        return logRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationLogResponse> getLogsByStatus(Long userId, String status) {
        return logRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status.toUpperCase())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getStats(Long userId) {
        Map<String, Long> stats = new LinkedHashMap<>();
        logRepository.countByStatusForUser(userId)
                .forEach(row -> stats.put((String) row[0], (Long) row[1]));
        return stats;
    }

    @Transactional
    protected void persistLog(User user, JobListing job, String status, String aiDecision,
                              int matchScore, String coverLetter, String resumeSnippet,
                              String manualUrl, String failureReason) {
        ApplicationLog log_ = logRepository.findByUserIdAndJobListingId(user.getId(), job.getId())
                .orElse(ApplicationLog.builder()
                        .user(user)
                        .jobListing(job)
                        .portal(job.getPortal())
                        .aiDecision(aiDecision)
                        .matchScore(matchScore)
                        .coverLetterUsed(coverLetter)
                        .resumeSnippetUsed(resumeSnippet)
                        .build());

        log_.setStatus(status);
        log_.setFailureReason(failureReason);
        log_.setManualApplyUrl(manualUrl);

        if ("APPLIED".equals(status)) {
            log_.setAppliedAt(LocalDateTime.now());
        }

        logRepository.save(log_);
    }

    private ApplicationLogResponse toResponse(ApplicationLog a) {
        return new ApplicationLogResponse(
                a.getId(),
                a.getJobListing().getId(),
                a.getJobListing().getJobTitle(),
                a.getJobListing().getCompanyName(),
                a.getPortal(),
                a.getStatus(),
                a.getAiDecision(),
                a.getMatchScore(),
                a.getAppliedAt(),
                a.getFailureReason(),
                a.getManualApplyUrl(),
                a.getCreatedAt()
        );
    }
}