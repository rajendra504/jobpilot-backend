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
//public class NaukriApplier implements PortalApplier {
//
//    private final ObjectMapper objectMapper;
//    private static final int MAX_CHATBOT_ROUNDS = 10;
//
//    @Override
//    public String portalKey() { return "naukri"; }
//
//    @Override
//    public ApplyResult apply(Page page, JobListing job, AiAnalysis analysis) {
//        log.info("[Naukri] Starting apply for job='{}' at '{}'", job.getJobTitle(), job.getCompanyName());
//        try {
//            page.navigate(job.getJobUrl());
//            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
//            StealthUtil.humanDelay(2000, 3500);
//
//            if (!page.url().contains("naukri.com")) {
//                return ApplyResult.manual(page.url());
//            }
//
//            // ── Find and click the Apply button ──────────────────────────────
//            Locator applyBtn = page.locator(
//                    "button[id*='apply'], " +
//                            "button.apply-button, " +
//                            "button[class*='apply'], " +
//                            "a[class*='apply'], " +
//                            "button:has-text('Apply'), " +
//                            "button:has-text('Apply now')"
//            ).first();
//
//            if (!applyBtn.isVisible()) {
//                return ApplyResult.failed("No apply button found");
//            }
//
//            applyBtn.click();
//            StealthUtil.humanDelay(2000, 3000);
//
//            // ── Handle new tab if opened ──────────────────────────────────────
//            List<Page> pages = page.context().pages();
//            if (pages.size() > 1) {
//                Page newTab = pages.get(pages.size() - 1);
//                if (!newTab.url().contains("naukri.com")) {
//                    newTab.close();
//                    return ApplyResult.manual(job.getJobUrl());
//                }
//                // Continue on new tab if it's still naukri
//                page = newTab;
//                StealthUtil.humanDelay(1500, 2500);
//            }
//
//            // ── Check immediate success (profile-based apply) ─────────────────
//            if (isApplied(page)) {
//                return ApplyResult.success("Applied via Naukri profile (no questions)");
//            }
//
//            // ── Parse the answer bank ─────────────────────────────────────────
//            List<Map<String, String>> answers = parseAnswers(analysis.getApplicationAnswersJson());
//
//            // ── Chatbot / questionnaire loop ──────────────────────────────────
//            // Naukri uses a chatbot-style flow: one question at a time, or a modal with multiple
//            for (int round = 0; round < MAX_CHATBOT_ROUNDS; round++) {
//                StealthUtil.humanDelay(1000, 2000);
//
//                if (isApplied(page)) {
//                    log.info("[Naukri] Applied successfully after {} rounds", round);
//                    return ApplyResult.success("Applied via Naukri chatbot flow");
//                }
//
//                boolean filled = fillVisibleQuestions(page, answers, analysis);
//
//                // Click Send / Next / Submit — whatever is available
//                boolean advanced = clickProceedButton(page);
//
//                if (!filled && !advanced) {
//                    log.warn("[Naukri] No progress on round {} — stopping chatbot loop", round);
//                    break;
//                }
//            }
//
//            if (isApplied(page)) {
//                return ApplyResult.success("Applied via Naukri");
//            }
//
//            log.warn("[Naukri] Could not confirm submission — marking MANUAL");
//            return ApplyResult.manual(job.getJobUrl());
//
//        } catch (Exception e) {
//            log.error("[Naukri] Error: {}", e.getMessage(), e);
//            return ApplyResult.failed("Error: " + e.getMessage());
//        }
//    }
//
//    // ── Fill whatever question fields are currently visible ───────────────────
//    private boolean fillVisibleQuestions(Page page,
//                                         List<Map<String, String>> answers,
//                                         AiAnalysis analysis) {
//        boolean anyFilled = false;
//
//        // ── Text / number inputs ──────────────────────────────────────────────
//        List<ElementHandle> inputs = page.querySelectorAll(
//                "input[type='text']:visible, input[type='number']:visible, " +
//                        "input[placeholder]:visible"
//        );
//        for (ElementHandle input : inputs) {
//            try {
//                if (!input.isVisible()) continue;
//                String current = input.inputValue();
//                if (current != null && !current.isBlank()) continue; // already filled
//
//                String label = resolveLabel(page, input);
//                String answer = findAnswer(answers, label);
//                if (answer == null) answer = fallbackByPlaceholder(input, answers);
//                if (answer == null) continue;
//
//                input.fill(answer);
//                StealthUtil.humanDelay(200, 500);
//                anyFilled = true;
//                log.debug("[Naukri] Filled input '{}' = '{}'", label, answer);
//            } catch (Exception ignored) {}
//        }
//
//        // ── Textareas (cover letter etc.) ─────────────────────────────────────
//        List<ElementHandle> textareas = page.querySelectorAll("textarea:visible");
//        for (ElementHandle ta : textareas) {
//            try {
//                if (ta.inputValue() != null && !ta.inputValue().isBlank()) continue;
//                String label = resolveLabel(page, ta);
//                if (label != null && label.contains("cover") && analysis.getCoverLetter() != null) {
//                    ta.fill(analysis.getCoverLetter());
//                    anyFilled = true;
//                } else {
//                    String answer = findAnswer(answers, label);
//                    if (answer != null) { ta.fill(answer); anyFilled = true; }
//                }
//                StealthUtil.humanDelay(300, 600);
//            } catch (Exception ignored) {}
//        }
//
//        // ── Select / dropdowns ────────────────────────────────────────────────
//        List<ElementHandle> selects = page.querySelectorAll("select:visible");
//        for (ElementHandle sel : selects) {
//            try {
//                String label = resolveLabel(page, sel);
//                String answer = findAnswer(answers, label);
//                if (answer != null) {
//                    sel.selectOption(answer);
//                    StealthUtil.humanDelay(200, 400);
//                    anyFilled = true;
//                }
//            } catch (Exception ignored) {}
//        }
//
//        // ── Radio buttons ─────────────────────────────────────────────────────
//        // Naukri groups radios inside a parent div with the question label above
//        List<ElementHandle> radioGroups = page.querySelectorAll(
//                "div[class*='radio'], div[class*='question'], " +
//                        "div[class*='form-group']:has(input[type='radio'])"
//        );
//        for (ElementHandle group : radioGroups) {
//            try {
//                String questionText = group.textContent().toLowerCase();
//                String answer = findAnswer(answers, questionText);
//                if (answer == null) continue;
//
//                // Try to click the matching radio label
//                List<ElementHandle> labels = group.querySelectorAll("label");
//                for (ElementHandle lbl : labels) {
//                    String lblText = lbl.textContent().trim().toLowerCase();
//                    if (lblText.equals(answer.toLowerCase()) ||
//                            answer.toLowerCase().contains(lblText)) {
//                        lbl.click();
//                        StealthUtil.humanDelay(200, 400);
//                        anyFilled = true;
//                        break;
//                    }
//                }
//            } catch (Exception ignored) {}
//        }
//
//        // ── Checkboxes ────────────────────────────────────────────────────────
//        List<ElementHandle> checkboxes = page.querySelectorAll("input[type='checkbox']:visible");
//        for (ElementHandle cb : checkboxes) {
//            try {
//                boolean checked = Boolean.parseBoolean(cb.getAttribute("checked"));
//                if (checked) continue;
//                String label = resolveLabel(page, cb).toLowerCase();
//                // Auto-check "I agree" / terms-of-service checkboxes
//                if (label.contains("agree") || label.contains("terms") ||
//                        label.contains("consent") || label.contains("confirm")) {
//                    cb.check();
//                    StealthUtil.humanDelay(200, 400);
//                    anyFilled = true;
//                }
//            } catch (Exception ignored) {}
//        }
//
//        return anyFilled;
//    }
//
//    // ── Click whichever proceed button is visible ─────────────────────────────
//    private boolean clickProceedButton(Page page) {
//        String[] buttonTexts = {
//                "Send", "Next", "Proceed", "Submit", "Continue",
//                "Save and Continue", "Apply", "Confirm"
//        };
//        for (String text : buttonTexts) {
//            try {
//                Locator btn = page.locator("button:has-text('" + text + "')").first();
//                if (btn.isVisible() && btn.isEnabled()) {
//                    btn.click();
//                    StealthUtil.humanDelay(1500, 2500);
//                    return true;
//                }
//            } catch (Exception ignored) {}
//        }
//        return false;
//    }
//
//    // ── Resolve the human-readable label for an input ────────────────────────
//    private String resolveLabel(Page page, ElementHandle input) {
//        try {
//            // Method 1: <label for="id">
//            String id = input.getAttribute("id");
//            if (id != null && !id.isBlank()) {
//                ElementHandle lbl = page.querySelector("label[for='" + id + "']");
//                if (lbl != null) return lbl.textContent().trim().toLowerCase();
//            }
//            // Method 2: aria-label
//            String aria = input.getAttribute("aria-label");
//            if (aria != null && !aria.isBlank()) return aria.trim().toLowerCase();
//            // Method 3: placeholder
//            String ph = input.getAttribute("placeholder");
//            if (ph != null && !ph.isBlank()) return ph.trim().toLowerCase();
//            // Method 4: name attribute
//            String name = input.getAttribute("name");
//            if (name != null && !name.isBlank()) return name.trim().toLowerCase();
//            // Method 5: walk up to parent and grab text
//            return input.evaluate("el => el.closest('div')?.querySelector('label,span,p')?.textContent?.toLowerCase() || ''").toString();
//        } catch (Exception e) {
//            return "";
//        }
//    }
//
//    // ── Try placeholder as a secondary label fallback ─────────────────────────
//    private String fallbackByPlaceholder(ElementHandle input, List<Map<String, String>> answers) {
//        try {
//            String ph = input.getAttribute("placeholder");
//            return findAnswer(answers, ph);
//        } catch (Exception e) {
//            return null;
//        }
//    }
//
//    // ── Check if the application is confirmed ─────────────────────────────────
//    private boolean isApplied(Page page) {
//        try {
//            Locator l = page.locator(
//                    "div:has-text('Application submitted'), " +
//                            "div:has-text('Applied successfully'), " +
//                            "div:has-text('You have already applied'), " +
//                            "[class*='success'], " +
//                            "[class*='applied-banner'], " +
//                            ".toast--success, " +
//                            "h1:has-text('Thank you'), " +
//                            "h2:has-text('Thank you')"
//            ).first();
//            return l.isVisible();
//        } catch (Exception e) {
//            return false;
//        }
//    }
//
//    // ── Fuzzy keyword match from answer bank ──────────────────────────────────
//    private String findAnswer(List<Map<String, String>> answers, String label) {
//        if (label == null || label.isBlank() || answers == null || answers.isEmpty()) return null;
//        String lower = label.toLowerCase().trim();
//        // Exact / contains match
//        return answers.stream()
//                .filter(a -> {
//                    String q = a.getOrDefault("question", "").toLowerCase();
//                    return !q.isBlank() && (lower.contains(q) || q.contains(lower));
//                })
//                .map(a -> a.get("answer"))
//                .findFirst()
//                .orElse(null);
//    }
//
//    @SuppressWarnings("unchecked")
//    private List<Map<String, String>> parseAnswers(String json) {
//        if (json == null || json.isBlank()) return List.of();
//        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
//        catch (Exception e) { return List.of(); }
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
import java.util.Optional;

/**
 * NaukriApplier — applies to Naukri.com jobs via Playwright.
 *
 * Naukri Apply Flow:
 * 1. Navigate to job URL
 * 2. Click the "Apply" button
 * 3. If a new tab opens: handle it (external site → MANUAL, still Naukri → continue)
 * 4. Check immediate success (profile-based apply with no questions)
 * 5. If questions appear: loop filling all visible fields until confirmed applied
 *
 * Question handling:
 * - Text inputs: matched via label/placeholder/aria-label against AI-generated answers
 * - Radio groups: matched by group question text → click matching label
 * - Dropdowns: matched via label text
 * - Checkboxes: auto-check "agree/terms/consent/confirm" types
 * - Cover letter: filled from AiAnalysis.coverLetter
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaukriApplier implements PortalApplier {

    private final ObjectMapper objectMapper;

    private static final int MAX_CHATBOT_ROUNDS = 12;
    private static final int TIMEOUT_MS         = 15_000;

    @Override
    public String portalKey() { return "naukri"; }

    @Override
    public ApplyResult apply(Page page, JobListing job, AiAnalysis analysis) {
        log.info("[Naukri] Starting apply for job='{}' at '{}'", job.getJobTitle(), job.getCompanyName());

        try {
            page.setDefaultTimeout(TIMEOUT_MS);
            page.navigate(job.getJobUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            StealthUtil.humanDelay(2500, 4000);

            // Guard: redirected away from Naukri entirely
            if (!page.url().contains("naukri.com")) {
                log.info("[Naukri] Redirected to external URL — marking MANUAL");
                return ApplyResult.manual(page.url());
            }

            // ── Find and click the Apply button ──────────────────────────────
            // Naukri uses multiple apply button variants depending on job type
            Locator applyBtn = page.locator(
                    "button[id*='apply-button'], " +
                            "button.styles_apply-button__3k8Ql, " +
                            "button.styles_jhBtn__pP8Gh, " +
                            "button[class*='apply'], " +
                            "a[class*='apply'], " +
                            "button:has-text('Apply'), " +
                            "button:has-text('Apply now'), " +
                            "button:has-text('Apply for this job')"
            ).first();

            if (!applyBtn.isVisible()) {
                log.warn("[Naukri] No apply button found for job={}", job.getId());
                return ApplyResult.failed("No apply button visible on page");
            }

            log.info("[Naukri] Clicking Apply button for job={}", job.getId());
            applyBtn.click();
            StealthUtil.humanDelay(2500, 4000);

            // ── Handle new tab / popup ────────────────────────────────────────
            List<Page> allPages = page.context().pages();
            if (allPages.size() > 1) {
                Page newTab = allPages.get(allPages.size() - 1);
                String newUrl = newTab.url();
                log.info("[Naukri] New tab opened: {}", newUrl);

                if (!newUrl.contains("naukri.com")) {
                    // External company site — can't auto-apply
                    newTab.close();
                    log.info("[Naukri] External site — MANUAL");
                    return ApplyResult.manual(newUrl);
                }

                // Still on Naukri — switch to the new tab
                page = newTab;
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                StealthUtil.humanDelay(1500, 2500);
            }

            // ── Check for immediate success (one-click profile apply) ─────────
            if (isApplied(page)) {
                log.info("[Naukri] Applied immediately (profile-based, no questions) for job={}", job.getId());
                return ApplyResult.success("Applied via Naukri profile (one-click)");
            }

            // ── Parse AI-generated answer bank ────────────────────────────────
            List<Map<String, String>> answers = parseAnswers(analysis.getApplicationAnswersJson());
            log.info("[Naukri] Answer bank has {} entries for job={}", answers.size(), job.getId());

            // ── Chatbot / question loop ───────────────────────────────────────
            // Naukri shows questions one-at-a-time or in a modal form.
            // We fill visible fields, click proceed, repeat until applied or stuck.
            for (int round = 0; round < MAX_CHATBOT_ROUNDS; round++) {
                StealthUtil.humanDelay(1200, 2200);

                if (isApplied(page)) {
                    log.info("[Naukri] Applied successfully after {} rounds for job={}", round, job.getId());
                    return ApplyResult.success("Applied via Naukri (answered " + round + " question rounds)");
                }

                boolean filled   = fillVisibleFields(page, answers, analysis);
                boolean advanced = clickProceedButton(page);

                log.debug("[Naukri] Round {}: filled={} advanced={}", round, filled, advanced);

                if (!filled && !advanced) {
                    log.warn("[Naukri] No progress on round {} for job={} — breaking", round, job.getId());
                    break;
                }
            }

            // Final check after loop
            if (isApplied(page)) {
                return ApplyResult.success("Applied via Naukri");
            }

            log.warn("[Naukri] Could not confirm submission for job={} — marking MANUAL", job.getId());
            return ApplyResult.manual(job.getJobUrl());

        } catch (Exception e) {
            log.error("[Naukri] Error applying to job={}: {}", job.getId(), e.getMessage(), e);
            return ApplyResult.failed("Error: " + e.getMessage());
        }
    }

    // ── Fill all currently visible question fields ────────────────────────────

    private boolean fillVisibleFields(Page page,
                                      List<Map<String, String>> answers,
                                      AiAnalysis analysis) {
        boolean anyFilled = false;

        // ── Text inputs and number inputs ─────────────────────────────────────
        List<ElementHandle> inputs = page.querySelectorAll(
                "input[type='text']:visible, " +
                        "input[type='number']:visible, " +
                        "input[placeholder]:visible"
        );
        for (ElementHandle input : inputs) {
            try {
                if (!input.isVisible()) continue;
                String current = input.inputValue();
                if (current != null && !current.isBlank()) continue;

                String label  = resolveLabel(page, input);
                String answer = findAnswer(answers, label);
                if (answer == null) answer = findAnswer(answers, input.getAttribute("placeholder"));
                if (answer == null) continue;

                input.fill(answer);
                StealthUtil.humanDelay(200, 500);
                anyFilled = true;
                log.debug("[Naukri] Filled text input '{}' = '{}'", label, answer);
            } catch (Exception ignored) {}
        }

        // ── Textareas (cover letter, additional info) ─────────────────────────
        List<ElementHandle> textareas = page.querySelectorAll("textarea:visible");
        for (ElementHandle ta : textareas) {
            try {
                String val = ta.inputValue();
                if (val != null && !val.isBlank()) continue;

                String label = resolveLabel(page, ta);
                String lower = label != null ? label.toLowerCase() : "";

                if ((lower.contains("cover") || lower.contains("letter"))
                        && analysis.getCoverLetter() != null) {
                    ta.fill(analysis.getCoverLetter());
                    anyFilled = true;
                } else {
                    String answer = findAnswer(answers, label);
                    if (answer != null) {
                        ta.fill(answer);
                        anyFilled = true;
                    }
                }
                StealthUtil.humanDelay(300, 600);
            } catch (Exception ignored) {}
        }

        // ── Select / dropdown elements ────────────────────────────────────────
        List<ElementHandle> selects = page.querySelectorAll("select:visible");
        for (ElementHandle sel : selects) {
            try {
                String label  = resolveLabel(page, sel);
                String answer = findAnswer(answers, label);
                if (answer != null) {
                    sel.selectOption(answer);
                    StealthUtil.humanDelay(200, 400);
                    anyFilled = true;
                    log.debug("[Naukri] Selected dropdown '{}' = '{}'", label, answer);
                }
            } catch (Exception ignored) {}
        }

        // ── Radio button groups ───────────────────────────────────────────────
        // Naukri wraps radio groups in div containers with question text above
        List<ElementHandle> radioGroups = page.querySelectorAll(
                "div[class*='radio-group'], " +
                        "div[class*='question'], " +
                        "div[class*='form-group']:has(input[type='radio']), " +
                        "div[class*='chatbot']:has(input[type='radio'])"
        );
        for (ElementHandle group : radioGroups) {
            try {
                String questionText = group.textContent().toLowerCase();
                String answer = findAnswer(answers, questionText);
                if (answer == null) continue;

                List<ElementHandle> labels = group.querySelectorAll("label");
                for (ElementHandle lbl : labels) {
                    String lblText = lbl.textContent().trim().toLowerCase();
                    if (lblText.equals(answer.toLowerCase()) ||
                            answer.toLowerCase().contains(lblText) ||
                            lblText.contains(answer.toLowerCase())) {
                        lbl.click();
                        StealthUtil.humanDelay(200, 400);
                        anyFilled = true;
                        log.debug("[Naukri] Clicked radio '{}' for question '{}'", lblText, questionText.substring(0, Math.min(50, questionText.length())));
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        // ── Checkboxes — auto-tick agreement / consent / terms types ──────────
        List<ElementHandle> checkboxes = page.querySelectorAll("input[type='checkbox']:visible");
        for (ElementHandle cb : checkboxes) {
            try {
                // isChecked() is more reliable than getAttribute("checked")
                if (cb.isChecked()) continue;
                String label = resolveLabel(page, cb).toLowerCase();
                if (label.contains("agree") || label.contains("terms") ||
                        label.contains("consent") || label.contains("confirm") ||
                        label.contains("acknowledge")) {
                    cb.check();
                    StealthUtil.humanDelay(200, 400);
                    anyFilled = true;
                    log.debug("[Naukri] Auto-checked consent checkbox: '{}'", label);
                }
            } catch (Exception ignored) {}
        }

        return anyFilled;
    }

    // ── Click the next/proceed/submit button ──────────────────────────────────

    private boolean clickProceedButton(Page page) {
        // Priority order: Submit/Apply first, then Next/Continue
        String[] buttonTexts = {
                "Submit", "Apply", "Confirm",
                "Next", "Continue", "Proceed",
                "Send", "Save and Continue"
        };
        for (String text : buttonTexts) {
            try {
                Locator btn = page.locator("button:has-text('" + text + "')").first();
                if (btn.isVisible() && btn.isEnabled()) {
                    log.debug("[Naukri] Clicking proceed button: '{}'", text);
                    btn.click();
                    StealthUtil.humanDelay(1500, 2500);
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    // ── Check whether the application was successfully submitted ─────────────

    private boolean isApplied(Page page) {
        try {
            // Naukri shows a success state in various ways depending on job type
            // Using Playwright's :text() pseudo-class for more precise text matching
            String[] successIndicators = {
                    "text='Application submitted'",
                    "text='Applied successfully'",
                    "text='You have already applied'",
                    "text='Application received'",
                    "[class*='success-screen']",
                    "[class*='apply-success']",
                    "[class*='applied']",
                    ".toast--success",
                    "[class*='toastSuccess']"
            };

            for (String selector : successIndicators) {
                try {
                    Locator l = page.locator(selector).first();
                    if (l.isVisible()) {
                        log.info("[Naukri] Success indicator found: {}", selector);
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Resolve label for a form element ─────────────────────────────────────

    private String resolveLabel(Page page, ElementHandle input) {
        try {
            String id = input.getAttribute("id");
            if (id != null && !id.isBlank()) {
                ElementHandle lbl = page.querySelector("label[for='" + id + "']");
                if (lbl != null) return lbl.textContent().trim().toLowerCase();
            }
            String aria = input.getAttribute("aria-label");
            if (aria != null && !aria.isBlank()) return aria.trim().toLowerCase();

            String ph = input.getAttribute("placeholder");
            if (ph != null && !ph.isBlank()) return ph.trim().toLowerCase();

            String name = input.getAttribute("name");
            if (name != null && !name.isBlank()) return name.trim().toLowerCase();

            // Walk up DOM to find a label or span near the input
            Object parentText = input.evaluate(
                    "el => el.closest('div')?.querySelector('label,span.label,p.question,div.question')?.textContent?.trim()?.toLowerCase() || ''"
            );
            return parentText != null ? parentText.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    // ── Fuzzy answer lookup ───────────────────────────────────────────────────

    private String findAnswer(List<Map<String, String>> answers, String label) {
        if (label == null || label.isBlank() || answers == null || answers.isEmpty()) return null;
        String lower = label.toLowerCase().trim();

        // Priority 1: exact contains match
        Optional<String> exact = answers.stream()
                .filter(a -> {
                    String q = a.getOrDefault("question", "").toLowerCase();
                    return !q.isBlank() && (lower.contains(q) || q.contains(lower));
                })
                .map(a -> a.get("answer"))
                .findFirst();
        if (exact.isPresent()) return exact.get();

        // Priority 2: keyword overlap match (any significant word)
        return answers.stream()
                .filter(a -> {
                    String q = a.getOrDefault("question", "").toLowerCase();
                    if (q.isBlank()) return false;
                    String[] words = q.split("\\s+");
                    for (String word : words) {
                        if (word.length() > 3 && lower.contains(word)) return true;
                    }
                    return false;
                })
                .map(a -> a.get("answer"))
                .findFirst()
                .orElse(null);
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