package com.jobpilot.jobpilot_backend.resume;

import com.jobpilot.jobpilot_backend.exception.ResourceNotFoundException;
import com.jobpilot.jobpilot_backend.resume.dto.ResumeResponse;
import com.jobpilot.jobpilot_backend.user.User;
import com.jobpilot.jobpilot_backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final int  MAX_RESUMES_PER_USER = 5;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final ResumeRepository     resumeRepository;
    private final UserRepository       userRepository;
    private final ResumeStorageService storageService;
    private final TikaParserService    tikaParser;

    // ── Upload ────────────────────────────────────────────────

    @Transactional
    public ResumeResponse upload(Long userId, MultipartFile file) throws IOException {
        validateFile(file);

        if (resumeRepository.countByUserId(userId) >= MAX_RESUMES_PER_USER) {
            throw new IllegalStateException(
                    "Maximum of " + MAX_RESUMES_PER_USER + " resumes allowed. Delete one to upload a new one.");
        }

        User user = findUser(userId);

        // 1. Extract text BEFORE saving to disk (stream is still open)
        String extractedText = tikaParser.extractText(file);

        // 2. Save file to disk
        String filePath = storageService.save(file, userId);

        // 3. If this is the user's first resume, mark it as primary automatically
        boolean isPrimary = resumeRepository.countByUserId(userId) == 0;

        Resume resume = Resume.builder()
                .user(user)
                .originalFilename(file.getOriginalFilename())
                .filePath(filePath)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .extractedText(extractedText.isEmpty() ? null : extractedText)
                .primary(isPrimary)
                .build();

        Resume saved = resumeRepository.save(resume);
        log.info("Uploaded resume id={} for userId={}, primary={}", saved.getId(), userId, isPrimary);

        return toResponse(saved);
    }

    // ── List all ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ResumeResponse> listResumes(Long userId) {
        return resumeRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Get one ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ResumeResponse getResume(Long userId, Long resumeId) {
        Resume resume = findResume(userId, resumeId);
        return toResponse(resume);
    }

    // ── Set primary ───────────────────────────────────────────

    @Transactional
    public ResumeResponse setPrimary(Long userId, Long resumeId) {
        Resume resume = findResume(userId, resumeId);

        resumeRepository.clearPrimaryForUser(userId);   // clear all first
        resume.setPrimary(true);
        Resume saved = resumeRepository.save(resume);

        log.info("Set resume id={} as primary for userId={}", resumeId, userId);
        return toResponse(saved);
    }

    // ── Delete ────────────────────────────────────────────────

    @Transactional
    public void delete(Long userId, Long resumeId) {
        Resume resume = findResume(userId, resumeId);

        storageService.delete(resume.getFilePath());
        resumeRepository.delete(resume);

        // If the deleted resume was primary and others exist, auto-promote the newest one
        if (resume.isPrimary()) {
            resumeRepository.findByUserIdOrderByCreatedAtDesc(userId)
                    .stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setPrimary(true);
                        resumeRepository.save(next);
                        log.info("Auto-promoted resume id={} to primary after deletion", next.getId());
                    });
        }

        log.info("Deleted resume id={} for userId={}", resumeId, userId);
    }

    // ── Package-level: used by AI Engine in Module 6 ─────────

    /**
     * Returns the raw extracted text of a user's primary resume.
     * Only called internally — not exposed via REST.
     */
    public String getPrimaryResumeText(Long userId) {
        return resumeRepository.findByUserIdAndPrimaryTrue(userId)
                .map(Resume::getExtractedText)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No primary resume found for user: " + userId));
    }

    // ── Validation ────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Only PDF and DOCX files are accepted. Received: " + contentType);
        }

        long maxSize = 5 * 1024 * 1024L; // 5MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size must not exceed 5MB");
        }
    }

    // ── Mapping ───────────────────────────────────────────────

    private ResumeResponse toResponse(Resume resume) {
        String preview = null;
        boolean textExtracted = false;

        if (resume.getExtractedText() != null && !resume.getExtractedText().isBlank()) {
            textExtracted = true;
            String text = resume.getExtractedText();
            preview = text.length() > 300 ? text.substring(0, 300) + "..." : text;
        }

        return new ResumeResponse(
                resume.getId(),
                resume.getOriginalFilename(),
                resume.getContentType(),
                resume.getFileSize(),
                resume.isPrimary(),
                preview,
                textExtracted,
                resume.getCreatedAt(),
                resume.getUpdatedAt()
        );
    }

    // ── Internal helpers ──────────────────────────────────────

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private Resume findResume(Long userId, Long resumeId) {
        return resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resume not found: " + resumeId));
    }
}