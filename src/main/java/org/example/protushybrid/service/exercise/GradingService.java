package org.example.protushybrid.service.exercise;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.protushybrid.domain.exercise.Exercise;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.exercise.LearnerExerciseAttempt;
import org.example.protushybrid.repository.exercise.ExerciseRepository;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.exercise.LearnerExerciseAttemptRepository;
import org.example.protushybrid.service.llm.LlmClient;
import org.example.protushybrid.service.llm.LlmGradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Multi-modal grading service supporting deterministic, LLM, and hybrid grading.
 * Routes exercise types to appropriate grading strategies.
 */
@Service
public class GradingService {

    private static final Logger log = LoggerFactory.getLogger(GradingService.class);

    private final ExerciseRepository exerciseRepo;
    private final LearnerRepository learnerRepo;
    private final LearnerExerciseAttemptRepository attemptRepo;
    private final LlmGradingService llmGrading;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public GradingService(ExerciseRepository exerciseRepo,
                          LearnerRepository learnerRepo,
                          LearnerExerciseAttemptRepository attemptRepo,
                          LlmGradingService llmGrading,
                          LlmClient llmClient,
                          ObjectMapper objectMapper) {
        this.exerciseRepo = exerciseRepo;
        this.learnerRepo = learnerRepo;
        this.attemptRepo = attemptRepo;
        this.llmGrading = llmGrading;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Grade an exercise submission
     * @param exerciseId Exercise ID
     * @param learnerId Learner ID
     * @param userAnswer User's answer (format depends on exercise type)
     * @param explainReasoning Optional: request LLM explanation for deterministic grading
     * @return Grading result with score, feedback, and metadata
     */
    @Transactional
    public GradingResponse gradeSubmission(Long exerciseId, Long learnerId, String userAnswer, boolean explainReasoning) {
        Exercise exercise = exerciseRepo.findById(exerciseId)
                .orElseThrow(() -> new RuntimeException("Exercise not found: " + exerciseId));
        Learner learner = learnerRepo.findById(learnerId)
                .orElseThrow(() -> new RuntimeException("Learner not found: " + learnerId));

        // Log code submissions for CODE_WRITE and CODE_READ exercises
        if (exercise.getExerciseType() == Exercise.ExerciseType.CODE_WRITE ||
            exercise.getExerciseType() == Exercise.ExerciseType.CODE_READ) {
            log.info("Code submission received for {} exercise {} by learner {} (code length: {} chars)",
                    exercise.getExerciseType(), exerciseId, learnerId,
                    userAnswer != null ? userAnswer.length() : 0);
        }

        // Record attempt to prevent duplicate exercises
        recordAttempt(learner, exercise);

        return switch (exercise.getGradingMode()) {
            case DETERMINISTIC -> gradeDeterministic(exercise, userAnswer, explainReasoning);
            case LLM -> gradeLlm(exercise, learner, userAnswer);
            case HYBRID -> gradeHybrid(exercise, learner, userAnswer);
        };
    }

    /**
     * Record that a learner has attempted an exercise
     */
    private void recordAttempt(Learner learner, Exercise exercise) {
        // Check if already recorded (to handle retries)
        if (!attemptRepo.existsByLearnerIdAndExerciseId(learner.getId(), exercise.getId())) {
            LearnerExerciseAttempt attempt = new LearnerExerciseAttempt();
            attempt.setLearner(learner);
            attempt.setExercise(exercise);
            attemptRepo.save(attempt);
            log.debug("Recorded exercise attempt: learner={}, exercise={}", learner.getId(), exercise.getId());
        }
    }

    private GradingResponse gradeDeterministic(Exercise exercise, String userAnswer, boolean explainReasoning) {
        double score;
        boolean correct;
        String feedback = "";

        switch (exercise.getExerciseType()) {
            case MC -> {
                // Check if this is new 2-question MC format
                Map<String, Object> questionJson = exercise.getQuestionJson();
                if (questionJson.containsKey("questions")) {
                    // New format: 2 questions for 0.0, 0.5, 1.0 scoring
                    McGradingResult mcResult = gradeMultipleChoiceTwoQuestions(exercise, userAnswer);
                    score = mcResult.score();
                    correct = mcResult.allCorrect();
                    feedback = mcResult.feedback();
                } else {
                    // Legacy single-question MC
                    String correctAnswer = extractCorrectAnswer(exercise);
                    correct = userAnswer.trim().equalsIgnoreCase(correctAnswer.trim());
                    score = correct ? 1.0 : 0.0;
                    feedback = correct ? "Correct!" : "Incorrect. Review the concept and try again.";
                }
            }
            case GAP_FILL -> {
                Map<String, Object> answerJson = exercise.getCorrectAnswerJson();

                // Check if this is a multi-gap exercise
                if (answerJson.containsKey("gaps")) {
                    // New-style multi-gap GAP_FILL
                    GapGradingResult gapResult = gradeMultiGapFill(exercise, userAnswer);
                    score = gapResult.score();
                    correct = gapResult.allCorrect();
                    feedback = gapResult.feedback();
                } else {
                    // Legacy single-answer GAP_FILL
                    String correctAnswer = extractCorrectAnswer(exercise);
                    String normalized = normalize(userAnswer);
                    String correctNormalized = normalize(correctAnswer);
                    correct = normalized.equals(correctNormalized);
                    score = correct ? 1.0 : (similarity(normalized, correctNormalized) > 0.7 ? 0.5 : 0.0);
                    feedback = correct ? "Correct!" : "Close, but check your spelling/formatting.";
                }
            }
            case CODE_READ -> {
                // CODE_READ now uses LLM grading for nuanced understanding assessment
                // This case should not be reached as CODE_READ should have gradingMode = LLM
                throw new UnsupportedOperationException("CODE_READ should use LLM grading mode, not deterministic");
            }
            default -> throw new UnsupportedOperationException("Exercise type " + exercise.getExerciseType() + " not supported in deterministic mode");
        }

        // Optional LLM reasoning
        String reasoning = null;
        if (explainReasoning) {
            reasoning = requestLlmExplanation(exercise, userAnswer, correct);
        }

        // Convert to raw score for historical/audit purposes (EWMA now uses direct score)
        short scoreRaw = GradingResponse.convertToRaw(score);

        log.debug("Deterministic grading complete: score={}, scoreRaw={}/5, correct={}",
                score, scoreRaw, correct);

        return new GradingResponse(score, feedback, correct, true, reasoning, scoreRaw);
    }

    private GradingResponse gradeLlm(Exercise exercise, Learner learner, String userAnswer) {
        LlmGradingService.GradingResult result = llmGrading.gradeOpenEnded(exercise, userAnswer, learner);
        short scoreRaw = GradingResponse.convertToRaw(result.score());
        return new GradingResponse(
                result.score(),
                result.feedback(),
                result.correct(),
                false, // LLM grading is not deterministic
                null,
                scoreRaw
        );
    }

    private GradingResponse gradeHybrid(Exercise exercise, Learner learner, String userAnswer) {
        // Deterministic first, then LLM provides additional feedback
        GradingResponse deterministic = gradeDeterministic(exercise, userAnswer, false);
        LlmGradingService.GradingResult llmResult = llmGrading.gradeOpenEnded(exercise, userAnswer, learner);

        // Use deterministic score, but enrich with LLM feedback
        return new GradingResponse(
                deterministic.score(),
                deterministic.feedback() + "\n\n" + llmResult.feedback(),
                deterministic.correct(),
                true, // Primary grading is deterministic
                "LLM advisory score: " + llmResult.score(),
                deterministic.scoreRaw() // Use scoreRaw from deterministic grading
        );
    }

    private String extractCorrectAnswer(Exercise exercise) {
        Map<String, Object> answerJson = exercise.getCorrectAnswerJson();
        Object answer = answerJson.get("answer");
        return answer != null ? answer.toString() : "";
    }

    private String normalize(String text) {
        return text.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private double similarity(String s1, String s2) {
        // Simple Levenshtein-based similarity
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        int distance = levenshtein(s1, s2);
        return 1.0 - (double) distance / maxLen;
    }

    private int levenshtein(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }

    /**
     * Grade 2-question Multiple Choice exercise
     * @param exercise Exercise with questions array in questionJson
     * @param userAnswer JSON string like {"answers": [{"questionId":1, "selectedAnswer":"A"}, ...]}
     * @return Grading result with 0.0, 0.5, or 1.0 score
     */
    @SuppressWarnings("unchecked")
    private McGradingResult gradeMultipleChoiceTwoQuestions(Exercise exercise, String userAnswer) {
        try {
            Map<String, Object> questionJson = exercise.getQuestionJson();
            List<Map<String, Object>> questions = (List<Map<String, Object>>) questionJson.get("questions");

            if (questions == null || questions.size() != 2) {
                log.warn("Expected 2 questions in MC exercise {}, found {}", exercise.getId(), questions != null ? questions.size() : 0);
                return new McGradingResult(0.0, false, "Invalid exercise configuration");
            }

            // Parse user answers (expected format: {"answers": [{"questionId":1, "selectedAnswer":"A"}, ...]})
            Map<String, Object> answerJson;
            try {
                answerJson = objectMapper.readValue(userAnswer, Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse MC answer JSON: {}", userAnswer);
                return new McGradingResult(0.0, false, "Invalid answer format");
            }

            List<Map<String, Object>> userAnswers = (List<Map<String, Object>>) answerJson.get("answers");
            if (userAnswers == null || userAnswers.size() != 2) {
                return new McGradingResult(0.0, false, "Expected 2 answers");
            }

            // Grade each question
            int correctCount = 0;
            List<String> feedbackLines = new ArrayList<>();

            for (int i = 0; i < questions.size(); i++) {
                Map<String, Object> question = questions.get(i);
                int questionId = ((Number) question.get("id")).intValue();
                String correctAnswer = question.get("correctAnswer").toString();

                // Find user's answer for this question
                String userSelectedAnswer = userAnswers.stream()
                    .filter(ans -> ((Number) ans.get("questionId")).intValue() == questionId)
                    .map(ans -> ans.get("selectedAnswer").toString())
                    .findFirst()
                    .orElse("");

                boolean isCorrect = normalize(userSelectedAnswer).equals(normalize(correctAnswer));
                if (isCorrect) {
                    correctCount++;
                    feedbackLines.add(String.format("Question %d: ✓ Correct", i + 1));
                } else {
                    feedbackLines.add(String.format("Question %d: ✗ Incorrect (correct answer: %s)", i + 1, correctAnswer));
                }
            }

            // Calculate score: 0.0 (0/2), 0.5 (1/2), or 1.0 (2/2)
            double score = correctCount / 2.0;
            boolean allCorrect = correctCount == 2;

            String feedback = String.format("You got %d/2 questions correct.\n%s",
                correctCount, String.join("\n", feedbackLines));

            return new McGradingResult(score, allCorrect, feedback);

        } catch (Exception e) {
            log.error("Error grading 2-question MC: {}", e.getMessage());
            return new McGradingResult(0.0, false, "Error grading answer: " + e.getMessage());
        }
    }

    /**
     * Grade multi-gap GAP_FILL exercise
     * @param exercise Exercise with gaps array in correctAnswerJson
     * @param userAnswer JSON array string like ["answer1", "answer2"]
     * @return Grading result with per-gap scoring
     */
    @SuppressWarnings("unchecked")
    private GapGradingResult gradeMultiGapFill(Exercise exercise, String userAnswer) {
        try {
            Map<String, Object> answerJson = exercise.getCorrectAnswerJson();
            List<Map<String, Object>> gaps = (List<Map<String, Object>>) answerJson.get("gaps");

            if (gaps == null || gaps.isEmpty()) {
                log.warn("No gaps found in GAP_FILL exercise {}", exercise.getId());
                return new GapGradingResult(0.0, false, "Invalid exercise configuration");
            }

            // Parse user answers (expected format: ["answer1", "answer2", ...])
            List<String> userAnswers;
            try {
                userAnswers = objectMapper.readValue(userAnswer, List.class);
            } catch (Exception e) {
                log.warn("Failed to parse user answer as JSON array: {}", userAnswer);
                // Fallback: try single answer
                userAnswers = List.of(userAnswer);
            }

            if (userAnswers.size() != gaps.size()) {
                return new GapGradingResult(
                    0.0,
                    false,
                    String.format("Expected %d answers, got %d", gaps.size(), userAnswers.size())
                );
            }

            // Grade each gap
            int correctCount = 0;
            List<String> gapFeedback = new ArrayList<>();

            log.debug("GAP_FILL grading started: {} gaps to evaluate", gaps.size());

            for (int i = 0; i < gaps.size(); i++) {
                Map<String, Object> gap = gaps.get(i);
                String userGapAnswer = userAnswers.get(i);
                String normalizedUser = normalize(userGapAnswer);

                log.debug("  Gap {}: user answer='{}', normalized='{}'", i + 1, userGapAnswer, normalizedUser);

                // Check if gap has multiple valid answers (new format) or single answer (legacy)
                List<String> validAnswers;
                if (gap.containsKey("validAnswers")) {
                    // New format: array of valid answers
                    validAnswers = (List<String>) gap.get("validAnswers");
                } else {
                    // Legacy format: single correctAnswer
                    validAnswers = List.of(gap.get("correctAnswer").toString());
                }

                // Check if user answer matches ANY valid answer
                boolean isCorrect = validAnswers.stream()
                    .anyMatch(validAns -> normalize(validAns).equals(normalizedUser));

                log.debug("    Valid answers: {}, isCorrect={}", validAnswers, isCorrect);

                if (isCorrect) {
                    correctCount++;
                    gapFeedback.add(String.format("Gap %d: ✓", i + 1));
                } else {
                    // Check for close match with any valid answer
                    double maxSimilarity = validAnswers.stream()
                        .mapToDouble(validAns -> similarity(normalizedUser, normalize(validAns)))
                        .max()
                        .orElse(0.0);

                    if (maxSimilarity > 0.7) {
                        gapFeedback.add(String.format("Gap %d: Close (try: %s)",
                            i + 1, String.join(", ", validAnswers.subList(0, Math.min(3, validAnswers.size())))));
                    } else {
                        gapFeedback.add(String.format("Gap %d: ✗ (valid answers: %s)",
                            i + 1, String.join(", ", validAnswers.subList(0, Math.min(3, validAnswers.size())))));
                    }
                }
            }

            // Calculate score (partial credit for some correct)
            double score = (double) correctCount / gaps.size();
            boolean allCorrect = correctCount == gaps.size();

            String feedback = allCorrect
                ? "Perfect! All gaps filled correctly."
                : String.format("Score: %d/%d correct.\n%s",
                    correctCount, gaps.size(), String.join("\n", gapFeedback));

            log.info("GAP_FILL grading: {}/{} gaps correct, score={}, allCorrect={}",
                    correctCount, gaps.size(), score, allCorrect);

            return new GapGradingResult(score, allCorrect, feedback);

        } catch (Exception e) {
            log.error("Error grading multi-gap GAP_FILL: {}", e.getMessage());
            return new GapGradingResult(0.0, false, "Error grading answer: " + e.getMessage());
        }
    }

    private String requestLlmExplanation(Exercise exercise, String userAnswer, boolean correct) {
        String question = exercise.getQuestionJson().getOrDefault("question", "").toString();
        String correctAnswer = extractCorrectAnswer(exercise);

        String prompt = String.format("""
                Explain briefly why this answer is %s for this question:

                QUESTION: %s
                CORRECT ANSWER: %s
                USER ANSWER: %s

                Provide a 1-2 sentence explanation suitable for a student.
                """, correct ? "correct" : "incorrect", question, correctAnswer, userAnswer);

        try {
            return llmClient.generate(prompt);
        } catch (Exception e) {
            log.warn("Failed to generate LLM reasoning: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Grading response with score, feedback, and metadata
     */
    public record GradingResponse(
            double score,          // Normalized score 0.0-1.0 (used directly for EWMA mastery tracking)
            String feedback,
            boolean correct,
            boolean deterministic,
            String reasoning,      // Optional LLM explanation
            Short scoreRaw         // Raw score 1-5 (kept for historical/audit purposes, not used in EWMA)
    ) {
        /**
         * Convert normalized score (0-1) to raw score (1-5) for historical/audit purposes.
         * Note: EWMA mastery tracking now uses the direct normalized score (0.0-1.0) for transparency.
         * This conversion is maintained for backwards compatibility and audit logging.
         *
         * Mapping (for reference only):
         * - 0.9-1.0 (Excellent) → 5
         * - 0.7-0.89 (Very Good) → 4
         * - 0.5-0.69 (Good) → 3
         * - 0.3-0.49 (Fair) → 2
         * - 0.0-0.29 (Needs Work) → 1
         */
        public static short convertToRaw(double normalizedScore) {
            if (normalizedScore >= 0.9) return 5;  // Excellent
            if (normalizedScore >= 0.7) return 4;  // Very Good
            if (normalizedScore >= 0.5) return 3;  // Good
            if (normalizedScore >= 0.3) return 2;  // Fair
            return 1;                               // Needs Work
        }
    }

    /**
     * Internal result for multi-gap GAP_FILL grading
     */
    private record GapGradingResult(
            double score,
            boolean allCorrect,
            String feedback
    ) {}

    /**
     * Internal result for 2-question MC grading
     */
    private record McGradingResult(
            double score,
            boolean allCorrect,
            String feedback
    ) {}
}
