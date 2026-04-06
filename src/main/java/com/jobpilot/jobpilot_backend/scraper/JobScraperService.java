package com.jobpilot.jobpilot_backend.scraper;

import com.jobpilot.jobpilot_backend.exception.ResourceNotFoundException;
import com.jobpilot.jobpilot_backend.preferences.JobPreferences;
import com.jobpilot.jobpilot_backend.preferences.JobPreferencesService;
import com.jobpilot.jobpilot_backend.profile.UserProfile;
import com.jobpilot.jobpilot_backend.profile.UserProfileMapper;
import com.jobpilot.jobpilot_backend.profile.UserProfileRepository;
import com.jobpilot.jobpilot_backend.profile.UserProfileService;
import com.jobpilot.jobpilot_backend.scraper.config.PlaywrightConfig;
import com.jobpilot.jobpilot_backend.scraper.dto.JobListingResponse;
import com.jobpilot.jobpilot_backend.scraper.dto.ScrapedJobDto;
import com.jobpilot.jobpilot_backend.scraper.session.BrowserSessionService;
import com.jobpilot.jobpilot_backend.security.UserPrincipal;
import com.jobpilot.jobpilot_backend.user.User;
import com.jobpilot.jobpilot_backend.user.UserRepository;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobScraperService {

    private final JobListingRepository  listingRepository;
    private final UserRepository        userRepository;
    private final UserProfileRepository profileRepository;
    private final UserProfileMapper     profileMapper;
    private final UserProfileService    profileService;
    private final JobPreferencesService preferencesService;
    private final PlaywrightConfig      playwrightConfig;
    private final BrowserSessionService sessionService;
    private final List<PortalScraper>   portalScrapers;

    @Transactional
    public ScrapeResultSummary triggerScrape(Authentication auth) {
        Long userId = ((UserPrincipal) auth.getPrincipal()).getId();
        return runScrapeForUser(userId);
    }

    @Transactional
    public void scrapeForAllUsers() {
        List<User> users = userRepository.findAll();
        log.info("Scheduled scrape starting for {} users", users.size());
        for (User user : users) {
            try {
                runScrapeForUser(user.getId());
            } catch (Exception e) {
                log.warn("Scrape failed for userId={}: {}", user.getId(), e.getMessage());
            }
        }
    }

    private ScrapeResultSummary runScrapeForUser(Long userId) {

        JobPreferences preferences;
        try {
            preferences = preferencesService.getEntityByUserId(userId);
        } catch (ResourceNotFoundException e) {
            log.info("userId={} has no job preferences — skipping scrape", userId);
            return ScrapeResultSummary.skipped("No job preferences found. Please set up your preferences first.");
        }

        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No user profile found for userId=" + userId));

        Map<String, Map<String, String>> rawCredMap =
                profileMapper.parseCredentialsMap(profile.getPortalCredentialsJson());

        if (rawCredMap.isEmpty()) {
            log.info("userId={} has no portal credentials — skipping scrape", userId);
            return ScrapeResultSummary.skipped("No portal credentials saved. Please add credentials in Profile → Credentials.");
        }

        try {
            playwrightConfig.getBrowser();
        } catch (Exception e) {
            log.error("Playwright browser failed to start for userId={}: {}", userId, e.getMessage());
            throw new IllegalStateException(
                    "Browser automation is unavailable on this server. " +
                            "Please ensure Playwright system dependencies are installed, " +
                            "or run the scraper locally.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Map<String, PortalScraper> scraperMap = portalScrapers.stream()
                .collect(Collectors.toMap(PortalScraper::portalKey, s -> s));

        List<ScrapedJobDto> allScraped = new ArrayList<>();
        List<String> portalErrors = new ArrayList<>();

        for (String portalKey : rawCredMap.keySet()) {
            String normalizedKey = portalKey.toLowerCase();
            PortalScraper scraper = scraperMap.get(normalizedKey);

            if (scraper == null) {
                log.info("No scraper registered for portal='{}' — skipping", normalizedKey);
                continue;
            }

            try {
                List<ScrapedJobDto> portalResults = scrapePortal(
                        userId, normalizedKey, scraper, preferences);
                allScraped.addAll(portalResults);
                log.info("Portal='{}' returned {} jobs for userId={}",
                        normalizedKey, portalResults.size(), userId);

            } catch (Exception e) {
                log.error("Scraper for portal='{}' failed for userId={}: {}",
                        normalizedKey, userId, e.getMessage(), e);
                portalErrors.add(normalizedKey + ": " + sanitizePortalError(e));
            }
        }

        int savedCount   = 0;
        int skippedCount = 0;

        for (ScrapedJobDto dto : allScraped) {
            boolean exists = listingRepository.existsByUserIdAndPortalAndJobUrl(
                    userId, dto.getPortal(), dto.getJobUrl());

            if (exists) { skippedCount++; continue; }

            JobListing listing = JobListing.builder()
                    .user(user)
                    .portal(dto.getPortal())
                    .jobTitle(dto.getJobTitle())
                    .companyName(dto.getCompanyName())
                    .location(dto.getLocation())
                    .jobUrl(dto.getJobUrl())
                    .description(dto.getDescription())
                    .salaryRange(dto.getSalaryRange())
                    .experienceRequired(dto.getExperienceRequired())
                    .jobType(dto.getJobType())
                    .isEasyApply(Boolean.TRUE.equals(dto.getIsEasyApply()))
                    .status("NEW")
                    .build();

            listingRepository.save(listing);
            savedCount++;
        }

        log.info("Scrape done for userId={} | total={} saved={} skipped={}",
                userId, allScraped.size(), savedCount, skippedCount);

        return ScrapeResultSummary.success(allScraped.size(), savedCount, skippedCount, portalErrors);
    }

    private List<ScrapedJobDto> scrapePortal(Long userId, String normalizedKey,
                                             PortalScraper scraper,
                                             JobPreferences preferences) {
        Browser browser = playwrightConfig.getBrowser();

        try (BrowserContext context = browser.newContext()) {

            boolean sessionLoaded = sessionService.loadIntoContext(userId, normalizedKey, context);

            if (!sessionLoaded) {
                log.warn("No saved session for portal='{}' userId={}. " +
                                "Use POST /api/sessions/init?portal={} to create one.",
                        normalizedKey, userId, normalizedKey);
            }

            String username = "";
            String password = "";
            try {
                Map<String, String> decrypted =
                        profileService.getDecryptedCredentials(userId, normalizedKey);
                username = decrypted.get("username");
                password = decrypted.get("password");
            } catch (Exception e) {
                log.warn("Could not get credentials for portal='{}': {}", normalizedKey, e.getMessage());
            }

            Page page = context.newPage();
            List<ScrapedJobDto> scraped = scraper.scrape(page, username, password, preferences);

            if (!scraped.isEmpty() || sessionLoaded) {
                sessionService.saveSession(userId, normalizedKey, context);
            }

            return scraped;
        }
    }

    private String sanitizePortalError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

        if (msg.contains("Host system is missing") || msg.contains("playwright") || msg.contains("Chromium")) {
            return "Browser dependencies missing on server";
        }
        if (msg.contains("Timeout") || msg.contains("timeout")) {
            return "Timed out loading portal page";
        }
        if (msg.contains("net::ERR") || msg.contains("Navigation")) {
            return "Network error reaching portal";
        }
        // Don't expose internal details
        return "Portal scrape failed — check server logs";
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<JobListingResponse> getListings(
            Authentication auth, String status, String portal, int page, int size) {

        Long userId = ((UserPrincipal) auth.getPrincipal()).getId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("scrapedAt").descending());

        org.springframework.data.domain.Page<JobListing> listings;

        if (status != null && portal != null) {
            listings = listingRepository.findByUserIdAndStatusAndPortal(
                    userId, status.toUpperCase(), portal.toLowerCase(), pageable);
        } else if (status != null) {
            listings = listingRepository.findByUserIdAndStatus(
                    userId, status.toUpperCase(), pageable);
        } else if (portal != null) {
            listings = listingRepository.findByUserIdAndPortal(
                    userId, portal.toLowerCase(), pageable);
        } else {
            listings = listingRepository.findByUserId(userId, pageable);
        }

        return listings.map(this::toResponse);
    }

    @Transactional
    public JobListingResponse updateStatus(Authentication auth, Long listingId, String newStatus) {
        Long userId = ((UserPrincipal) auth.getPrincipal()).getId();

        JobListing listing = listingRepository.findByIdAndUserId(listingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job listing not found with id=" + listingId));

        listing.setStatus(newStatus.toUpperCase());
        if ("APPLIED".equalsIgnoreCase(newStatus)) {
            listing.setAppliedAt(LocalDateTime.now());
        }

        return toResponse(listingRepository.save(listing));
    }

    private JobListingResponse toResponse(JobListing j) {
        return JobListingResponse.builder()
                .id(j.getId())
                .portal(j.getPortal())
                .jobTitle(j.getJobTitle())
                .companyName(j.getCompanyName())
                .location(j.getLocation())
                .jobUrl(j.getJobUrl())
                .description(j.getDescription())
                .salaryRange(j.getSalaryRange())
                .experienceRequired(j.getExperienceRequired())
                .jobType(j.getJobType())
                .isEasyApply(j.getIsEasyApply())
                .status(j.getStatus())
                .scrapedAt(j.getScrapedAt())
                .appliedAt(j.getAppliedAt())
                .createdAt(j.getCreatedAt())
                .build();
    }

    public record ScrapeResultSummary(
            boolean ran,
            int totalScraped,
            int newSaved,
            int duplicatesSkipped,
            String skipReason,
            List<String> portalErrors) {

        static ScrapeResultSummary success(int scraped, int saved, int skipped, List<String> errors) {
            return new ScrapeResultSummary(true, scraped, saved, skipped, null, errors);
        }

        static ScrapeResultSummary skipped(String reason) {
            return new ScrapeResultSummary(false, 0, 0, 0, reason, List.of());
        }
    }
}