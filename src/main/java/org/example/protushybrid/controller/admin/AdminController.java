package org.example.protushybrid.controller.admin;

import org.example.protushybrid.domain.exercise.FlaggedExercise;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.core.LearningSession;
import org.example.protushybrid.repository.exercise.FlaggedExerciseRepository;
import org.example.protushybrid.repository.core.LearningSessionRepository;
import org.example.protushybrid.repository.mining.MinedSupportRepository;
import org.example.protushybrid.service.admin.SyntheticDataGenerator;
import org.example.protushybrid.service.mining.EvaluationService;
import org.example.protushybrid.service.mining.SequenceMiningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin endpoints for system maintenance, testing, and validation.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final SyntheticDataGenerator dataGenerator;
    private final SequenceMiningService miningService;
    private final EvaluationService evaluationService;
    private final LearningSessionRepository sessionRepo;
    private final MinedSupportRepository minedRepo;
    private final FlaggedExerciseRepository flaggedExerciseRepo;

    // Validation thresholds (from mining spec)
    private static final int MIN_EVENTS = 30;
    private static final int MIN_LEARNERS = 5;
    private static final double MIN_SUPPORT = 0.25;

    public AdminController(SyntheticDataGenerator dataGenerator,
                          SequenceMiningService miningService,
                          EvaluationService evaluationService,
                          LearningSessionRepository sessionRepo,
                          MinedSupportRepository minedRepo,
                          FlaggedExerciseRepository flaggedExerciseRepo) {
        this.dataGenerator = dataGenerator;
        this.miningService = miningService;
        this.evaluationService = evaluationService;
        this.sessionRepo = sessionRepo;
        this.minedRepo = minedRepo;
        this.flaggedExerciseRepo = flaggedExerciseRepo;
    }

    /**
     * Get synthetic data generation status.
     * GET /api/admin/data/status
     *
     * @return Status report showing if data exists and its statistics
     */
    @GetMapping("/data/status")
    public ResponseEntity<?> getDataStatus() {
        try {
            // Check if synthetic learners exist
            List<Learner> synthLearners = sessionRepo.findAll().stream()
                .map(s -> s.getLearner())
                .filter(l -> l.getExternalRef() != null && l.getExternalRef().startsWith("synth_"))
                .distinct()
                .toList();

            if (synthLearners.isEmpty()) {
                return ResponseEntity.ok(Map.of("exists", false));
            }

            // Count sessions and interactions
            List<LearningSession> synthSessions = sessionRepo.findAll().stream()
                .filter(s -> s.getLearner().getExternalRef() != null &&
                           s.getLearner().getExternalRef().startsWith("synth_"))
                .toList();

            int totalInteractions = synthSessions.stream()
                .mapToInt(s -> s.getCompletionSequence().size())
                .sum();

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("exists", true);
            status.put("numLearners", synthLearners.size());
            status.put("totalSessions", synthSessions.size());
            status.put("totalInteractions", totalInteractions);

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get data status", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Generate synthetic learning session data for mining demonstration.
     * POST /api/admin/data/generate
     *
     * Prevents regeneration if data already exists to avoid duplicate key errors.
     *
     * @return Generation report with statistics, or error if data exists
     */
    @PostMapping("/data/generate")
    public ResponseEntity<?> generateSyntheticData() {
        try {
            // Check if synthetic data already exists
            List<Learner> existingLearners = sessionRepo.findAll().stream()
                .map(s -> s.getLearner())
                .filter(l -> l.getExternalRef() != null && l.getExternalRef().startsWith("synth_"))
                .distinct()
                .toList();

            if (!existingLearners.isEmpty()) {
                log.warn("Data generation blocked - data already exists with {} learners", existingLearners.size());
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "error", "Data already exists. Cannot regenerate to prevent duplicate key errors.",
                        "existingLearners", existingLearners.size(),
                        "suggestion", "Use the pipeline steps individually (mining, validation, evaluation) with existing data."
                    ));
            }

            // Generate data
            SyntheticDataGenerator.GenerationReport report = dataGenerator.generateAll();
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Data generation failed", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Run sequence mining on all completed sessions.
     * POST /api/admin/mining/run
     *
     * @return Mining statistics
     */
    @PostMapping("/mining/run")
    public ResponseEntity<?> runMining() {
        try {
            Instant startTime = Instant.now();
            miningService.mineAllClusters();
            Instant endTime = Instant.now();

            long patternCount = minedRepo.count();
            // Count only FSLSM clusters (exclude GLOBAL fallback cluster)
            long distinctClusters = minedRepo.findAll().stream()
                .map(p -> p.getClusterKey())
                .filter(key -> !"GLOBAL".equals(key))
                .distinct()
                .count();

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("status", "success");
            stats.put("patternsExtracted", patternCount);
            stats.put("distinctClusters", distinctClusters);
            stats.put("durationMs", endTime.toEpochMilli() - startTime.toEpochMilli());

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Mining failed", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Run hold-out evaluation on test sessions.
     * POST /api/admin/evaluation/run
     *
     * @return Evaluation report with accuracy and coverage metrics
     */
    @PostMapping("/evaluation/run")
    public ResponseEntity<?> runEvaluation() {
        try {
            // Query test sessions from database (marked during data generation)
            List<String> testSessionIds = sessionRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsTestSet()))
                .map(LearningSession::getSessionId)
                .collect(Collectors.toList());

            if (testSessionIds.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "error", "No test sessions found",
                        "suggestion", "Generate synthetic data first using POST /api/admin/data/generate"
                    ));
            }

            EvaluationService.EvaluationReport report = evaluationService.evaluate(testSessionIds);

            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Evaluation failed", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Validate mining thresholds and data quality.
     * GET /api/admin/validation/check
     *
     * @return Validation report showing threshold compliance
     */
    @GetMapping("/validation/check")
    public ResponseEntity<?> validateThresholds() {
        try {
            Map<String, Object> validation = new LinkedHashMap<>();

            // Check session count
            long totalSessions = sessionRepo.count();
            validation.put("totalSessions", totalSessions);
            validation.put("minSessionsRequired", 100);
            validation.put("sessionCheckPassed", totalSessions >= 100);

            // Check mined patterns
            long totalPatterns = minedRepo.count();
            validation.put("totalPatterns", totalPatterns);
            validation.put("minPatternsRequired", 10);
            validation.put("patternCheckPassed", totalPatterns >= 10);

            // Check MIN_LEARNERS threshold
            var patterns = minedRepo.findAll();
            long patternsUnderMinLearners = patterns.stream()
                .filter(p -> p.getNLearners() < MIN_LEARNERS)
                .count();
            double learnerThresholdCompliance = totalPatterns > 0 ?
                1.0 - ((double) patternsUnderMinLearners / totalPatterns) : 0.0;

            validation.put("minLearnersThreshold", MIN_LEARNERS);
            validation.put("learnerThresholdCompliance", String.format("%.1f%%", learnerThresholdCompliance * 100));
            validation.put("learnerCheckPassed", learnerThresholdCompliance >= 0.9);

            // Check MIN_SUPPORT threshold
            long patternsUnderMinSupport = patterns.stream()
                .filter(p -> p.getSupport() < MIN_SUPPORT)
                .count();
            double supportThresholdCompliance = totalPatterns > 0 ?
                1.0 - ((double) patternsUnderMinSupport / totalPatterns) : 0.0;

            validation.put("minSupportThreshold", MIN_SUPPORT);
            validation.put("supportThresholdCompliance", String.format("%.1f%%", supportThresholdCompliance * 100));
            validation.put("supportCheckPassed", supportThresholdCompliance >= 0.8);

            // Overall status
            boolean allPassed = (boolean) validation.get("sessionCheckPassed") &&
                               (boolean) validation.get("patternCheckPassed") &&
                               (boolean) validation.get("learnerCheckPassed") &&
                               (boolean) validation.get("supportCheckPassed");

            validation.put("overallStatus", allPassed ? "PASSED" : "FAILED");

            return ResponseEntity.ok(validation);
        } catch (Exception e) {
            log.error("Validation failed", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get flagged exercises needing review.
     * GET /api/admin/exercises/flagged
     *
     * @return List of flagged exercises with learner and exercise details
     */
    @GetMapping("/exercises/flagged")
    public ResponseEntity<?> getFlaggedExercises() {
        try {
            List<FlaggedExercise> flaggedExercises = flaggedExerciseRepo.findAllByOrderByFlaggedAtDesc();

            // Convert to DTOs for frontend
            List<FlaggedExerciseDto> dtos = flaggedExercises.stream()
                .map(fe -> new FlaggedExerciseDto(
                    fe.getId(),
                    fe.getExercise().getId(),
                    fe.getLearner().getId(),
                    fe.getLearner().getDisplayName(),
                    fe.getExercise().getExerciseType().toString(),
                    extractQuestionText(fe.getExercise().getQuestionJson()),
                    fe.getUserAnswer(),
                    fe.getGradingScore(),
                    fe.getGradingFeedback(),
                    fe.getFlagReason(),
                    fe.getFlaggedAt().toString(),
                    fe.getReviewed(),
                    fe.getCodeContent()  // Include code content for CODE_WRITE/CODE_READ exercises
                ))
                .toList();

            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Failed to retrieve flagged exercises", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Extract question text from question JSON for display
     */
    private String extractQuestionText(Map<String, Object> questionJson) {
        if (questionJson == null) {
            return "No question text available";
        }

        // Try to extract question text based on format
        if (questionJson.containsKey("question")) {
            return String.valueOf(questionJson.get("question"));
        } else if (questionJson.containsKey("questions")) {
            // For multi-question format, combine all questions
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) questionJson.get("questions");
            if (questions != null && !questions.isEmpty()) {
                return questions.stream()
                    .map(q -> String.valueOf(q.get("text")))
                    .collect(Collectors.joining(" | "));
            }
        } else if (questionJson.containsKey("template")) {
            return String.valueOf(questionJson.get("template"));
        }

        return "Question text not available";
    }

    /**
     * DTO for flagged exercise response
     */
    public record FlaggedExerciseDto(
        Long id,
        Long exerciseId,
        Long learnerId,
        String learnerName,
        String exerciseType,
        String questionText,
        String userAnswer,
        Double gradingScore,
        String gradingFeedback,
        String flagReason,
        String flaggedAt,
        Boolean reviewed,
        /**
         * Code content for CODE_WRITE/CODE_READ exercises:
         * - CODE_WRITE: Contains the learner's submitted code
         * - CODE_READ: Contains the code snippet from the question that learner analyzed
         */
        String codeContent
    ) {}

    /**
     * Run complete pipeline: generate → mine → validate → evaluate.
     * POST /api/admin/pipeline/run
     *
     * Useful for end-to-end testing of the recommendation system.
     * If data already exists, skips generation step to prevent duplicate key errors.
     */
    @PostMapping("/pipeline/run")
    public ResponseEntity<?> runCompletePipeline() {
        try {
            Map<String, Object> results = new LinkedHashMap<>();

            // Check if synthetic data already exists
            List<Learner> existingLearners = sessionRepo.findAll().stream()
                .map(s -> s.getLearner())
                .filter(l -> l.getExternalRef() != null && l.getExternalRef().startsWith("synth_"))
                .distinct()
                .toList();

            boolean dataExists = !existingLearners.isEmpty();

            // Step 1: Generate data (skip if already exists)
            if (dataExists) {
                log.info("Pipeline Step 1/4: Skipping data generation (data already exists)...");
                results.put("generationSkipped", true);
                results.put("generation", Map.of(
                    "skipped", true,
                    "reason", "Data already exists - skipping to prevent duplicate key errors",
                    "existingLearners", existingLearners.size()
                ));
            } else {
                log.info("Pipeline Step 1/4: Generating synthetic data...");
                SyntheticDataGenerator.GenerationReport genReport = dataGenerator.generateAll();
                results.put("generationSkipped", false);
                results.put("generation", genReport);
            }

            // Step 2: Run mining
            log.info("Pipeline Step 2/4: Mining patterns...");
            Instant miningStart = Instant.now();
            miningService.mineAllClusters();
            long patternCount = minedRepo.count();
            // Count only FSLSM clusters (exclude GLOBAL fallback cluster)
            long distinctClusters = minedRepo.findAll().stream()
                .map(p -> p.getClusterKey())
                .filter(key -> !"GLOBAL".equals(key))
                .distinct()
                .count();
            results.put("mining", Map.of(
                "patternsExtracted", patternCount,
                "distinctClusters", distinctClusters,
                "durationMs", Instant.now().toEpochMilli() - miningStart.toEpochMilli()
            ));

            // Step 3: Validate thresholds
            log.info("Pipeline Step 3/4: Validating thresholds...");
            var validation = validateThresholds().getBody();
            results.put("validation", validation);

            // Check if validation passed before proceeding to evaluation
            @SuppressWarnings("unchecked")
            Map<String, Object> validationMap = (Map<String, Object>) validation;
            String validationStatus = (String) validationMap.get("overallStatus");

            if (!"PASSED".equals(validationStatus)) {
                log.warn("Validation failed - skipping evaluation step");
                results.put("evaluation", Map.of(
                    "status", "SKIPPED",
                    "reason", "Validation checks failed - insufficient data quality for meaningful evaluation"
                ));
                results.put("pipelineStatus", "COMPLETED_WITH_WARNINGS");
                results.put("completedAt", Instant.now().toString());
                return ResponseEntity.ok(results);
            }

            // Step 4: Run evaluation (only if validation passed)
            log.info("Pipeline Step 4/4: Evaluating on hold-out set...");
            List<String> testSessionIds = sessionRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsTestSet()))
                .map(LearningSession::getSessionId)
                .collect(Collectors.toList());

            if (testSessionIds.isEmpty()) {
                log.warn("No test sessions found - skipping evaluation");
                results.put("evaluation", Map.of(
                    "status", "SKIPPED",
                    "reason", "No test sessions found (expected if using real data only)"
                ));
                results.put("pipelineStatus", "COMPLETED_WITH_WARNINGS");
                results.put("completedAt", Instant.now().toString());
                return ResponseEntity.ok(results);
            }

            EvaluationService.EvaluationReport evalReport = evaluationService.evaluate(testSessionIds);
            results.put("evaluation", evalReport);

            // Summary
            results.put("pipelineStatus", "COMPLETED");
            results.put("completedAt", Instant.now().toString());

            log.info("Pipeline completed successfully");
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            log.error("Pipeline execution failed", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "pipelineStatus", "FAILED",
                    "error", e.getMessage()
                ));
        }
    }
}
