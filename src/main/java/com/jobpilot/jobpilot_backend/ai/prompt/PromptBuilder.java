//package com.jobpilot.jobpilot_backend.ai.prompt;
//
//import com.jobpilot.jobpilot_backend.profile.UserProfile;
//import com.jobpilot.jobpilot_backend.scraper.JobListing;
//import org.springframework.stereotype.Component;
//
//@Component
//public class PromptBuilder {
//
//    public String buildAnalysisPrompt(UserProfile profile,
//                                      String resumeText,
//                                      JobListing job) {
//
//        String skills = formatSkills(profile.getSkillsJson());
//        String qaBank = buildQaSection(profile);
//
//        String safeResume = trimText(resumeText, 2000);
//
//        return """
//You are a STRICT JSON generator.
//
//RULES:
//- Return ONLY valid JSON
//- NO markdown
//- NO explanations
//- Keep responses SHORT
//- Ensure valid JSON formatting
//
//========================
//CANDIDATE
//========================
//Name: %s
//Location: %s
//Title: %s
//Experience: %s
//Skills: %s
//
//========================
//JOB
//========================
//Title: %s
//Company: %s
//Description:
//%s
//
//========================
//RESUME
//========================
//%s
//
//========================
//Q&A
//========================
//%s
//
//========================
//INSTRUCTIONS
//========================
//
//1. matchScore: integer (0-100)
//
//2. decision:
//   APPLY if >= 45 else SKIP
//
//3. decisionReason:
//   max 20 words
//
//4. missingSkills:
//   Return a VALID JSON array of strings.
//
//   STRICT RULES:
//   - Each item must be a properly closed string
//   - No trailing commas
//   - No incomplete values
//   - Max 5 items
//   - If unsure → return []
//
//   Example:
//   ["Kafka", "Docker"]
//
//   If none → []
//
//5. coverLetter:
//   Write a SHORT cover letter (max 80 words).
//
//   IMPORTANT:
//   - Must be a SINGLE LINE string
//   - Do NOT use line breaks
//   - Do NOT use quotes inside text
//   - Use simple sentences
//
//   If decision is SKIP → return ""
//
//6. resumeSnippet:
//   Provide 2–3 short bullet points.
//
//   IMPORTANT:
//   - Return as a SINGLE LINE string
//   - Separate bullets using " | "
//   - Do NOT use line breaks
//
//   Example:
//   "Built REST APIs | Developed Angular UI | Optimized SQL queries"
//
//   If decision is SKIP → return ""
//
//7. applicationAnswers:
//   You are given a Q&A bank (user's predefined answers).
//
//   Q&A bank is provided in the Q&A section above.
//
//   Your task:
//   - Read ALL questions from the Q&A section
//   - Select ONLY questions relevant to this job
//   - Return answers EXACTLY as provided
//   - DO NOT modify answers
//   - DO NOT generate new answers
//
//   Rules:
//   - Include work authorization if job is location-based
//   - Include notice period if job involves hiring timeline
//   - Include experience questions if job is technical
//   - Include relocation if job location differs
//   - Include salary if job mentions compensation
//
//   Return format:
//   [
//     {"question": "<question>", "answer": "<answer>"}
//   ]
//
//IMPORTANT for answers:
//- For questions asking "how many years", answer with a NUMBER ONLY (e.g. "3" not "3 years")
//- For yes/no questions, answer "Yes" or "No" only
//- For CTC/salary questions, answer in LPA number only (e.g. "6" not "6 LPA")
//- For notice period, answer number of days only (e.g. "15" not "15 days")
//
//========================
//OUTPUT FORMAT
//========================
//
//Return STRICT VALID JSON.
//
//Rules:
//- Use double quotes ONLY
//- Escape special characters properly
//- NO line breaks inside values
//- NO markdown
//- NO trailing commas
//
//Output:
//{
//  "matchScore": 70,
//  "decision": "APPLY",
//  "decisionReason": "Good match",
//  "missingSkills": [],
//  "coverLetter": "Short single line text",
//  "resumeSnippet": "Point1 | Point2",
//  "applicationAnswers": []
//}
//
//RETURN ONLY JSON
//""".formatted(
//                safe(getUserName(profile)),
//                safe(profile.getLocation()),
//                safe(getCurrentTitle(profile)),
//                safe(getYearsExperience(profile)),
//                skills,
//                safe(job.getJobTitle()),
//                safe(job.getCompanyName()),
//                safe(job.getDescription()),
//                safeResume,
//                qaBank
//        );
//    }
//
//
//    private String safe(Object val) {
//        return val != null ? val.toString() : "Not specified";
//    }
//
//    private String trimText(String text, int max) {
//        if (text == null) return "Resume not available";
//        return text.length() > max ? text.substring(0, max) : text;
//    }
//
//    private String formatSkills(String skillsJson) {
//        if (skillsJson == null) return "Not specified";
//        return skillsJson.replaceAll("[\\[\\]\"]", "");
//    }
//
//    private String buildQaSection(UserProfile p) {
//        if (p.getQaBankJson() == null || p.getQaBankJson().isBlank()) {
//            return "No Q&A provided";
//        }
//        return p.getQaBankJson();
//    }
//
//    private String getUserName(UserProfile p) {
//        return (p.getUser() != null) ? p.getUser().getFullName() : null;
//    }
//
//    private String getCurrentTitle(UserProfile p) {
//        if (p.getSummary() != null && !p.getSummary().isBlank()) {
//            return p.getSummary().split("\n")[0];
//        }
//        return "Associate Software Engineer";
//    }
//
//    private String getYearsExperience(UserProfile p) {
//        return "3+";
//    }
//}


package com.jobpilot.jobpilot_backend.ai.prompt;

import com.jobpilot.jobpilot_backend.profile.UserProfile;
import com.jobpilot.jobpilot_backend.scraper.JobListing;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildAnalysisPrompt(UserProfile profile, String resumeText, JobListing job) {
        String skills = formatSkills(profile.getSkillsJson());
        String qaBank = buildQaSection(profile);
        String safeResume = trimText(resumeText, 1500); // Reduced slightly to save token space

        return """
You are an expert Recruitment AI. Analyze the candidate against the job.

STRICT JSON RULES:
1. Return ONLY valid JSON.
2. NO markdown (no ```json).
3. NO conversational filler.
4. If a field is unknown, return [].

========================
CANDIDATE DATA
========================
Name: %s
Skills: %s
Resume: %s

========================
JOB DATA
========================
Title: %s
Company: %s
Description: %s

========================
Q&A BANK (Use these answers for applicationAnswers)
========================
%s

========================
ANALYSIS STEPS
========================
1. Identify MISSING SKILLS: Compare JOB Description vs CANDIDATE Skills/Resume. 
   - List technologies mentioned in the job that are NOT in the candidate profile.
   - Max 5 items.

2. Calculate matchScore (0-100).
3. Decide: "APPLY" if score >= 45, else "SKIP".

========================
REQUIRED OUTPUT FORMAT (STRICT)
========================
{
  "matchScore": 85,
  "decision": "APPLY",
  "decisionReason": "Short reason here.",
  "missingSkills": ["Docker", "Kubernetes"],
  "coverLetter": "Single line cover letter under 80 words.",
  "resumeSnippet": "Point 1 | Point 2 | Point 3",
  "applicationAnswers": [
    {"question": "Relevant question from bank", "answer": "Exact answer from bank"}
  ]
}

RETURN ONLY THE JSON OBJECT.
""".formatted(
                safe(getUserName(profile)),
                skills,
                safeResume,
                safe(job.getJobTitle()),
                safe(job.getCompanyName()),
                safe(job.getDescription()),
                qaBank
        );
    }

    private String safe(Object val) {
        if (val == null) return "Not specified";
        // Clean strings to prevent breaking the prompt structure
        return val.toString().replaceAll("[\\r\\n]+", " ").trim();
    }

    private String trimText(String text, int max) {
        if (text == null) return "Resume not available";
        return text.length() > max ? text.substring(0, max) : text;
    }

    private String formatSkills(String skillsJson) {
        if (skillsJson == null || skillsJson.isBlank()) return "None";
        // Ensure skills are passed as a clean comma-separated list so the AI can read them
        return skillsJson.replaceAll("[\\[\\]\"]", "").trim();
    }

    private String buildQaSection(UserProfile p) {
        return (p.getQaBankJson() == null || p.getQaBankJson().isBlank())
                ? "No Q&A bank provided." : p.getQaBankJson();
    }

    private String getUserName(UserProfile p) {
        return (p.getUser() != null) ? p.getUser().getFullName() : "Candidate";
    }
}