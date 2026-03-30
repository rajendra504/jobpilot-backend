package com.jobpilot.jobpilot_backend.profile;

import com.jobpilot.jobpilot_backend.exception.ResourceNotFoundException;
import com.jobpilot.jobpilot_backend.security.EncryptionService;
import com.jobpilot.jobpilot_backend.profile.dto.PortalCredentialDto;
import com.jobpilot.jobpilot_backend.profile.dto.UserProfileRequest;
import com.jobpilot.jobpilot_backend.profile.dto.UserProfileResponse;
import com.jobpilot.jobpilot_backend.user.User;
import com.jobpilot.jobpilot_backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository profileRepository;
    private final UserRepository        userRepository;
    private final UserProfileMapper     mapper;
    private final EncryptionService     encryptionService;

    @Transactional
    public UserProfileResponse createProfile(Long userId, UserProfileRequest request) {
        if (profileRepository.existsByUserId(userId)) {
            throw new IllegalStateException("Profile already exists for this user. Use PUT to update.");
        }

        User user = findUser(userId);
        UserProfile profile = mapper.toEntity(request);
        profile.setUser(user);

        UserProfile saved = profileRepository.save(profile);
        log.info("Created profile for userId={}", userId);

        return mapper.toResponse(saved, List.of());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        UserProfile profile = findProfile(userId);
        List<String> portals = extractConnectedPortalNames(profile.getPortalCredentialsJson());
        return mapper.toResponse(profile, portals);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserProfileRequest request) {
        UserProfile profile = findProfile(userId);
        mapper.mergeIntoEntity(request, profile);
        UserProfile saved = profileRepository.save(profile);
        log.info("Updated profile for userId={}", userId);

        List<String> portals = extractConnectedPortalNames(saved.getPortalCredentialsJson());
        return mapper.toResponse(saved, portals);
    }

    @Transactional
    public void deleteProfile(Long userId) {
        UserProfile profile = findProfile(userId);
        profileRepository.delete(profile);
        log.info("Deleted profile for userId={}", userId);
    }

    @Transactional
    public UserProfileResponse savePortalCredential(Long userId, PortalCredentialDto dto) {
        UserProfile profile = findProfile(userId);

        Map<String, Map<String, String>> credMap =
                mapper.parseCredentialsMap(profile.getPortalCredentialsJson());

        Map<String, Map<String, String>> mutableCredMap = new HashMap<>(credMap);

        String encryptedPassword = encryptionService.encrypt(dto.password());

        Map<String, String> portalEntry = new HashMap<>();
        portalEntry.put("username", dto.username());
        portalEntry.put("encryptedPassword", encryptedPassword);

        mutableCredMap.put(dto.portal().toLowerCase(), portalEntry);

        profile.setPortalCredentialsJson(mapper.toJson(mutableCredMap));
        UserProfile saved = profileRepository.save(profile);

        log.info("Saved credentials for portal={} userId={}", dto.portal(), userId);

        List<String> portals = extractConnectedPortalNames(saved.getPortalCredentialsJson());
        return mapper.toResponse(saved, portals);
    }

    @Transactional
    public UserProfileResponse removePortalCredential(Long userId, String portal) {
        UserProfile profile = findProfile(userId);

        Map<String, Map<String, String>> credMap =
                mapper.parseCredentialsMap(profile.getPortalCredentialsJson());

        String normalizedPortal = portal.toLowerCase();

        if (!credMap.containsKey(normalizedPortal)) {
            throw new ResourceNotFoundException(
                    "No credentials found for portal: " + portal +
                            ". Connected portals: " + credMap.keySet());
        }

        Map<String, Map<String, String>> mutableCredMap = new HashMap<>(credMap);
        mutableCredMap.remove(normalizedPortal);

        profile.setPortalCredentialsJson(mapper.toJson(mutableCredMap));
        UserProfile saved = profileRepository.save(profile);

        log.info("Removed credentials for portal={} userId={}", portal, userId);

        List<String> portals = extractConnectedPortalNames(saved.getPortalCredentialsJson());
        return mapper.toResponse(saved, portals);
    }

    public Map<String, String> getDecryptedCredentials(Long userId, String portal) {
        UserProfile profile = findProfile(userId);

        Map<String, Map<String, String>> credMap =
                mapper.parseCredentialsMap(profile.getPortalCredentialsJson());

        Map<String, String> entry = credMap.get(portal.toLowerCase());
        if (entry == null) {
            throw new ResourceNotFoundException("No credentials stored for portal: " + portal);
        }

        String decryptedPassword = encryptionService.decrypt(entry.get("encryptedPassword"));
        Map<String, String> result = new HashMap<>();
        result.put("username", entry.get("username"));
        result.put("password", decryptedPassword);
        return result;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private UserProfile findProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profile not found for user: " + userId));
    }

    private List<String> extractConnectedPortalNames(String credentialsJson) {
        Map<String, Map<String, String>> credMap = mapper.parseCredentialsMap(credentialsJson);
        return new ArrayList<>(credMap.keySet());
    }
}