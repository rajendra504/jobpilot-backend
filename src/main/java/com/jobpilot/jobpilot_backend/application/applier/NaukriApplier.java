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
public class NaukriApplier implements PortalApplier {

    private final ObjectMapper objectMapper;
    private static final int MAX_CHATBOT_ROUNDS = 10;

    @Override
    public String portalKey() { return "naukri"; }

    @Override
    public ApplyResult apply(Page page, JobListing job, AiAnalysis analysis) {
        log.info("[Naukri] Starting apply for job='{}' at '{}'", job.getJobTitle(), job.getCompanyName());
        try {
            page.navigate(job.getJobUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            StealthUtil.humanDelay(2000, 3500);

            if (!page.url().contains("naukri.com")) {
                return ApplyResult.manual(page.url());
            }

            // ── Find and click the Apply button ──────────────────────────────
            Locator applyBtn = page.locator(
                    "button[id*='apply'], " +
                            "button.apply-button, " +
                            "button[class*='apply'], " +
                            "a[class*='apply'], " +
                            "button:has-text('Apply'), " +
                            "button:has-text('Apply now')"
            ).first();

            if (!applyBtn.isVisible()) {
                return ApplyResult.failed("No apply button found");
            }

            applyBtn.click();
            StealthUtil.humanDelay(2000, 3000);

            // ── Handle new tab if opened ──────────────────────────────────────
            List<Page> pages = page.context().pages();
            if (pages.size() > 1) {
                Page newTab = pages.get(pages.size() - 1);
                if (!newTab.url().contains("naukri.com")) {
                    newTab.close();
                    return ApplyResult.manual(job.getJobUrl());
                }
                // Continue on new tab if it's still naukri
                page = newTab;
                StealthUtil.humanDelay(1500, 2500);
            }

            // ── Check immediate success (profile-based apply) ─────────────────
            if (isApplied(page)) {
                return ApplyResult.success("Applied via Naukri profile (no questions)");
            }

            // ── Parse the answer bank ─────────────────────────────────────────
            List<Map<String, String>> answers = parseAnswers(analysis.getApplicationAnswersJson());

            // ── Chatbot / questionnaire loop ──────────────────────────────────
            // Naukri uses a chatbot-style flow: one question at a time, or a modal with multiple
            for (int round = 0; round < MAX_CHATBOT_ROUNDS; round++) {
                StealthUtil.humanDelay(1000, 2000);

                if (isApplied(page)) {
                    log.info("[Naukri] Applied successfully after {} rounds", round);
                    return ApplyResult.success("Applied via Naukri chatbot flow");
                }

                boolean filled = fillVisibleQuestions(page, answers, analysis);

                // Click Send / Next / Submit — whatever is available
                boolean advanced = clickProceedButton(page);

                if (!filled && !advanced) {
                    log.warn("[Naukri] No progress on round {} — stopping chatbot loop", round);
                    break;
                }
            }

            if (isApplied(page)) {
                return ApplyResult.success("Applied via Naukri");
            }

            log.warn("[Naukri] Could not confirm submission — marking MANUAL");
            return ApplyResult.manual(job.getJobUrl());

        } catch (Exception e) {
            log.error("[Naukri] Error: {}", e.getMessage(), e);
            return ApplyResult.failed("Error: " + e.getMessage());
        }
    }

    // ── Fill whatever question fields are currently visible ───────────────────
    private boolean fillVisibleQuestions(Page page,
                                         List<Map<String, String>> answers,
                                         AiAnalysis analysis) {
        boolean anyFilled = false;

        // ── Text / number inputs ──────────────────────────────────────────────
        List<ElementHandle> inputs = page.querySelectorAll(
                "input[type='text']:visible, input[type='number']:visible, " +
                        "input[placeholder]:visible"
        );
        for (ElementHandle input : inputs) {
            try {
                if (!input.isVisible()) continue;
                String current = input.inputValue();
                if (current != null && !current.isBlank()) continue; // already filled

                String label = resolveLabel(page, input);
                String answer = findAnswer(answers, label);
                if (answer == null) answer = fallbackByPlaceholder(input, answers);
                if (answer == null) continue;

                input.fill(answer);
                StealthUtil.humanDelay(200, 500);
                anyFilled = true;
                log.debug("[Naukri] Filled input '{}' = '{}'", label, answer);
            } catch (Exception ignored) {}
        }

        // ── Textareas (cover letter etc.) ─────────────────────────────────────
        List<ElementHandle> textareas = page.querySelectorAll("textarea:visible");
        for (ElementHandle ta : textareas) {
            try {
                if (ta.inputValue() != null && !ta.inputValue().isBlank()) continue;
                String label = resolveLabel(page, ta);
                if (label != null && label.contains("cover") && analysis.getCoverLetter() != null) {
                    ta.fill(analysis.getCoverLetter());
                    anyFilled = true;
                } else {
                    String answer = findAnswer(answers, label);
                    if (answer != null) { ta.fill(answer); anyFilled = true; }
                }
                StealthUtil.humanDelay(300, 600);
            } catch (Exception ignored) {}
        }

        // ── Select / dropdowns ────────────────────────────────────────────────
        List<ElementHandle> selects = page.querySelectorAll("select:visible");
        for (ElementHandle sel : selects) {
            try {
                String label = resolveLabel(page, sel);
                String answer = findAnswer(answers, label);
                if (answer != null) {
                    sel.selectOption(answer);
                    StealthUtil.humanDelay(200, 400);
                    anyFilled = true;
                }
            } catch (Exception ignored) {}
        }

        // ── Radio buttons ─────────────────────────────────────────────────────
        // Naukri groups radios inside a parent div with the question label above
        List<ElementHandle> radioGroups = page.querySelectorAll(
                "div[class*='radio'], div[class*='question'], " +
                        "div[class*='form-group']:has(input[type='radio'])"
        );
        for (ElementHandle group : radioGroups) {
            try {
                String questionText = group.textContent().toLowerCase();
                String answer = findAnswer(answers, questionText);
                if (answer == null) continue;

                // Try to click the matching radio label
                List<ElementHandle> labels = group.querySelectorAll("label");
                for (ElementHandle lbl : labels) {
                    String lblText = lbl.textContent().trim().toLowerCase();
                    if (lblText.equals(answer.toLowerCase()) ||
                            answer.toLowerCase().contains(lblText)) {
                        lbl.click();
                        StealthUtil.humanDelay(200, 400);
                        anyFilled = true;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        // ── Checkboxes ────────────────────────────────────────────────────────
        List<ElementHandle> checkboxes = page.querySelectorAll("input[type='checkbox']:visible");
        for (ElementHandle cb : checkboxes) {
            try {
                boolean checked = Boolean.parseBoolean(cb.getAttribute("checked"));
                if (checked) continue;
                String label = resolveLabel(page, cb).toLowerCase();
                // Auto-check "I agree" / terms-of-service checkboxes
                if (label.contains("agree") || label.contains("terms") ||
                        label.contains("consent") || label.contains("confirm")) {
                    cb.check();
                    StealthUtil.humanDelay(200, 400);
                    anyFilled = true;
                }
            } catch (Exception ignored) {}
        }

        return anyFilled;
    }

    // ── Click whichever proceed button is visible ─────────────────────────────
    private boolean clickProceedButton(Page page) {
        String[] buttonTexts = {
                "Send", "Next", "Proceed", "Submit", "Continue",
                "Save and Continue", "Apply", "Confirm"
        };
        for (String text : buttonTexts) {
            try {
                Locator btn = page.locator("button:has-text('" + text + "')").first();
                if (btn.isVisible() && btn.isEnabled()) {
                    btn.click();
                    StealthUtil.humanDelay(1500, 2500);
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    // ── Resolve the human-readable label for an input ────────────────────────
    private String resolveLabel(Page page, ElementHandle input) {
        try {
            // Method 1: <label for="id">
            String id = input.getAttribute("id");
            if (id != null && !id.isBlank()) {
                ElementHandle lbl = page.querySelector("label[for='" + id + "']");
                if (lbl != null) return lbl.textContent().trim().toLowerCase();
            }
            // Method 2: aria-label
            String aria = input.getAttribute("aria-label");
            if (aria != null && !aria.isBlank()) return aria.trim().toLowerCase();
            // Method 3: placeholder
            String ph = input.getAttribute("placeholder");
            if (ph != null && !ph.isBlank()) return ph.trim().toLowerCase();
            // Method 4: name attribute
            String name = input.getAttribute("name");
            if (name != null && !name.isBlank()) return name.trim().toLowerCase();
            // Method 5: walk up to parent and grab text
            return input.evaluate("el => el.closest('div')?.querySelector('label,span,p')?.textContent?.toLowerCase() || ''").toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ── Try placeholder as a secondary label fallback ─────────────────────────
    private String fallbackByPlaceholder(ElementHandle input, List<Map<String, String>> answers) {
        try {
            String ph = input.getAttribute("placeholder");
            return findAnswer(answers, ph);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Check if the application is confirmed ─────────────────────────────────
    private boolean isApplied(Page page) {
        try {
            Locator l = page.locator(
                    "div:has-text('Application submitted'), " +
                            "div:has-text('Applied successfully'), " +
                            "div:has-text('You have already applied'), " +
                            "[class*='success'], " +
                            "[class*='applied-banner'], " +
                            ".toast--success, " +
                            "h1:has-text('Thank you'), " +
                            "h2:has-text('Thank you')"
            ).first();
            return l.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    // ── Fuzzy keyword match from answer bank ──────────────────────────────────
    private String findAnswer(List<Map<String, String>> answers, String label) {
        if (label == null || label.isBlank() || answers == null || answers.isEmpty()) return null;
        String lower = label.toLowerCase().trim();
        // Exact / contains match
        return answers.stream()
                .filter(a -> {
                    String q = a.getOrDefault("question", "").toLowerCase();
                    return !q.isBlank() && (lower.contains(q) || q.contains(lower));
                })
                .map(a -> a.get("answer"))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseAnswers(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }
}