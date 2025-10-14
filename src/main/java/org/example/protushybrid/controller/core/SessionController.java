package org.example.protushybrid.controller.core;

import org.example.protushybrid.domain.core.LearningObject;
import org.example.protushybrid.domain.core.LearningSession;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.core.LearningObjectRepository;
import org.example.protushybrid.repository.core.LearningSessionRepository;
import org.example.protushybrid.service.recommendation.RecommendationService;
import org.example.protushybrid.service.core.ContentLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for learning session management.
 * Handles modular concept sessions where learners navigate freely between learning objects.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final LearningSessionRepository sessionRepo;
    private final LearnerRepository learnerRepo;
    private final ConceptRepository conceptRepo;
    private final LearningObjectRepository loRepo;
    private final RecommendationService recommendationService;
    private final ContentLoader contentLoader;

    public SessionController(LearningSessionRepository sessionRepo,
                            LearnerRepository learnerRepo,
                            ConceptRepository conceptRepo,
                            LearningObjectRepository loRepo,
                            RecommendationService recommendationService,
                            ContentLoader contentLoader) {
        this.sessionRepo = sessionRepo;
        this.learnerRepo = learnerRepo;
        this.conceptRepo = conceptRepo;
        this.loRepo = loRepo;
        this.recommendationService = recommendationService;
        this.contentLoader = contentLoader;
    }

    /**
     * Get or create learning session for a learner in a concept.
     * Returns all available modules (LOs) for the concept with recommendation.
     *
     * GET /api/sessions?learnerId=X&conceptId=Y
     */
    @GetMapping
    public ResponseEntity<SessionDTO> getOrCreateSession(
            @RequestParam Long learnerId,
            @RequestParam Long conceptId) {

        log.info("Getting or creating session for learner {} in concept {}", learnerId, conceptId);

        var learner = learnerRepo.findById(learnerId)
                .orElseThrow(() -> new RuntimeException("Learner not found: " + learnerId));
        var concept = conceptRepo.findById(conceptId)
                .orElseThrow(() -> new RuntimeException("Concept not found: " + conceptId));

        // Find or create active session
        Instant dayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        var session = sessionRepo.findActiveSession(learnerId, conceptId, dayAgo)
                .orElseGet(() -> {
                    log.info("Creating new session for learner {} in concept {}", learnerId, conceptId);
                    var newSession = new LearningSession();
                    newSession.setLearner(learner);
                    newSession.setConcept(concept);
                    return sessionRepo.save(newSession);
                });

        // Load all available LOs for this concept
        var allLOs = loRepo.findByConcept(concept);

        // Get recommendation for next module with trace
        Long recommendedLoId = null;
        List<String> trace = new ArrayList<>();
        try {
            var recommendedLO = recommendationService.recommendNext(learnerId, conceptId);
            if (recommendedLO.isPresent()) {
                LearningObject lo = recommendedLO.get();
                log.info("[SESSION] Recommendation service returned: id={}, type='{}', title='{}'",
                        lo.getId(), lo.getType(), lo.getTitle());
                recommendedLoId = lo.getId();

                // Get detailed trace for the recommendation process
                trace = new ArrayList<>(recommendationService.getDetailedTrace(learnerId, conceptId, 10));

                // Add the actual result to the trace
                trace.add("");
                trace.add("[ACTUAL RECOMMENDATION]");
                trace.add(String.format("   Module ID: %d", lo.getId()));
                trace.add(String.format("   Type: %s", lo.getType()));
                trace.add(String.format("   Title: %s", lo.getTitle()));
            } else {
                log.info("[SESSION] Recommendation service returned: empty (no recommendation)");
                trace = List.of("[NO RECOMMENDATION]", "   No suitable learning object found");
            }
        } catch (Exception e) {
            log.warn("Failed to get recommendation for learner {} in concept {}: {}",
                    learnerId, conceptId, e.getMessage());
            trace = List.of("[ERROR]", String.format("   %s", e.getMessage()));
        }

        // Build response
        var dto = new SessionDTO(
                session.getSessionId(),
                learnerId,
                conceptId,
                concept.getName(),
                allLOs.stream().map(this::toModuleDTO).collect(Collectors.toList()),
                session.getVisitedLoIds(),
                session.getDoneLoIds(),
                session.getRevisitedLoIds(),
                recommendedLoId,
                session.isCompleted(),
                trace
        );

        log.info("Returning session {} with {} modules, {} visited, {} done, {} revisited, recommended: {}",
                session.getSessionId(), allLOs.size(), session.getVisitedLoIds().size(),
                session.getDoneLoIds().size(), session.getRevisitedLoIds().size(), recommendedLoId);

        return ResponseEntity.ok(dto);
    }

    /**
     * Record that learner viewed a module (LO).
     * Adds to session sequence if not already present.
     *
     * POST /api/sessions/visit
     */
    @PostMapping("/visit")
    public ResponseEntity<Void> recordVisit(
            @RequestParam String sessionId,
            @RequestParam Long loId) {

        log.debug("Recording visit to LO {} in session {}", loId, sessionId);

        var session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        boolean added = session.addToSequence(loId);
        sessionRepo.save(session);

        if (added) {
            log.info("Added LO {} to session {} sequence (now {} items)",
                    loId, sessionId, session.getVisitedLoIds().size());
        } else {
            log.debug("LO {} already in session {} sequence", loId, sessionId);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Mark a learning object as done (completed) for the first time.
     * Adds to done list and visited sequence.
     *
     * POST /api/sessions/mark-done?sessionId=X&loId=Y
     */
    @PostMapping("/mark-done")
    public ResponseEntity<Void> markAsDone(
            @RequestParam String sessionId,
            @RequestParam Long loId) {

        log.debug("Marking LO {} as done in session {}", loId, sessionId);

        var session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        // Mark as done using new method
        boolean newlyDone = session.markAsDone(loId);
        sessionRepo.save(session);

        if (newlyDone) {
            log.info("Marked LO {} as done in session {} (now {} done, {} visited)",
                    loId, sessionId, session.getDoneLoIds().size(), session.getVisitedLoIds().size());
        } else {
            log.debug("LO {} already marked as done in session {}", loId, sessionId);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Mark a learning object as revisited (completed again after being marked done).
     * Updates recommendations even when all activities in a content type are done.
     *
     * POST /api/sessions/mark-revisited?sessionId=X&loId=Y
     */
    @PostMapping("/mark-revisited")
    public ResponseEntity<Void> markAsRevisited(
            @RequestParam String sessionId,
            @RequestParam Long loId) {

        log.debug("Marking LO {} as revisited in session {}", loId, sessionId);

        var session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        // Mark as revisited
        boolean marked = session.markAsRevisited(loId);
        sessionRepo.save(session);

        if (marked) {
            log.info("Marked LO {} as revisited in session {} (now {} revisits)",
                    loId, sessionId, session.getRevisitedLoIds().size());
        } else {
            log.warn("Cannot mark LO {} as revisited - not yet marked as done in session {}", loId, sessionId);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Mark session as completed.
     *
     * POST /api/sessions/{sessionId}/complete
     */
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<Void> completeSession(@PathVariable String sessionId) {
        log.info("Marking session {} as completed", sessionId);

        var session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        session.setCompleted(true);
        sessionRepo.save(session);

        return ResponseEntity.ok().build();
    }

    // ========== DTOs ==========

    private ModuleDTO toModuleDTO(LearningObject lo) {
        // Use custom title if present, otherwise auto-generate
        String title = lo.getTitle() != null && !lo.getTitle().isBlank()
                ? lo.getTitle()
                : generateTitle(lo);

        return new ModuleDTO(
                lo.getId(),
                lo.getType(),
                title,
                generateContent(lo),
                lo.getEstTimeMin() != null ? lo.getEstTimeMin() : 5
        );
    }

    private String generateTitle(LearningObject lo) {
        String type = lo.getType();
        String conceptName = lo.getConcept() != null ? lo.getConcept().getName() : "Concept";

        return switch (type.toLowerCase()) {
            case "theory", "explanation" -> conceptName + " - Theory";
            case "example" -> conceptName + " - Example";
            case "figure", "diagram" -> conceptName + " - Visual Aid";
            case "checkpoint" -> conceptName + " - Quick Check";
            case "definition" -> conceptName + " - Definition";
            default -> conceptName + " - " + type;
        };
    }

    private String generateContent(LearningObject lo) {
        // 1. Try to load from markdown file if sourceUri points to a resource
        if (lo.getSourceUri() != null && lo.getSourceUri().startsWith("classpath:")) {
            String resourcePath = lo.getSourceUri().replace("classpath:", "");
            String content = contentLoader.loadContent(resourcePath);
            if (content != null) {
                return content;
            }
        }

        // 2. Try to load by convention: content/{conceptCode}_{type}_{id}.md
        if (lo.getConcept() != null && lo.getConcept().getCode() != null) {
            String conceptCode = lo.getConcept().getCode();
            // Extract index from sourceUri or use ID
            int index = extractIndexFromSourceUri(lo.getSourceUri(), lo.getId());
            String conventionPath = contentLoader.buildContentPath(conceptCode, lo.getType(), index);
            String content = contentLoader.loadContent(conventionPath);
            if (content != null) {
                return content;
            }
        }

        // 3. Fallback to generated placeholder content
        String type = lo.getType();
        String conceptName = lo.getConcept() != null ? lo.getConcept().getName() : "this concept";

        return switch (type.toLowerCase()) {
            case "theory", "explanation" -> String.format(
                    "# %s\n\n" +
                    "This section provides a comprehensive explanation of %s. " +
                    "Understanding this concept is essential for mastering the fundamentals.\n\n" +
                    "## Key Points\n" +
                    "- Core principle 1\n" +
                    "- Core principle 2\n" +
                    "- Core principle 3\n\n" +
                    "Take your time to understand each point before moving forward.",
                    conceptName, conceptName
            );
            case "example" -> String.format(
                    "# Worked Example: %s\n\n" +
                    "Let's walk through a practical example to see %s in action.\n\n" +
                    "## Problem\n" +
                    "Given a scenario where...\n\n" +
                    "## Solution\n" +
                    "Step 1: Identify the key elements\n" +
                    "Step 2: Apply the concept\n" +
                    "Step 3: Verify the result\n\n" +
                    "Try to work through similar examples on your own!",
                    conceptName, conceptName
            );
            case "figure", "diagram" -> String.format(
                    "# Visual Representation: %s\n\n" +
                    "[Diagram/Figure would be displayed here]\n\n" +
                    "This visual aid helps illustrate the relationships and structure of %s. " +
                    "Study the diagram carefully and note how different components interact.",
                    conceptName, conceptName
            );
            case "checkpoint" -> String.format(
                    "# Quick Check: %s\n\n" +
                    "Before continuing, let's verify your understanding with a quick question.\n\n" +
                    "**Question:** What is the main purpose of %s?\n\n" +
                    "(Interactive checkpoint would appear here)",
                    conceptName, conceptName
            );
            case "definition" -> String.format(
                    "# Definition: %s\n\n" +
                    "**%s** is defined as...\n\n" +
                    "This fundamental definition forms the basis for all subsequent learning in this area.",
                    conceptName, conceptName
            );
            default -> String.format(
                    "# %s\n\nContent for %s module.",
                    conceptName, type
            );
        };
    }

    public record SessionDTO(
            String sessionId,
            Long learnerId,
            Long conceptId,
            String conceptName,
            List<ModuleDTO> modules,
            List<Long> visitedModuleIds,
            List<Long> doneModuleIds,
            List<Long> revisitedModuleIds,
            Long recommendedModuleId,
            boolean completed,
            List<String> recommendationTrace
    ) {}

    /**
     * Extract index from sourceUri (e.g., "synthetic://Variables/T/0" -> 1, "synthetic://Variables/T/1" -> 2)
     * Falls back to using LO ID if extraction fails.
     */
    private int extractIndexFromSourceUri(String sourceUri, Long loId) {
        if (sourceUri != null && sourceUri.contains("/")) {
            String[] parts = sourceUri.split("/");
            String lastPart = parts[parts.length - 1];
            try {
                // Convert 0-based to 1-based index
                return Integer.parseInt(lastPart) + 1;
            } catch (NumberFormatException e) {
                // Ignore, fall through
            }
        }
        // Fallback to LO ID modulo 10 to keep numbers small
        return loId != null ? (int) (loId % 10) + 1 : 1;
    }

    public record ModuleDTO(
            Long id,
            String type,
            String title,
            String content,
            int estimatedMinutes
    ) {}
}
