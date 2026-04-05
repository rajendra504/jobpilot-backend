//package com.jobpilot.jobpilot_backend.application.applier;
//
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.jobpilot.jobpilot_backend.ai.AiAnalysis;
//import com.jobpilot.jobpilot_backend.scraper.JobListing;
//import com.jobpilot.jobpilot_backend.scraper.util.StealthUtil;
//import com.microsoft.playwright.*;
//import com.microsoft.playwright.options.LoadState;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.Map;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class LinkedInApplier implements PortalApplier {
//
//    private final ObjectMapper objectMapper;
//
//    private static final int MAX_STEPS   = 8;
//    private static final int TIMEOUT_MS  = 15_000;
//
//    @Override
//    public String portalKey() { return "linkedin"; }
//
//    @Override
//    public ApplyResult apply(Page page, JobListing job, AiAnalysis analysis) {
//        log.info("[LinkedIn Apply] Starting for job='{}' at '{}' url={}",
//                job.getJobTitle(), job.getCompanyName(), job.getJobUrl());
//
//        try {
//
//            page.navigate(job.getJobUrl());
//            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
//            StealthUtil.humanDelay(2000, 3500);
//
//            if (isAuthWall(page)) {
//                log.warn("[LinkedIn Apply] Auth wall — session expired for job={}", job.getId());
//                return ApplyResult.failed("LinkedIn session expired. Re-run session init.");
//            }
//
//            Locator easyApplyBtn = page.locator(
//                    "button.jobs-apply-button, " +
//                            "button[aria-label*='Easy Apply'], " +
//                            "button.jobs-s-apply__button"
//            ).first();
//
//            if (!easyApplyBtn.isVisible()) {
//                log.info("[LinkedIn Apply] No Easy Apply button — marking MANUAL for job={}", job.getId());
//                return ApplyResult.manual(job.getJobUrl());
//            }
//
//            easyApplyBtn.click();
//            StealthUtil.humanDelay(1500, 2500);
//
//            List<Map<String, String>> answers = parseAnswers(analysis.getApplicationAnswersJson());
//
//            for (int step = 0; step < MAX_STEPS; step++) {
//
//                Locator submitBtn = page.locator("button[aria-label='Submit application']").first();
//                if (submitBtn.isVisible()) {
//                    StealthUtil.humanDelay(800, 1500);
//                    submitBtn.click();
//                    StealthUtil.humanDelay(2000, 3000);
//
//                    if (isConfirmationVisible(page)) {
//                        log.info("[LinkedIn Apply] Successfully applied! job='{}' at '{}'",
//                                job.getJobTitle(), job.getCompanyName());
//                        return ApplyResult.success("Applied via Easy Apply");
//                    }
//                    break;
//                }
//
//                fillCurrentStep(page, analysis, answers);
//                StealthUtil.humanDelay(800, 1500);
//
//                Locator nextBtn = page.locator(
//                        "button[aria-label='Continue to next step'], " +
//                                "button[aria-label='Review your application'], " +
//                                "footer button:has-text('Next')"
//                ).first();
//
//                if (!nextBtn.isVisible()) {
//                    log.warn("[LinkedIn Apply] Next button not found on step {}", step);
//                    break;
//                }
//
//                nextBtn.click();
//                StealthUtil.humanDelay(1200, 2200);
//            }
//
//            return ApplyResult.failed("Apply modal completed but submission not confirmed");
//
//        } catch (Exception e) {
//            log.error("[LinkedIn Apply] Error for job={}: {}", job.getId(), e.getMessage(), e);
//            return ApplyResult.failed("Playwright error: " + e.getMessage());
//        }
//    }
//
////    private void fillCurrentStep(Page page, AiAnalysis analysis, List<Map<String, String>> answers) {
////
////        Locator phoneField = page.locator("input[id*='phoneNumber'], input[aria-label*='Phone']").first();
////        if (phoneField.isVisible() && phoneField.inputValue().isBlank()) {
////            phoneField.fill("+91 9999999999");  // TODO: wire from UserProfile.phone
////            StealthUtil.humanDelay(300, 600);
////        }
////
////        Locator coverArea = page.locator("textarea[id*='cover-letter'], textarea[aria-label*='cover']").first();
////        if (coverArea.isVisible() && analysis.getCoverLetter() != null) {
////            coverArea.fill(analysis.getCoverLetter());
////            StealthUtil.humanDelay(500, 1000);
////        }
////
////        List<ElementHandle> textInputs = page.querySelectorAll("input[type='text']:visible, input[type='number']:visible");
////        for (ElementHandle input : textInputs) {
////            try {
////                String labelText = getLabelForInput(page, input);
////                if (labelText == null) continue;
////
////                String answer = findAnswer(answers, labelText);
////                if (answer != null && input.inputValue().isBlank()) {
////                    input.fill(answer);
////                    StealthUtil.humanDelay(200, 500);
////                }
////            } catch (Exception ignored) {}
////        }
////
////        List<ElementHandle> radios = page.querySelectorAll("input[type='radio']:visible");
////        for (ElementHandle radio : radios) {
////            try {
////                boolean checked = Boolean.parseBoolean(radio.getAttribute("checked"));
////                if (!checked) {
////                    String labelText = getLabelForInput(page, radio);
////                    String answer = findAnswer(answers, labelText);
////                    if ("yes".equalsIgnoreCase(answer)) {
////                        radio.click();
////                        StealthUtil.humanDelay(200, 400);
////                        break;
////                    }
////                }
////            } catch (Exception ignored) {}
////        }
////
////        List<ElementHandle> selects = page.querySelectorAll("select:visible");
////        for (ElementHandle select : selects) {
////            try {
////                String labelText = getLabelForInput(page, select);
////                String answer = findAnswer(answers, labelText);
////                if (answer != null) {
////                    select.selectOption(answer);
////                    StealthUtil.humanDelay(200, 400);
////                }
////            } catch (Exception ignored) {}
////        }
////    }
//
//    private void fillCurrentStep(Page page, AiAnalysis analysis, List<Map<String, String>> answers) {
//
//        // ── Phone number ──────────────────────────────────────────────────────
//        List<String> phoneSelectors = List.of(
//                "input[id*='phoneNumber']",
//                "input[aria-label*='Phone']",
//                "input[aria-label*='phone']"
//        );
//        for (String sel : phoneSelectors) {
//            try {
//                Locator f = page.locator(sel).first();
//                if (f.isVisible() && f.inputValue().isBlank()) {
//                    f.fill(findAnswer(answers, "phone") != null
//                            ? findAnswer(answers, "phone") : "+91 9876543210");
//                    StealthUtil.humanDelay(300, 600);
//                    break;
//                }
//            } catch (Exception ignored) {}
//        }
//
//        // ── Cover letter ──────────────────────────────────────────────────────
//        try {
//            Locator coverArea = page.locator(
//                    "textarea[id*='cover-letter'], textarea[aria-label*='cover'], " +
//                            "textarea[aria-label*='Cover']"
//            ).first();
//            if (coverArea.isVisible() && analysis.getCoverLetter() != null
//                    && coverArea.inputValue().isBlank()) {
//                coverArea.fill(analysis.getCoverLetter());
//                StealthUtil.humanDelay(500, 1000);
//            }
//        } catch (Exception ignored) {}
//
//        // ── All text / number fields (LinkedIn uses fb-single-line-text__input) ─
//        List<ElementHandle> textInputs = page.querySelectorAll(
//                "input[type='text']:visible, input[type='number']:visible, " +
//                        "input.fb-single-line-text__input:visible"
//        );
//        for (ElementHandle input : textInputs) {
//            try {
//                if (!input.inputValue().isBlank()) continue;
//                String label = getLabelForInput(page, input);
//                String answer = findAnswer(answers, label);
//                if (answer != null) {
//                    input.fill(answer);
//                    StealthUtil.humanDelay(200, 500);
//                }
//            } catch (Exception ignored) {}
//        }
//
//        // ── Radio buttons (LinkedIn wraps each group in fieldset or div.fb-form-element) ──
//        List<ElementHandle> formElements = page.querySelectorAll(
//                "fieldset.fb-form-element, div.fb-form-element, " +
//                        "div[data-test-form-element], " +
//                        "div[class*='form-component-radio']"
//        );
//        for (ElementHandle el : formElements) {
//            try {
//                String questionText = "";
//                ElementHandle lbl = el.querySelector("legend, label.fb-form-element-label, span[data-test-form-element-label]");
//                if (lbl != null) questionText = lbl.textContent().trim().toLowerCase();
//
//                String answer = findAnswer(answers, questionText);
//                if (answer == null) continue;
//
//                // Find the radio option that matches the answer
//                List<ElementHandle> options = el.querySelectorAll("input[type='radio']");
//                for (ElementHandle radio : options) {
//                    String val = radio.getAttribute("value");
//                    String radioLabel = "";
//                    String radioId = radio.getAttribute("id");
//                    if (radioId != null) {
//                        ElementHandle radioLbl = el.querySelector("label[for='" + radioId + "']");
//                        if (radioLbl != null) radioLabel = radioLbl.textContent().trim().toLowerCase();
//                    }
//                    if ((val != null && val.equalsIgnoreCase(answer)) ||
//                            radioLabel.equalsIgnoreCase(answer) ||
//                            radioLabel.contains(answer.toLowerCase())) {
//                        radio.click();
//                        StealthUtil.humanDelay(200, 400);
//                        break;
//                    }
//                }
//            } catch (Exception ignored) {}
//        }
//
//        // ── Dropdowns / selects ───────────────────────────────────────────────
//        List<ElementHandle> selects = page.querySelectorAll("select:visible");
//        for (ElementHandle select : selects) {
//            try {
//                String label = getLabelForInput(page, select);
//                String answer = findAnswer(answers, label);
//                if (answer != null) {
//                    select.selectOption(answer);
//                    StealthUtil.humanDelay(200, 400);
//                }
//            } catch (Exception ignored) {}
//        }
//
//        // ── Checkboxes (agree / terms / confirm) ─────────────────────────────
//        List<ElementHandle> checkboxes = page.querySelectorAll("input[type='checkbox']:visible");
//        for (ElementHandle cb : checkboxes) {
//            try {
//                boolean checked = Boolean.parseBoolean(cb.getAttribute("checked"));
//                if (checked) continue;
//                String cbLabel = getLabelForInput(page, cb).toLowerCase();
//                if (cbLabel.contains("agree") || cbLabel.contains("terms") ||
//                        cbLabel.contains("follow") || cbLabel.contains("confirm")) {
//                    cb.check();
//                    StealthUtil.humanDelay(200, 400);
//                }
//            } catch (Exception ignored) {}
//        }
//    }
//    private boolean isAuthWall(Page page) {
//        String url = page.url();
//        return url.contains("/login") || url.contains("/authwall") || url.contains("/checkpoint");
//    }
//
//    private boolean isConfirmationVisible(Page page) {
//        try {
//            return page.locator(
//                    "[class*='artdeco-modal--layer-confirmation'], " +
//                            "h2:has-text('Application submitted'), " +
//                            "div[data-test-modal-id='application-submitted']"
//            ).first().isVisible();
//        } catch (Exception e) {
//            return false;
//        }
//    }
//
//    private String getLabelForInput(Page page, ElementHandle input) {
//        try {
//            String id = input.getAttribute("id");
//            if (id != null && !id.isBlank()) {
//                ElementHandle label = page.querySelector("label[for='" + id + "']");
//                if (label != null) return label.innerText().trim().toLowerCase();
//            }
//            String ariaLabel = input.getAttribute("aria-label");
//            return ariaLabel != null ? ariaLabel.trim().toLowerCase() : null;
//        } catch (Exception e) {
//            return null;
//        }
//    }
//
//    private String findAnswer(List<Map<String, String>> answers, String label) {
//        if (label == null || answers == null) return null;
//        String lower = label.toLowerCase();
//        return answers.stream()
//                .filter(a -> {
//                    String q = a.getOrDefault("question", "").toLowerCase();
//                    return lower.contains(q) || q.contains(lower);
//                })
//                .map(a -> a.get("answer"))
//                .findFirst()
//                .orElse(null);
//    }
//
//    @SuppressWarnings("unchecked")
//    private List<Map<String, String>> parseAnswers(String json) {
//        if (json == null || json.isBlank()) return List.of();
//        try {
//            return objectMapper.readValue(json, new TypeReference<>() {});
//        } catch (Exception e) {
//            return List.of();
//        }
//    }
//}



package com.jobpilot.jobpilot_backend.application.applier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.jobpilot_backend.ai.AiAnalysis;
import com.jobpilot.jobpilot_backend.scraper.JobListing;
import com.jobpilot.jobpilot_backend.scraper.util.StealthUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * LinkedInApplier — applies to LinkedIn jobs via Easy Apply.
 *
 * LinkedIn Easy Apply Flow:
 * 1. Navigate to job URL
 * 2. Click the "Easy Apply" button (only present for Easy Apply jobs)
 * 3. If no Easy Apply button → mark MANUAL (external apply)
 * 4. Multi-step modal: fill each step, click Next until Submit button appears
 * 5. Click Submit → confirm via the post-apply confirmation modal
 *
 * Note: LinkedIn only marks MANUAL for non-Easy-Apply jobs.
 * Session must be valid (cookies loaded) before this runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkedInApplier implements PortalApplier {

    private final ObjectMapper objectMapper;

    private static final int MAX_STEPS  = 10;
    private static final int TIMEOUT_MS = 20_000;

    @Override
    public String portalKey() { return "linkedin"; }

    @Override
    public ApplyResult apply(Page page, JobListing job, AiAnalysis analysis) {
        log.info("[LinkedIn] Starting apply for job='{}' at '{}' url={}",
                job.getJobTitle(), job.getCompanyName(), job.getJobUrl());

        try {
            page.setDefaultTimeout(TIMEOUT_MS);
            page.navigate(job.getJobUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            StealthUtil.humanDelay(2500, 4000);

            // ── Auth wall guard ───────────────────────────────────────────────
            if (isAuthWall(page)) {
                log.warn("[LinkedIn] Auth wall detected — session expired for job={}", job.getId());
                return ApplyResult.failed("LinkedIn session expired — re-login via POST /api/sessions/init");
            }

            // ── Dismiss any cookie banners ────────────────────────────────────
            dismissCookieBanner(page);

            // ── Find Easy Apply button ────────────────────────────────────────
            // LinkedIn renders this button with multiple possible selectors
            Locator easyApplyBtn = page.locator(
                    "button.jobs-apply-button:has-text('Easy Apply'), " +
                            "button[aria-label*='Easy Apply'], " +
                            "button.jobs-s-apply__button:has-text('Easy Apply'), " +
                            ".jobs-apply-button--top-card button"
            ).first();

            StealthUtil.humanDelay(1000, 2000);

            if (!easyApplyBtn.isVisible()) {
                log.info("[LinkedIn] No Easy Apply button found — marking MANUAL for job={}", job.getId());
                return ApplyResult.manual(job.getJobUrl());
            }

            log.info("[LinkedIn] Clicking Easy Apply button for job={}", job.getId());
            easyApplyBtn.click();
            StealthUtil.humanDelay(2000, 3000);

            // Wait for the Easy Apply modal to open
            boolean modalOpened = waitForModal(page);
            if (!modalOpened) {
                log.warn("[LinkedIn] Easy Apply modal did not open for job={}", job.getId());
                return ApplyResult.failed("Easy Apply modal did not open");
            }

            List<Map<String, String>> answers = parseAnswers(analysis.getApplicationAnswersJson());
            log.info("[LinkedIn] Answer bank has {} entries for job={}", answers.size(), job.getId());

            // ── Multi-step modal loop ─────────────────────────────────────────
            for (int step = 0; step < MAX_STEPS; step++) {
                StealthUtil.humanDelay(1000, 1800);

                log.debug("[LinkedIn] Processing Easy Apply step {}", step + 1);

                // Check for Submit button — final step
                Locator submitBtn = page.locator(
                        "button[aria-label='Submit application'], " +
                                "button:has-text('Submit application')"
                ).first();

                if (submitBtn.isVisible()) {
                    log.info("[LinkedIn] Submit button found on step {} for job={}", step + 1, job.getId());
                    StealthUtil.humanDelay(800, 1500);
                    submitBtn.click();
                    StealthUtil.humanDelay(2500, 4000);

                    if (isConfirmationVisible(page)) {
                        log.info("[LinkedIn] ✓ Application submitted successfully for job='{}'", job.getJobTitle());
                        // Close the confirmation modal
                        dismissConfirmationModal(page);
                        return ApplyResult.success("Applied via LinkedIn Easy Apply");
                    }

                    // If no confirmation modal, check for any success text
                    if (isPostSubmitSuccess(page)) {
                        return ApplyResult.success("Applied via LinkedIn Easy Apply");
                    }

                    log.warn("[LinkedIn] Submit clicked but no confirmation for job={}", job.getId());
                    return ApplyResult.failed("Submitted but confirmation not detected");
                }

                // ── Fill current step's fields ────────────────────────────────
                fillCurrentStep(page, analysis, answers);
                StealthUtil.humanDelay(800, 1500);

                // ── Click Next / Review ───────────────────────────────────────
                Locator nextBtn = page.locator(
                        "button[aria-label='Continue to next step'], " +
                                "button[aria-label='Review your application'], " +
                                "footer button:has-text('Next'), " +
                                "button:has-text('Review')"
                ).first();

                if (nextBtn.isVisible() && nextBtn.isEnabled()) {
                    log.debug("[LinkedIn] Clicking Next on step {}", step + 1);
                    nextBtn.click();
                    StealthUtil.humanDelay(1500, 2500);
                } else {
                    log.warn("[LinkedIn] Neither Submit nor Next found on step {} for job={}", step + 1, job.getId());
                    break;
                }
            }

            // If we exit the loop without submitting
            log.warn("[LinkedIn] Max steps reached without submission for job={}", job.getId());
            return ApplyResult.failed("Max steps reached — application not submitted");

        } catch (Exception e) {
            log.error("[LinkedIn] Error applying to job={}: {}", job.getId(), e.getMessage(), e);
            return ApplyResult.failed("Playwright error: " + e.getMessage());
        }
    }

    // ── Fill all form fields on the current step ──────────────────────────────

    private void fillCurrentStep(Page page, AiAnalysis analysis, List<Map<String, String>> answers) {

        // ── Phone number ──────────────────────────────────────────────────────
        try {
            Locator phoneField = page.locator(
                    "input[id*='phoneNumber'], " +
                            "input[aria-label*='Phone number'], " +
                            "input[aria-label*='phone']"
            ).first();
            if (phoneField.isVisible() && phoneField.inputValue().isBlank()) {
                String phoneAnswer = findAnswer(answers, "phone");
                phoneField.fill(phoneAnswer != null ? phoneAnswer : "+91 9876543210");
                StealthUtil.humanDelay(300, 600);
                log.debug("[LinkedIn] Filled phone number");
            }
        } catch (Exception ignored) {}

        // ── Cover letter textarea ─────────────────────────────────────────────
        try {
            Locator coverArea = page.locator(
                    "textarea[id*='cover-letter'], " +
                            "textarea[aria-label*='cover letter'], " +
                            "textarea[aria-label*='Cover letter'], " +
                            "textarea[aria-label*='Cover Letter']"
            ).first();
            if (coverArea.isVisible() && analysis.getCoverLetter() != null
                    && coverArea.inputValue().isBlank()) {
                coverArea.fill(analysis.getCoverLetter());
                StealthUtil.humanDelay(500, 1000);
                log.debug("[LinkedIn] Filled cover letter");
            }
        } catch (Exception ignored) {}

        // ── Text and number inputs ────────────────────────────────────────────
        // LinkedIn Easy Apply uses class fb-single-line-text__input for its custom inputs
        List<ElementHandle> textInputs = page.querySelectorAll(
                "input[type='text']:visible, " +
                        "input[type='number']:visible, " +
                        "input.fb-single-line-text__input:visible"
        );
        for (ElementHandle input : textInputs) {
            try {
                if (!input.isVisible()) continue;
                String val = input.inputValue();
                if (val != null && !val.isBlank()) continue;

                String label  = getLabelForInput(page, input);
                String answer = findAnswer(answers, label);
                if (answer != null) {
                    input.fill(answer);
                    StealthUtil.humanDelay(200, 500);
                    log.debug("[LinkedIn] Filled text input '{}' = '{}'", label, answer);
                }
            } catch (Exception ignored) {}
        }

        // ── Radio button groups ───────────────────────────────────────────────
        // LinkedIn wraps each question in fieldset.fb-form-element or similar containers
        List<ElementHandle> formElements = page.querySelectorAll(
                "fieldset.fb-form-element, " +
                        "div.fb-form-element, " +
                        "div[data-test-form-element], " +
                        "div[class*='form-component-radio']"
        );
        for (ElementHandle el : formElements) {
            try {
                ElementHandle legendEl = el.querySelector(
                        "legend, label.fb-form-element-label, " +
                                "span[data-test-form-element-label], " +
                                "div[data-test-form-element-label]"
                );
                if (legendEl == null) continue;

                String questionText = legendEl.textContent().trim().toLowerCase();
                String answer = findAnswer(answers, questionText);
                if (answer == null) continue;

                // Find and click the matching radio option
                List<ElementHandle> radios = el.querySelectorAll("input[type='radio']");
                for (ElementHandle radio : radios) {
                    String radioId = radio.getAttribute("id");
                    String radioLabel = "";
                    if (radioId != null) {
                        ElementHandle lbl = el.querySelector("label[for='" + radioId + "']");
                        if (lbl != null) radioLabel = lbl.textContent().trim().toLowerCase();
                    }
                    String radioValue = radio.getAttribute("value");

                    if ((radioValue != null && radioValue.equalsIgnoreCase(answer)) ||
                            radioLabel.equalsIgnoreCase(answer) ||
                            radioLabel.contains(answer.toLowerCase())) {
                        radio.click();
                        StealthUtil.humanDelay(200, 400);
                        log.debug("[LinkedIn] Selected radio '{}' for '{}'", radioLabel, questionText);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        // ── Dropdowns / selects ───────────────────────────────────────────────
        List<ElementHandle> selects = page.querySelectorAll("select:visible");
        for (ElementHandle select : selects) {
            try {
                String label  = getLabelForInput(page, select);
                String answer = findAnswer(answers, label);
                if (answer != null) {
                    select.selectOption(answer);
                    StealthUtil.humanDelay(200, 400);
                    log.debug("[LinkedIn] Selected dropdown '{}' = '{}'", label, answer);
                }
            } catch (Exception ignored) {}
        }

        // ── Checkboxes ────────────────────────────────────────────────────────
        List<ElementHandle> checkboxes = page.querySelectorAll("input[type='checkbox']:visible");
        for (ElementHandle cb : checkboxes) {
            try {
                if (cb.isChecked()) continue;
                String cbLabel = getLabelForInput(page, cb);
                if (cbLabel == null) continue;
                String lower = cbLabel.toLowerCase();
                if (lower.contains("agree") || lower.contains("terms") ||
                        lower.contains("follow") || lower.contains("confirm") ||
                        lower.contains("acknowledge")) {
                    cb.check();
                    StealthUtil.humanDelay(200, 400);
                    log.debug("[LinkedIn] Auto-checked: '{}'", cbLabel);
                }
            } catch (Exception ignored) {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean waitForModal(Page page) {
        try {
            page.waitForSelector(
                    "div.jobs-easy-apply-modal, " +
                            "[data-test-modal], " +
                            "div[role='dialog']",
                    new Page.WaitForSelectorOptions().setTimeout(8000)
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAuthWall(Page page) {
        String url = page.url();
        return url.contains("/login") || url.contains("/authwall") ||
                url.contains("/checkpoint") || url.contains("/uas/");
    }

    private boolean isConfirmationVisible(Page page) {
        try {
            // LinkedIn shows a post-apply confirmation modal with specific text
            String[] confirmSelectors = {
                    "h2:has-text('Application submitted')",
                    "h3:has-text('Application submitted')",
                    "div[data-test-modal-id='application-submitted']",
                    "[class*='post-apply-timeline']",
                    "div:has-text('Your application was sent')"
            };
            for (String sel : confirmSelectors) {
                try {
                    if (page.locator(sel).first().isVisible()) {
                        log.info("[LinkedIn] Confirmation found: {}", sel);
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPostSubmitSuccess(Page page) {
        try {
            // Fallback: check page text for success indicators
            String pageText = page.textContent("body");
            return pageText != null && (
                    pageText.contains("Application submitted") ||
                            pageText.contains("Your application was sent") ||
                            pageText.contains("application has been submitted")
            );
        } catch (Exception e) {
            return false;
        }
    }

    private void dismissConfirmationModal(Page page) {
        try {
            Locator dismissBtn = page.locator(
                    "button[aria-label='Dismiss'], " +
                            "button[data-test-modal-close-btn], " +
                            "button:has-text('Done'), " +
                            "button:has-text('Close')"
            ).first();
            if (dismissBtn.isVisible()) {
                dismissBtn.click();
                StealthUtil.humanDelay(500, 1000);
            }
        } catch (Exception ignored) {}
    }

    private void dismissCookieBanner(Page page) {
        try {
            Locator acceptBtn = page.locator(
                    "button:has-text('Accept'), " +
                            "button:has-text('Accept all'), " +
                            "[class*='cookie'] button"
            ).first();
            if (acceptBtn.isVisible()) {
                acceptBtn.click();
                StealthUtil.humanDelay(500, 1000);
            }
        } catch (Exception ignored) {}
    }

    private String getLabelForInput(Page page, ElementHandle input) {
        try {
            String id = input.getAttribute("id");
            if (id != null && !id.isBlank()) {
                ElementHandle label = page.querySelector("label[for='" + id + "']");
                if (label != null) return label.innerText().trim().toLowerCase();
            }
            String ariaLabel = input.getAttribute("aria-label");
            if (ariaLabel != null && !ariaLabel.isBlank()) return ariaLabel.trim().toLowerCase();

            String placeholder = input.getAttribute("placeholder");
            if (placeholder != null && !placeholder.isBlank()) return placeholder.trim().toLowerCase();

            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String findAnswer(List<Map<String, String>> answers, String label) {
        if (label == null || label.isBlank() || answers == null || answers.isEmpty()) return null;
        String lower = label.toLowerCase().trim();

        // Priority 1: direct contains match
        for (Map<String, String> a : answers) {
            String q = a.getOrDefault("question", "").toLowerCase();
            if (!q.isBlank() && (lower.contains(q) || q.contains(lower))) {
                return a.get("answer");
            }
        }

        // Priority 2: keyword overlap
        for (Map<String, String> a : answers) {
            String q = a.getOrDefault("question", "").toLowerCase();
            if (q.isBlank()) continue;
            for (String word : q.split("\\s+")) {
                if (word.length() > 3 && lower.contains(word)) {
                    return a.get("answer");
                }
            }
        }

        return null;
    }

    private List<Map<String, String>> parseAnswers(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}