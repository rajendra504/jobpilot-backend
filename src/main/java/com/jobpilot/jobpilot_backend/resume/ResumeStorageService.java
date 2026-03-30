package com.jobpilot.jobpilot_backend.resume;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class ResumeStorageService {

    @Value("${app.upload.resume-dir}")
    private String resumeDir;

    public String save(MultipartFile file, Long userId) throws IOException {
        Path userDir = Paths.get(resumeDir, String.valueOf(userId));
        Files.createDirectories(userDir);

        String uniqueFilename = UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
        Path destination = userDir.resolve(uniqueFilename);

        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        log.info("Saved resume to: {}", destination);

        return destination.toString();
    }

    public void delete(String filePath) {
        try {
            Path path = Paths.get(filePath);
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                log.info("Deleted resume file: {}", filePath);
            } else {
                log.warn("Resume file not found for deletion: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete resume file: {}", filePath, e);
        }
    }

    private String sanitize(String filename) {
        if (filename == null) return "resume";
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}