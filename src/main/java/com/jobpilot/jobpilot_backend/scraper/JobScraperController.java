package com.jobpilot.jobpilot_backend.scraper;

import com.jobpilot.jobpilot_backend.common.ApiResponse;
import com.jobpilot.jobpilot_backend.scraper.dto.JobListingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobScraperController {

    private final JobScraperService scraperService;

    /**
     * POST /api/jobs/scrape
     * Manually triggers a scrape for the authenticated user immediately.
     * Runs synchronously — responds once scraping + saving is complete.
     */
    @PostMapping("/scrape")
    public ResponseEntity<ApiResponse<JobScraperService.ScrapeResultSummary>> triggerScrape(
            Authentication auth) {

        JobScraperService.ScrapeResultSummary result = scraperService.triggerScrape(auth);

        String message = result.ran()
                ? String.format("Scrape complete. %d new jobs saved, %d duplicates skipped.",
                result.newSaved(), result.duplicatesSkipped())
                : "Scrape skipped: " + result.skipReason();

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    /**
     * GET /api/jobs
     * Returns paginated job listings for the authenticated user.
     *
     * Query params (all optional):
     *   status  — filter by status: NEW, APPLIED, REJECTED, INTERVIEW, OFFER
     *   portal  — filter by portal: linkedin, naukri
     *   page    — page number (0-based, default 0)
     *   size    — page size (default 20)
     */
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

    /**
     * PATCH /api/jobs/{id}/status
     * Updates the status of a single job listing.
     * Valid statuses: NEW, APPLIED, REJECTED, INTERVIEW, OFFER
     */
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