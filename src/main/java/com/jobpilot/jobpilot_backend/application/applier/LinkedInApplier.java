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

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkedInApplier implements PortalApplier {

    private final ObjectMapper objectMapper;

    private static final int MAX_STEPS   = 8;
    private static final int TIMEOUT_MS  = 15_000;

    @Override
    public String portalKey() { return "linkedin"; }

    @Override
    public ApplyResult apply(Page page, JobListing job, AiAnalysis analysis) {
        log.info("[LinkedIn Apply] Starting for job='{}' at '{}' url={}",
                job.getJobTitle(), job.getCompanyName(), job.getJobUrl());

        try {

            page.navigate(job.getJobUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            StealthUtil.humanDelay(2000, 3500);

            if (isAuthWall(page)) {
                log.warn("[LinkedIn Apply] Auth wall — session expired for job={}", job.getId());
                return ApplyResult.failed("LinkedIn session expired. Re-run session init.");
            }

            Locator easyApplyBtn = page.locator(
                    "button.jobs-apply-button, " +
                            "button[aria-label*='Easy Apply'], " +
                            "button.jobs-s-apply__button"
            ).first();

            if (!easyApplyBtn.isVisible()) {
                log.info("[LinkedIn Apply] No Easy Apply button — marking MANUAL for job={}", job.getId());
                return ApplyResult.manual(job.getJobUrl());
            }

            easyApplyBtn.click();
            StealthUtil.humanDelay(1500, 2500);

            List<Map<String, String>> answers = parseAnswers(analysis.getApplicationAnswersJson());

            for (int step = 0; step < MAX_STEPS; step++) {

                Locator submitBtn = page.locator("button[aria-label='Submit application']").first();
                if (submitBtn.isVisible()) {
                    StealthUtil.humanDelay(800, 1500);
                    submitBtn.click();
                    StealthUtil.humanDelay(2000, 3000);

                    if (isConfirmationVisible(page)) {
                        log.info("[LinkedIn Apply] Successfully applied! job='{}' at '{}'",
                                job.getJobTitle(), job.getCompanyName());
                        return ApplyResult.success("Applied via Easy Apply");
                    }
                    break;
                }

                fillCurrentStep(page, analysis, answers);
                StealthUtil.humanDelay(800, 1500);

                Locator nextBtn = page.locator(
                        "button[aria-label='Continue to next step'], " +
                                "button[aria-label='Review your application'], " +
                                "footer button:has-text('Next')"
                ).first();

                if (!nextBtn.isVisible()) {
                    log.warn("[LinkedIn Apply] Next button not found on step {}", step);
                    break;
                }

                nextBtn.click();
                StealthUtil.humanDelay(1200, 2200);
            }

            return ApplyResult.failed("Apply modal completed but submission not confirmed");

        } catch (Exception e) {
            log.error("[LinkedIn Apply] Error for job={}: {}", job.getId(), e.getMessage(), e);
            return ApplyResult.failed("Playwright error: " + e.getMessage());
        }
    }

//    private void fillCurrentStep(Page page, AiAnalysis analysis, List<Map<String, String>> answers) {
//
//        Locator phoneField = page.locator("input[id*='phoneNumber'], input[aria-label*='Phone']").first();
//        if (phoneField.isVisible() && phoneField.inputValue().isBlank()) {
//            phoneField.fill("+91 9999999999");  // TODO: wire from UserProfile.phone
//            StealthUtil.humanDelay(300, 600);
//        }
//
//        Locator coverArea = page.locator("textarea[id*='cover-letter'], textarea[aria-label*='cover']").first();
//        if (coverArea.isVisible() && analysis.getCoverLetter() != null) {
//            coverArea.fill(analysis.getCoverLetter());
//            StealthUtil.humanDelay(500, 1000);
//        }
//
//        List<ElementHandle> textInputs = page.querySelectorAll("input[type='text']:visible, input[type='number']:visible");
//        for (ElementHandle input : textInputs) {
//            try {
//                String labelText = getLabelForInput(page, input);
//                if (labelText == null) continue;
//
//                String answer = findAnswer(answers, labelText);
//                if (answer != null && input.inputValue().isBlank()) {
//                    input.fill(answer);
//                    StealthUtil.humanDelay(200, 500);
//                }
//            } catch (Exception ignored) {}
//        }
//
//        List<ElementHandle> radios = page.querySelectorAll("input[type='radio']:visible");
//        for (ElementHandle radio : radios) {
//            try {
//                boolean checked = Boolean.parseBoolean(radio.getAttribute("checked"));
//                if (!checked) {
//                    String labelText = getLabelForInput(page, radio);
//                    String answer = findAnswer(answers, labelText);
//                    if ("yes".equalsIgnoreCase(answer)) {
//                        radio.click();
//                        StealthUtil.humanDelay(200, 400);
//                        break;
//                    }
//                }
//            } catch (Exception ignored) {}
//        }
//
//        List<ElementHandle> selects = page.querySelectorAll("select:visible");
//        for (ElementHandle select : selects) {
//            try {
//                String labelText = getLabelForInput(page, select);
//                String answer = findAnswer(answers, labelText);
//                if (answer != null) {
//                    select.selectOption(answer);
//                    StealthUtil.humanDelay(200, 400);
//                }
//            } catch (Exception ignored) {}
//        }
//    }

    private void fillCurrentStep(Page page, AiAnalysis analysis, List<Map<String, String>> answers) {

        // ── Phone number ──────────────────────────────────────────────────────
        List<String> phoneSelectors = List.of(
                "input[id*='phoneNumber']",
                "input[aria-label*='Phone']",
                "input[aria-label*='phone']"
        );
        for (String sel : phoneSelectors) {
            try {
                Locator f = page.locator(sel).first();
                if (f.isVisible() && f.inputValue().isBlank()) {
                    f.fill(findAnswer(answers, "phone") != null
                            ? findAnswer(answers, "phone") : "+91 9876543210");
                    StealthUtil.humanDelay(300, 600);
                    break;
                }
            } catch (Exception ignored) {}
        }

        // ── Cover letter ──────────────────────────────────────────────────────
        try {
            Locator coverArea = page.locator(
                    "textarea[id*='cover-letter'], textarea[aria-label*='cover'], " +
                            "textarea[aria-label*='Cover']"
            ).first();
            if (coverArea.isVisible() && analysis.getCoverLetter() != null
                    && coverArea.inputValue().isBlank()) {
                coverArea.fill(analysis.getCoverLetter());
                StealthUtil.humanDelay(500, 1000);
            }
        } catch (Exception ignored) {}

        // ── All text / number fields (LinkedIn uses fb-single-line-text__input) ─
        List<ElementHandle> textInputs = page.querySelectorAll(
                "input[type='text']:visible, input[type='number']:visible, " +
                        "input.fb-single-line-text__input:visible"
        );
        for (ElementHandle input : textInputs) {
            try {
                if (!input.inputValue().isBlank()) continue;
                String label = getLabelForInput(page, input);
                String answer = findAnswer(answers, label);
                if (answer != null) {
                    input.fill(answer);
                    StealthUtil.humanDelay(200, 500);
                }
            } catch (Exception ignored) {}
        }

        // ── Radio buttons (LinkedIn wraps each group in fieldset or div.fb-form-element) ──
        List<ElementHandle> formElements = page.querySelectorAll(
                "fieldset.fb-form-element, div.fb-form-element, " +
                        "div[data-test-form-element], " +
                        "div[class*='form-component-radio']"
        );
        for (ElementHandle el : formElements) {
            try {
                String questionText = "";
                ElementHandle lbl = el.querySelector("legend, label.fb-form-element-label, span[data-test-form-element-label]");
                if (lbl != null) questionText = lbl.textContent().trim().toLowerCase();

                String answer = findAnswer(answers, questionText);
                if (answer == null) continue;

                // Find the radio option that matches the answer
                List<ElementHandle> options = el.querySelectorAll("input[type='radio']");
                for (ElementHandle radio : options) {
                    String val = radio.getAttribute("value");
                    String radioLabel = "";
                    String radioId = radio.getAttribute("id");
                    if (radioId != null) {
                        ElementHandle radioLbl = el.querySelector("label[for='" + radioId + "']");
                        if (radioLbl != null) radioLabel = radioLbl.textContent().trim().toLowerCase();
                    }
                    if ((val != null && val.equalsIgnoreCase(answer)) ||
                            radioLabel.equalsIgnoreCase(answer) ||
                            radioLabel.contains(answer.toLowerCase())) {
                        radio.click();
                        StealthUtil.humanDelay(200, 400);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        // ── Dropdowns / selects ───────────────────────────────────────────────
        List<ElementHandle> selects = page.querySelectorAll("select:visible");
        for (ElementHandle select : selects) {
            try {
                String label = getLabelForInput(page, select);
                String answer = findAnswer(answers, label);
                if (answer != null) {
                    select.selectOption(answer);
                    StealthUtil.humanDelay(200, 400);
                }
            } catch (Exception ignored) {}
        }

        // ── Checkboxes (agree / terms / confirm) ─────────────────────────────
        List<ElementHandle> checkboxes = page.querySelectorAll("input[type='checkbox']:visible");
        for (ElementHandle cb : checkboxes) {
            try {
                boolean checked = Boolean.parseBoolean(cb.getAttribute("checked"));
                if (checked) continue;
                String cbLabel = getLabelForInput(page, cb).toLowerCase();
                if (cbLabel.contains("agree") || cbLabel.contains("terms") ||
                        cbLabel.contains("follow") || cbLabel.contains("confirm")) {
                    cb.check();
                    StealthUtil.humanDelay(200, 400);
                }
            } catch (Exception ignored) {}
        }
    }
    private boolean isAuthWall(Page page) {
        String url = page.url();
        return url.contains("/login") || url.contains("/authwall") || url.contains("/checkpoint");
    }

    private boolean isConfirmationVisible(Page page) {
        try {
            return page.locator(
                    "[class*='artdeco-modal--layer-confirmation'], " +
                            "h2:has-text('Application submitted'), " +
                            "div[data-test-modal-id='application-submitted']"
            ).first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    private String getLabelForInput(Page page, ElementHandle input) {
        try {
            String id = input.getAttribute("id");
            if (id != null && !id.isBlank()) {
                ElementHandle label = page.querySelector("label[for='" + id + "']");
                if (label != null) return label.innerText().trim().toLowerCase();
            }
            String ariaLabel = input.getAttribute("aria-label");
            return ariaLabel != null ? ariaLabel.trim().toLowerCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String findAnswer(List<Map<String, String>> answers, String label) {
        if (label == null || answers == null) return null;
        String lower = label.toLowerCase();
        return answers.stream()
                .filter(a -> {
                    String q = a.getOrDefault("question", "").toLowerCase();
                    return lower.contains(q) || q.contains(lower);
                })
                .map(a -> a.get("answer"))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseAnswers(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}