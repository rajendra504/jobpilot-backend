package com.jobpilot.jobpilot_backend.application.applier;

import com.jobpilot.jobpilot_backend.ai.AiAnalysis;
import com.jobpilot.jobpilot_backend.scraper.JobListing;
import com.microsoft.playwright.Page;

public interface PortalApplier {

    String portalKey();

    ApplyResult apply(Page page, JobListing job, AiAnalysis analysis);

    record ApplyResult(
            Outcome outcome,
            String details
    ) {
        public static ApplyResult success(String msg)       { return new ApplyResult(Outcome.APPLIED, msg); }
        public static ApplyResult failed(String reason)     { return new ApplyResult(Outcome.FAILED, reason); }
        public static ApplyResult manual(String applyUrl)   { return new ApplyResult(Outcome.MANUAL, applyUrl); }
    }

    enum Outcome { APPLIED, FAILED, MANUAL }
}