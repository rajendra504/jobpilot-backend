package com.jobpilot.jobpilot_backend.preferences;

import com.jobpilot.jobpilot_backend.exception.ResourceNotFoundException;
import com.jobpilot.jobpilot_backend.preferences.dto.JobPreferencesRequest;
import com.jobpilot.jobpilot_backend.preferences.dto.JobPreferencesResponse;
import com.jobpilot.jobpilot_backend.security.UserPrincipal;
import com.jobpilot.jobpilot_backend.user.User;
import com.jobpilot.jobpilot_backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobPreferencesService {

    private final JobPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;

    // ─── Save or Update ──────────────────────────────────────────────────────────

    @Transactional
    public JobPreferencesResponse saveOrUpdate(Authentication auth, JobPreferencesRequest request) {
        Long userId = extractUserId(auth);
        User user = fetchUser(userId);

        JobPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElse(JobPreferences.builder().user(user).build());

        applyRequest(preferences, request);
        JobPreferences saved = preferencesRepository.save(preferences);

        log.info("Job preferences saved for userId={}", userId);
        return toResponse(saved);
    }

    // ─── Get ──────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public JobPreferencesResponse get(Authentication auth) {
        Long userId = extractUserId(auth);
        JobPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No job preferences found. Please create preferences first."));
        return toResponse(preferences);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Authentication auth) {
        Long userId = extractUserId(auth);
        JobPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No job preferences found for this user."));
        preferencesRepository.delete(preferences);
        log.info("Job preferences deleted for userId={}", userId);
    }

    // ─── Internal helper used by JobScraperService ────────────────────────────────

    @Transactional(readOnly = true)
    public JobPreferences getEntityByUserId(Long userId) {
        return preferencesRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User with id=" + userId + " has not set job preferences yet."));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private void applyRequest(JobPreferences p, JobPreferencesRequest r) {
        p.setDesiredRoles(r.getDesiredRoles());
        p.setPreferredLocations(r.getPreferredLocations());
        p.setJobTypes(r.getJobTypes());
        p.setMinSalary(r.getMinSalary());
        p.setMaxSalary(r.getMaxSalary());
        if (r.getCurrency() != null) p.setCurrency(r.getCurrency());
        p.setExperienceLevel(r.getExperienceLevel());
        p.setPreferredIndustries(r.getPreferredIndustries());
        if (r.getNoticePeriodDays() != null) p.setNoticePeriodDays(r.getNoticePeriodDays());
        if (r.getOpenToRemote() != null) p.setOpenToRemote(r.getOpenToRemote());
        if (r.getOpenToHybrid() != null) p.setOpenToHybrid(r.getOpenToHybrid());
        if (r.getOpenToRelocation() != null) p.setOpenToRelocation(r.getOpenToRelocation());
    }

    private JobPreferencesResponse toResponse(JobPreferences p) {
        return JobPreferencesResponse.builder()
                .id(p.getId())
                .userId(p.getUser().getId())
                .desiredRoles(p.getDesiredRoles())
                .preferredLocations(p.getPreferredLocations())
                .jobTypes(p.getJobTypes())
                .minSalary(p.getMinSalary())
                .maxSalary(p.getMaxSalary())
                .currency(p.getCurrency())
                .experienceLevel(p.getExperienceLevel())
                .preferredIndustries(p.getPreferredIndustries())
                .noticePeriodDays(p.getNoticePeriodDays())
                .openToRemote(p.getOpenToRemote())
                .openToHybrid(p.getOpenToHybrid())
                .openToRelocation(p.getOpenToRelocation())
                .active(p.getActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private Long extractUserId(Authentication auth) {
        return ((UserPrincipal) auth.getPrincipal()).getId();
    }

    private User fetchUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}