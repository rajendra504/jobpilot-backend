package com.jobpilot.jobpilot_backend.resume;

import com.jobpilot.jobpilot_backend.common.ApiResponse;
import com.jobpilot.jobpilot_backend.resume.dto.ResumeResponse;
import com.jobpilot.jobpilot_backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Base path: /api/resumes
 *
 * POST   /api/resumes/upload          → upload PDF or DOCX (multipart/form-data)
 * GET    /api/resumes                 → list all resumes for logged-in user
 * GET    /api/resumes/{id}            → get single resume metadata
 * PATCH  /api/resumes/{id}/primary    → set resume as primary (used for auto-apply)
 * DELETE /api/resumes/{id}            → delete resume (file + DB row)
 */
@RestController
@RequestMapping("/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    // ── Upload ────────────────────────────────────────────────

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ResumeResponse>> upload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        ResumeResponse response = resumeService.upload(principal.getId(), file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Resume uploaded successfully", response));
    }

    // ── List all ──────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<ResumeResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ResumeResponse> resumes = resumeService.listResumes(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Resumes retrieved", resumes));
    }

    // ── Get single ────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ResumeResponse>> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        ResumeResponse response = resumeService.getResume(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Resume retrieved", response));
    }

    // ── Set primary ───────────────────────────────────────────

    @PatchMapping("/{id}/primary")
    public ResponseEntity<ApiResponse<ResumeResponse>> setPrimary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        ResumeResponse response = resumeService.setPrimary(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Primary resume updated", response));
    }

    // ── Delete ────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        resumeService.delete(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Resume deleted", null));
    }
}