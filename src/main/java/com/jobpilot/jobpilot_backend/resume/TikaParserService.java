package com.jobpilot.jobpilot_backend.resume;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
public class TikaParserService {

    private static final int MAX_CHARS = 100_000;

    public String extractText(MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            BodyContentHandler handler = new BodyContentHandler(MAX_CHARS);
            Metadata metadata        = new Metadata();
            ParseContext context     = new ParseContext();
            AutoDetectParser parser  = new AutoDetectParser();

            parser.parse(stream, handler, metadata, context);

            String text = handler.toString().trim();
            log.info("Tika extracted {} chars from file: {}", text.length(), file.getOriginalFilename());
            return text;

        } catch (Exception e) {
         log.warn("Tika failed to extract text from {}: {}", file.getOriginalFilename(), e.getMessage());
            return "";
        }
    }
}