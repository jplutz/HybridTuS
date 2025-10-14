package org.example.protushybrid.service.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service for loading learning object content from markdown files.
 *
 * Content files are stored in src/main/resources/content/ directory.
 * File naming convention: {conceptCode}_{type}_{index}.md
 *
 * Example: RECURSION_T_1.md, RECURSION_E_1.md
 */
@Service
public class ContentLoader {

    private static final Logger log = LoggerFactory.getLogger(ContentLoader.class);

    /**
     * Load content from a markdown file in the classpath.
     *
     * @param resourcePath Path relative to src/main/resources/ (e.g., "content/RECURSION_T_1.md")
     * @return File content as string, or null if not found
     */
    public String loadContent(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);

            if (!resource.exists()) {
                log.warn("Content file not found: {}", resourcePath);
                return null;
            }

            byte[] bytes = resource.getInputStream().readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);

            log.debug("Loaded content from {}: {} characters", resourcePath, content.length());
            return content;

        } catch (IOException e) {
            log.error("Failed to load content from {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    /**
     * Build resource path from concept code and LO type.
     *
     * @param conceptCode Concept code (e.g., "RECURSION")
     * @param loType LO type (e.g., "T", "E", "F")
     * @param index Index for multiple LOs of same type (1-based)
     * @return Resource path (e.g., "content/RECURSION_T_1.md")
     */
    public String buildContentPath(String conceptCode, String loType, int index) {
        return String.format("content/%s_%s_%d.md", conceptCode, loType, index);
    }
}
