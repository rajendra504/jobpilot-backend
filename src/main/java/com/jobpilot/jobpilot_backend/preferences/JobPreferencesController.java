package com.jobpilot.jobpilot_backend.preferences;

import com.jobpilot.jobpilot_backend.common.ApiResponse;
import com.jobpilot.jobpilot_backend.preferences.dto.JobPreferencesRequest;
import com.jobpilot.jobpilot_backend.preferences.dto.JobPreferencesResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/preferences")
@RequiredArgsConstructor
public class JobPreferencesController {

    private final JobPreferencesService preferencesService;

    /**
     * POST /api/users/preferences
     * Creates or fully replaces the job preferences for the authenticated user.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<JobPreferencesResponse>> saveOrUpdate(
            Authentication auth,
            @Valid @RequestBody JobPreferencesRequest request) {

        JobPreferencesResponse response = preferencesService.saveOrUpdate(auth, request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Job preferences saved successfully.", response));
    }

    /**
     * GET /api/users/preferences
     * Returns the job preferences of the authenticated user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<JobPreferencesResponse>> get(Authentication auth) {
        JobPreferencesResponse response = preferencesService.get(auth);
        return ResponseEntity.ok(ApiResponse.success("Job preferences retrieved.", response));
    }

    /**
     * DELETE /api/users/preferences
     * Deletes the job preferences of the authenticated user.
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(Authentication auth) {
        preferencesService.delete(auth);
        return ResponseEntity.ok(ApiResponse.success("Job preferences deleted successfully.", null));
    }
}