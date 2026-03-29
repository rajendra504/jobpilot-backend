package com.jobpilot.jobpilot_backend.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.jobpilot_backend.profile.dto.QaPairDto;
import com.jobpilot.jobpilot_backend.profile.dto.UserProfileRequest;
import com.jobpilot.jobpilot_backend.profile.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Handles conversion between the UserProfile entity (which stores collections as JSON strings)
 * and the typed DTOs the service layer works with.
 *
 * We use manual mapping here instead of MapStruct because the JSON parse/write logic
 * would need custom converters in MapStruct anyway — simpler to keep it explicit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileMapper {

    private final ObjectMapper objectMapper;

    // ── Entity → Response DTO ─────────────────────────────────

    public UserProfileResponse toResponse(UserProfile profile, List<String> connectedPortals) {
        return new UserProfileResponse(
                profile.getUser().getId(),
                profile.getUser().getFullName(),
                profile.getUser().getEmail(),
                profile.getPhone(),
                profile.getLocation(),
                profile.getLinkedinUrl(),
                profile.getGithubUrl(),
                profile.getPortfolioUrl(),
                profile.getSummary(),
                parseList(profile.getSkillsJson(), new TypeReference<>() {}),
                parseList(profile.getLanguagesJson(), new TypeReference<>() {}),
                parseList(profile.getEducationJson(), new TypeReference<>() {}),
                parseList(profile.getExperienceJson(), new TypeReference<>() {}),
                parseList(profile.getQaBankJson(), new TypeReference<>() {}),
                connectedPortals,
                calculateCompleteness(profile),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    // ── Request DTO → Entity (for create) ────────────────────

    public UserProfile toEntity(UserProfileRequest request) {
        return UserProfile.builder()
                .phone(request.phone())
                .location(request.location())
                .linkedinUrl(request.linkedinUrl())
                .githubUrl(request.githubUrl())
                .portfolioUrl(request.portfolioUrl())
                .summary(request.summary())
                .skillsJson(toJson(request.skills()))
                .languagesJson(toJson(request.languages()))
                .educationJson(toJson(request.education()))
                .experienceJson(toJson(request.experience()))
                .qaBankJson(toJson(request.qaBank()))
                .build();
    }

    // ── Request DTO → existing Entity (for update / merge) ───

    public void mergeIntoEntity(UserProfileRequest request, UserProfile profile) {
        if (request.phone() != null)        profile.setPhone(request.phone());
        if (request.location() != null)     profile.setLocation(request.location());
        if (request.linkedinUrl() != null)  profile.setLinkedinUrl(request.linkedinUrl());
        if (request.githubUrl() != null)    profile.setGithubUrl(request.githubUrl());
        if (request.portfolioUrl() != null) profile.setPortfolioUrl(request.portfolioUrl());
        if (request.summary() != null)      profile.setSummary(request.summary());
        if (request.skills() != null)       profile.setSkillsJson(toJson(request.skills()));
        if (request.languages() != null)    profile.setLanguagesJson(toJson(request.languages()));
        if (request.education() != null)    profile.setEducationJson(toJson(request.education()));
        if (request.experience() != null)   profile.setExperienceJson(toJson(request.experience()));
        if (request.qaBank() != null)       profile.setQaBankJson(toJson(request.qaBank()));
    }

    // ── JSON helpers ──────────────────────────────────────────

    public String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize object to JSON", e);
            return null;
        }
    }

    public <T> List<T> parseList(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("Failed to parse JSON list: {}", json, e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Map<String, String>> parseCredentialsMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse credentials JSON", e);
            return Collections.emptyMap();
        }
    }

    // ── Profile completeness score (0–100) ───────────────────

    private int calculateCompleteness(UserProfile p) {
        int score = 0;
        if (p.getPhone() != null && !p.getPhone().isBlank())     score += 10;
        if (p.getLocation() != null && !p.getLocation().isBlank()) score += 10;
        if (p.getSummary() != null && !p.getSummary().isBlank()) score += 15;
        if (p.getLinkedinUrl() != null)                           score += 10;
        if (p.getSkillsJson() != null && !p.getSkillsJson().equals("[]")) score += 15;
        if (p.getEducationJson() != null && !p.getEducationJson().equals("[]")) score += 15;
        if (p.getExperienceJson() != null && !p.getExperienceJson().equals("[]")) score += 15;
        if (p.getQaBankJson() != null && !p.getQaBankJson().equals("[]")) score += 10;
        return Math.min(score, 100);
    }
}