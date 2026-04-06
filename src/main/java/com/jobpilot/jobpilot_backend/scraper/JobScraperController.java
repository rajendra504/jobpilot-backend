package com.jobpilot.jobpilot_backend.scraper;

import com.jobpilot.jobpilot_backend.common.ApiResponse;
import com.jobpilot.jobpilot_backend.scraper.JobScraperService.ScrapeStatus;
import com.jobpilot.jobpilot_backend.scraper.async.ScrapeAsyncService;
import com.jobpilot.jobpilot_backend.scraper.dto.JobListingResponse;
import com.jobpilot.jobpilot_backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobScraperController {

    private final JobScraperService scraperService;

    private final ScrapeAsyncService scrapeAsyncService;

    @PostMapping("/scrape")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerScrape(Authentication auth) {
        Long userId = ((UserPrincipal) auth.getPrincipal()).getId();

        ScrapeStatus current = scraperService.getScrapeStatus(userId);
        if (current.running()) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Scrape already in progress.",
                    Map.of(
                            "status",      "RUNNING",
                            "phase",       current.phase(),
                            "jobsSaved",   current.jobsSavedSoFar(),
                            "message",     "A scrape is already running for your account. Check the status endpoint."
                    )
            ));
        }

        scrapeAsyncService.launchAsync(userId);

        return ResponseEntity.ok(ApiResponse.success(
                "Scrape started in the background. New jobs will appear as they are found.",
                Map.of(
                        "status",  "RUNNING",
                        "message", "Poll GET /api/jobs/scrape/status to track progress."
                )
        ));
    }

    @GetMapping("/scrape/status")
    public ResponseEntity<ApiResponse<ScrapeStatus>> getScrapeStatus(Authentication auth) {
        Long userId = ((UserPrincipal) auth.getPrincipal()).getId();
        ScrapeStatus status = scraperService.getScrapeStatus(userId);
        return ResponseEntity.ok(ApiResponse.success("Scrape status retrieved.", status));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobListingResponse>>> getListings(
            Authentication auth,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String portal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<JobListingResponse> listings =
                scraperService.getListings(auth, status, portal, page, size);

        return ResponseEntity.ok(ApiResponse.success(
                "Job listings retrieved. Total: " + listings.getTotalElements(), listings));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<JobListingResponse>> updateStatus(
            Authentication auth,
            @PathVariable Long id,
            @RequestParam String status) {

        JobListingResponse updated = scraperService.updateStatus(auth, id, status);
        return ResponseEntity.ok(ApiResponse.success(
                "Job status updated to " + status.toUpperCase() + ".", updated));
    }
}