package com.jobpilot.jobpilot_backend.ai;

import com.jobpilot.jobpilot_backend.ai.dto.AnalysisResult;
import com.jobpilot.jobpilot_backend.ai.dto.AiAnalysisResponse;
import com.jobpilot.jobpilot_backend.ai.prompt.PromptBuilder;
import com.jobpilot.jobpilot_backend.exception.ResourceNotFoundException;
import com.jobpilot.jobpilot_backend.profile.UserProfile;
import com.jobpilot.jobpilot_backend.profile.UserProfileRepository;
import com.jobpilot.jobpilot_backend.resume.Resume;
import com.jobpilot.jobpilot_backend.resume.ResumeRepository;
import com.jobpilot.jobpilot_backend.scraper.JobListing;
import com.jobpilot.jobpilot_backend.scraper.JobListingRepository;
import com.jobpilot.jobpilot_backend.user.User;
import com.jobpilot.jobpilot_backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEngineService {

    private final AiAnalysisRepository  analysisRepository;
    private final UserRepository        userRepository;
    private final UserProfileRepository profileRepository;
    private final ResumeRepository      resumeRepository;
    private final JobListingRepository  listingRepository;
    private final GeminiClient          geminiClient;
    private final PromptBuilder         promptBuilder;
    private final AiAnalysisMapper      mapper;

    @Transactional
    public AiAnalysisResponse analyseJob(Long userId, Long jobListingId) {

        analysisRepository.findByUserIdAndJobListingId(userId, jobListingId)
                .filter(a -> "DONE".equals(a.getStatus()))
                .map(mapper::toResponse)
                .ifPresent(r -> { throw new AlreadyAnalysedException(r); });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profile not found for userId=" + userId +
                                ". Please complete your profile before running analysis."));

        JobListing job = listingRepository.findByIdAndUserId(jobListingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job listing not found: " + jobListingId));

        String resumeText = getActiveResumeText(userId);

        AiAnalysis analysis = analysisRepository.findByUserIdAndJobListingId(userId, jobListingId)
                .orElse(AiAnalysis.builder()
                        .user(user)
                        .jobListing(job)
                        .build());

        analysis.setStatus("PENDING");
        analysis.setDecision("SKIP");
        analysis.setErrorMessage(null);
        analysis = analysisRepository.save(analysis);

        try {
            String prompt = promptBuilder.buildAnalysisPrompt(profile, resumeText, job);
            log.info("[AI] Calling Gemini for userId={} jobId={} job='{}'",
                    userId, jobListingId, job.getJobTitle());

            AnalysisResult result = geminiClient.sendAndParse(prompt);

            analysis.setMatchScore(result.getMatchScore() != null ? result.getMatchScore() : 0);
            analysis.setDecision(normaliseDecision(result.getDecision()));
            analysis.setDecisionReason(result.getDecisionReason());
            analysis.setMissingSkillsJson(mapper.toJson(result.getMissingSkills()));
            analysis.setCoverLetter(result.getCoverLetter());
            analysis.setResumeSnippet(result.getResumeSnippet());
            analysis.setApplicationAnswersJson(mapper.toJson(result.getApplicationAnswers()));
            analysis.setStatus("DONE");

            log.info("[AI] Done: userId={} jobId={} score={} decision={}",
                    userId, jobListingId, analysis.getMatchScore(), analysis.getDecision());

        } catch (GeminiClient.GeminiException e) {
            log.error("[AI] Gemini failed for userId={} jobId={}: {}", userId, jobListingId, e.getMessage());
            analysis.setStatus("FAILED");
            analysis.setErrorMessage(e.getMessage());
            analysis.setDecision("SKIP");
        }

        AiAnalysis saved = analysisRepository.save(analysis);
        return mapper.toResponse(saved);
    }

    @Transactional
    public List<AiAnalysisResponse> analyseAllNewJobs(Long userId) {
        List<JobListing> newJobs = listingRepository.findByUserIdAndStatus(userId, "NEW",
                        org.springframework.data.domain.PageRequest.of(0, 50,
                                org.springframework.data.domain.Sort.by("scrapedAt").descending()))
                .getContent();

        log.info("[AI] Batch analysis: {} NEW jobs for userId={}", newJobs.size(), userId);

        return newJobs.stream()
                .filter(job -> !analysisRepository.existsByUserIdAndJobListingId(userId, job.getId()))
                .map(job -> {
                    try {
                        return analyseJob(userId, job.getId());
                    } catch (AlreadyAnalysedException e) {
                        return e.getCachedResult();
                    } catch (Exception e) {
                        log.error("[AI] Batch analysis failed for jobId={}: {}", job.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(r -> r != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiAnalysisResponse getAnalysis(Long userId, Long jobListingId) {
        AiAnalysis analysis = analysisRepository.findByUserIdAndJobListingId(userId, jobListingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No analysis found for jobId=" + jobListingId +
                                ". Call POST /api/ai/analyse/" + jobListingId + " first."));
        return mapper.toResponse(analysis);
    }

    @Transactional(readOnly = true)
    public List<AiAnalysisResponse> getAllAnalyses(Long userId) {
        return analysisRepository.findByUserIdOrderByMatchScoreDesc(userId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiAnalysisResponse> getAnalysesByDecision(Long userId, String decision) {
        return analysisRepository.findByUserIdAndDecision(userId, decision.toUpperCase())
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    public AiAnalysis getAnalysisEntity(Long userId, Long jobListingId) {
        return analysisRepository.findByUserIdAndJobListingId(userId, jobListingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Analysis not found for jobId=" + jobListingId));
    }

    private String getActiveResumeText(Long userId) {
        return resumeRepository.findActiveByUserId(userId)
                .map(Resume::getExtractedText)
                .orElse("Resume not uploaded. Please upload your resume.");
    }

    private String normaliseDecision(String decision) {
        if (decision == null) return "SKIP";
        return decision.trim().toUpperCase().contains("APPLY") ? "APPLY" : "SKIP";
    }

    static class AlreadyAnalysedException extends RuntimeException {
        private final AiAnalysisResponse cachedResult;
        AlreadyAnalysedException(AiAnalysisResponse r) {
            super("Already analysed");
            this.cachedResult = r;
        }
        AiAnalysisResponse getCachedResult() { return cachedResult; }
    }
}