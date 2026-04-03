package com.jobpilot.jobpilot_backend.application;

import com.jobpilot.jobpilot_backend.application.dto.ApplicationLogResponse;
import com.jobpilot.jobpilot_backend.application.dto.RunResult;
import com.jobpilot.jobpilot_backend.common.ApiResponse;
import com.jobpilot.jobpilot_backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/runner")
@RequiredArgsConstructor
public class ApplicationRunnerController {

    private final ApplicationRunnerService runnerService;

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<RunResult>> triggerRun(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        RunResult result = runnerService.runForUser(principal.getId());

        String message = String.format(
                "Run complete. Applied: %d | Failed: %d | Manual: %d | Skipped: %d",
                result.applied(), result.failed(), result.manualRequired(), result.skipped()
        );

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<ApplicationLogResponse>>> getLogs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status
    ) {
        List<ApplicationLogResponse> logs = status != null
                ? runnerService.getLogsByStatus(principal.getId(), status)
                : runnerService.getLogs(principal.getId());

        return ResponseEntity.ok(ApiResponse.success("Application logs retrieved", logs));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Map<String, Long> stats = runnerService.getStats(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Stats retrieved", stats));
    }
}