package org.example.protushybrid.controller.exercise;

import org.example.protushybrid.domain.exercise.Exercise;
import org.example.protushybrid.domain.exercise.FlaggedExercise;
import org.example.protushybrid.repository.exercise.ExerciseRepository;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.exercise.LearnerExerciseAttemptRepository;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.exercise.FlaggedExerciseRepository;
import org.example.protushybrid.service.exercise.GradingService;
import org.example.protushybrid.service.exercise.ExerciseGenerationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for exercise grading, retrieval, and generation
 */
@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final GradingService gradingService;
    private final ExerciseGenerationService generationService;
    private final ExerciseRepository exerciseRepository;
    private final ConceptRepository conceptRepository;
    private final LearnerExerciseAttemptRepository attemptRepository;
    private final LearnerRepository learnerRepository;
    private final FlaggedExerciseRepository flaggedExerciseRepository;

    public ExerciseController(GradingService gradingService,
                            ExerciseGenerationService generationService,
                            ExerciseRepository exerciseRepository,
                            ConceptRepository conceptRepository,
                            LearnerExerciseAttemptRepository attemptRepository,
                            LearnerRepository learnerRepository,
                            FlaggedExerciseRepository flaggedExerciseRepository) {
        this.gradingService = gradingService;
        this.generationService = generationService;
        this.exerciseRepository = exerciseRepository;
        this.conceptRepository = conceptRepository;
        this.attemptRepository = attemptRepository;
        this.learnerRepository = learnerRepository;
        this.flaggedExerciseRepository = flaggedExerciseRepository;
    }

    /**
     * Get all exercises for a concept (excludes flagged exercises and optionally attempted ones)
     * GET /api/exercises?conceptId=X&learnerId=Y (optional)
     */
    @GetMapping
    public List<ExerciseDto> getExercisesByConceptId(
            @RequestParam Long conceptId,
            @RequestParam(required = false) Long learnerId) {
        var concept = conceptRepository.findById(conceptId)
                .orElseThrow(() -> new RuntimeException("Concept not found: " + conceptId));

        // Get attempted exercise IDs for this learner if provided
        List<Long> attemptedIds = List.of();
        if (learnerId != null) {
            attemptedIds = attemptRepository.findAttemptedExerciseIds(learnerId, conceptId);
        }
        final List<Long> excludeIds = attemptedIds;

        // Get all exercises for this concept, excluding already attempted ones
        return exerciseRepository.findAll().stream()
                .filter(ex -> ex.getConcept() != null
                        && ex.getConcept().getId().equals(conceptId))
                .filter(ex -> !excludeIds.contains(ex.getId())) // Exclude already attempted
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get exercise IDs already attempted by a learner for a concept
     * GET /api/exercises/attempted?learnerId=X&conceptId=Y
     */
    @GetMapping("/attempted")
    public List<Long> getAttemptedExerciseIds(
            @RequestParam Long learnerId,
            @RequestParam Long conceptId) {
        return attemptRepository.findAttemptedExerciseIds(learnerId, conceptId);
    }

    /**
     * Generate a new exercise for a concept using LLM
     * POST /api/exercises/generate
     */
    @PostMapping("/generate")
    public ExerciseDto generateExercise(@RequestBody GenerateRequest request) {
        Exercise generated = generationService.generateExercise(
                request.conceptId,
                request.type != null ? request.type : "MC",
                request.difficulty != null ? request.difficulty : "medium",
                request.previousQuestions != null ? request.previousQuestions : List.of()
        );
        return toDto(generated);
    }

    @PostMapping("/grade")
    public GradingService.GradingResponse gradeExercise(@RequestBody GradeRequest request) {
        return gradingService.gradeSubmission(
                request.exerciseId,
                request.learnerId,
                request.userAnswer,
                request.explainReasoning != null && request.explainReasoning
        );
    }

    private ExerciseDto toDto(Exercise exercise) {
        return new ExerciseDto(
                exercise.getId(),
                exercise.getConcept().getId(),
                exercise.getExerciseType().toString(),
                exercise.getQuestionJson(),
                exercise.getMaxScore(),
                exercise.getGradingMode().toString(),
                exercise.getIsCheckpoint()
        );
    }

    public record GradeRequest(
            Long exerciseId,
            Long learnerId,
            String userAnswer,
            Boolean explainReasoning
    ) {}

    public record ExerciseDto(
            Long id,
            Long conceptId,
            String type,
            Object questionJson,  // Let Spring serialize Map as JSON
            Double maxScore,
            String gradingMode,
            Boolean isCheckpoint
    ) {}

    /**
     * Flag exercise as problematic
     * POST /api/exercises/{id}/flag
     */
    @PostMapping("/{exerciseId}/flag")
    public FlagResponse flagExercise(
            @PathVariable Long exerciseId,
            @RequestBody FlagRequest request) {

        var exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new RuntimeException("Exercise not found: " + exerciseId));

        var learner = learnerRepository.findById(request.learnerId())
                .orElseThrow(() -> new RuntimeException("Learner not found: " + request.learnerId()));

        // Create flagged exercise entry
        FlaggedExercise flagged = new FlaggedExercise();
        flagged.setExercise(exercise);
        flagged.setLearner(learner);
        flagged.setUserAnswer(request.userAnswer() != null ? request.userAnswer() : "");
        flagged.setGradingScore(request.gradingScore());
        flagged.setGradingFeedback(request.gradingFeedback());
        flagged.setFlagReason(request.reason());
        flagged.setReviewed(false);

        // Store code separately for CODE_WRITE and CODE_READ exercises
        if (exercise.getExerciseType() == Exercise.ExerciseType.CODE_WRITE) {
            // For CODE_WRITE, the user's answer IS the code
            flagged.setCodeContent(request.userAnswer());
            System.out.println("Code content logged for CODE_WRITE exercise " + exerciseId +
                             " (length: " + (request.userAnswer() != null ? request.userAnswer().length() : 0) + " chars)");
        } else if (exercise.getExerciseType() == Exercise.ExerciseType.CODE_READ) {
            // For CODE_READ, extract the code snippet from the question JSON
            Map<String, Object> questionJson = exercise.getQuestionJson();
            Object codeSnippet = questionJson.get("codeSnippet");
            if (codeSnippet != null) {
                flagged.setCodeContent(codeSnippet.toString());
                System.out.println("Code snippet logged for CODE_READ exercise " + exerciseId +
                                 " (length: " + codeSnippet.toString().length() + " chars)");
            }
        }

        flaggedExerciseRepository.save(flagged);

        System.out.println("Exercise " + exerciseId + " flagged by learner " + request.learnerId() +
                          " and saved to database. Reason: " + request.reason());

        return new FlagResponse(true, "Exercise flagged successfully");
    }

    /**
     * Clear/remove flag from exercise
     * DELETE /api/exercises/flags/{flagId}
     */
    @DeleteMapping("/flags/{flagId}")
    public FlagResponse clearFlag(@PathVariable Long flagId) {
        var flagged = flaggedExerciseRepository.findById(flagId)
                .orElseThrow(() -> new RuntimeException("Flagged exercise not found: " + flagId));

        flaggedExerciseRepository.delete(flagged);
        System.out.println("Flag " + flagId + " cleared and removed from database");

        return new FlagResponse(true, "Flag cleared successfully");
    }

    /**
     * Accept flag (mark as reviewed but keep flagged)
     * PATCH /api/exercises/flags/{flagId}/accept
     */
    @PatchMapping("/flags/{flagId}/accept")
    public FlagResponse acceptFlag(@PathVariable Long flagId) {
        var flagged = flaggedExerciseRepository.findById(flagId)
                .orElseThrow(() -> new RuntimeException("Flagged exercise not found: " + flagId));

        flagged.setReviewed(true);
        flagged.setReviewedAt(java.time.Instant.now());
        flaggedExerciseRepository.save(flagged);

        System.out.println("Flag " + flagId + " accepted and marked as reviewed");

        return new FlagResponse(true, "Flag accepted successfully");
    }

    public record FlagResponse(
            boolean success,
            String message
    ) {}

    public record FlagRequest(
            Long learnerId,
            String userAnswer,
            Double gradingScore,
            String gradingFeedback,
            String reason
    ) {}

    public record GenerateRequest(
            Long conceptId,
            String type,        // MC, GAP_FILL, FREE_TEXT (default: MC)
            String difficulty,   // easy, medium, hard (default: medium)
            List<String> previousQuestions  // List of previously asked questions to avoid duplication
    ) {}
}
