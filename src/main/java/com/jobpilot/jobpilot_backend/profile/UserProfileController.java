package com.jobpilot.jobpilot_backend.profile;


import com.jobpilot.jobpilot_backend.common.ApiResponse;
import com.jobpilot.jobpilot_backend.profile.dto.PortalCredentialDto;
import com.jobpilot.jobpilot_backend.profile.dto.UserProfileRequest;
import com.jobpilot.jobpilot_backend.profile.dto.UserProfileResponse;
import com.jobpilot.jobpilot_backend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> createProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserProfileRequest request
    ) {
        UserProfileResponse response = profileService.createProfile(principal.getId(), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Profile created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UserProfileResponse response = profileService.getProfile(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved", response));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserProfileRequest request
    ) {
        UserProfileResponse response = profileService.updateProfile(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        profileService.deleteProfile(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Profile deleted successfully", null));
    }

    @PostMapping("/credentials")
    public ResponseEntity<ApiResponse<UserProfileResponse>> saveCredential(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PortalCredentialDto dto
    ) {
        UserProfileResponse response = profileService.savePortalCredential(principal.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success(
                dto.portal() + " credentials saved securely", response));
    }

    @DeleteMapping("/credentials/{portal}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> removeCredential(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String portal
    ) {
        UserProfileResponse response = profileService.removePortalCredential(principal.getId(), portal);
        return ResponseEntity.ok(ApiResponse.success(
                portal + " credentials removed", response));
    }
}