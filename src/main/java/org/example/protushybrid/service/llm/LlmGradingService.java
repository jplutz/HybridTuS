package org.example.protushybrid.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.protushybrid.domain.exercise.Exercise;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.llm.LlmGradingAudit;
import org.example.protushybrid.repository.llm.LlmGradingAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * LLM-based grading for open-ended questions (FREE_TEXT, CODE_WRITE).
 * Provides scoring and formative feedback with full audit logging.
 */
@Service
public class LlmGradingService {

    private static final Logger log = LoggerFactory.getLogger(LlmGradingService.class);

    private final LlmClient llmClient;
    private final LlmGradingAuditRepository auditRepo;
    private final ObjectMapper objectMapper;

    public LlmGradingService(LlmClient llmClient,
                             LlmGradingAuditRepository auditRepo,
                             ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.auditRepo = auditRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Grade open-ended answer using LLM
     * @return GradingResult with score (0.0-1.0), feedback text, and correctness
     */
    public GradingResult gradeOpenEnded(Exercise exercise, String userAnswer, Learner learner) {
        long startTime = System.currentTimeMillis();

        try {
            String question = extractQuestion(exercise);
            String correctAnswer = extractCorrectAnswer(exercise);

            // Log when grading code exercises
            if (exercise.getExerciseType() == Exercise.ExerciseType.CODE_WRITE ||
                exercise.getExerciseType() == Exercise.ExerciseType.CODE_READ) {
                log.info("LLM grading {} exercise {} for learner {} (code/answer length: {} chars)",
                        exercise.getExerciseType(), exercise.getId(), learner.getId(),
                        userAnswer != null ? userAnswer.length() : 0);
            }

            String prompt = buildGradingPrompt(question, correctAnswer, userAnswer, exercise.getExerciseType());

            String llmResponse = llmClient.generate(prompt);
            GradingResult result = parseGradingResponse(llmResponse);

            // Audit logging
            saveAuditLog(exercise, learner, userAnswer, result, System.currentTimeMillis() - startTime);

            // Log result for code exercises
            if (exercise.getExerciseType() == Exercise.ExerciseType.CODE_WRITE ||
                exercise.getExerciseType() == Exercise.ExerciseType.CODE_READ) {
                log.info("LLM grading complete for {} exercise {}: score={}, correct={}",
                        exercise.getExerciseType(), exercise.getId(), result.score(), result.correct());
            }

            return result;

        } catch (Exception e) {
            log.error("LLM grading failed for exercise {}: {}", exercise.getId(), e.getMessage());
            // Fallback to neutral score
            return new GradingResult(0.5, "Unable to grade at this time. Please try again.", false, false);
        }
    }

    private String buildGradingPrompt(String question, String correctAnswer, String userAnswer, Exercise.ExerciseType type) {
        String typeContext = switch (type) {
            case CODE_WRITE -> """
                This is a CODE-WRITING exercise. The student has written code that you must evaluate.
                Focus on:
                - Correctness: Does the code produce the expected output?
                - Logic: Is the approach sound?
                - Syntax: Are there any syntax errors?
                - Best practices: Does it follow good coding standards?
                Review the student's CODE carefully and grade it comprehensively.""";
            case CODE_READ -> """
                This is a CODE COMPREHENSION question. The student has analyzed code and provided their understanding.
                Focus on:
                - Understanding: Does the student correctly identify what the code does?
                - Output prediction: Can they predict the code's behavior?
                - Logic comprehension: Do they understand the control flow?
                Review the student's ANSWER about the code carefully.""";
            case FREE_TEXT -> "This is a free-text answer. Evaluate understanding and completeness.";
            default -> "Evaluate the answer.";
        };

        return String.format("""
                You are grading a student's answer to a programming question. Be encouraging and pedagogical.

                %s

                QUESTION:
                %s

                EXPECTED ANSWER:
                %s

                STUDENT ANSWER:
                %s

                Provide your assessment in EXACTLY this format:
                SCORE: [0.0 to 1.0]
                CORRECT: [true/false]
                EXPLANATION: [2-3 sentences explaining the score, what the student understood, and what they missed]

                IMPORTANT: EXPLANATION is MANDATORY. Be specific, encouraging, and pedagogical.

                Grading guidelines (BE FORGIVING - reward partial understanding):
                - 1.0 (Excellent): Answer demonstrates full understanding. Minor typos or formatting issues are acceptable.
                - 0.8-0.9 (Very Good): Answer is mostly correct with minor conceptual gaps or missing details.
                - 0.6-0.7 (Good): Answer shows solid understanding but has some notable errors or omissions.
                - 0.4-0.5 (Fair): Answer demonstrates partial understanding but misses key concepts.
                - 0.2-0.3 (Needs Work): Answer has correct elements but significant misunderstandings.
                - 0.0-0.1 (Incorrect): Answer fundamentally misses the point or is completely wrong.

                CODE_READ specific:
                - If student identifies correct output/behavior = at least 0.7
                - Perfect explanation not required for high score

                CODE_WRITE/FREE_TEXT specific:
                - Working code with minor style issues = 0.8-1.0
                - Correct approach with syntax errors = 0.6-0.8
                - Partial solution showing understanding = 0.4-0.6

                Be generous with scores if the student demonstrates understanding, even if execution is imperfect.
                """, typeContext, question, correctAnswer, userAnswer);
    }

    private GradingResult parseGradingResponse(String response) {
        try {
            double score = extractValue(response, "SCORE:", 0.5);
            boolean correct = extractBoolean(response, "CORRECT:", false);

            // Try new format first (EXPLANATION), fall back to legacy format (FEEDBACK)
            String explanation = extractText(response, "EXPLANATION:", null);
            if (explanation == null || explanation.isEmpty()) {
                explanation = extractText(response, "FEEDBACK:", "No explanation provided.");
            }

            return new GradingResult(
                    Math.max(0.0, Math.min(1.0, score)), // Clamp to [0,1]
                    explanation.trim(),
                    correct,
                    false // LLM grading is not deterministic
            );
        } catch (Exception e) {
            log.warn("Failed to parse LLM grading response: {}", e.getMessage());
            return new GradingResult(0.5, response.length() > 200 ? response.substring(0, 200) : response, false, false);
        }
    }

    private double extractValue(String text, String marker, double defaultValue) {
        int start = text.indexOf(marker);
        if (start == -1) return defaultValue;

        start += marker.length();
        int end = text.indexOf('\n', start);
        if (end == -1) end = text.length();

        try {
            return Double.parseDouble(text.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean extractBoolean(String text, String marker, boolean defaultValue) {
        int start = text.indexOf(marker);
        if (start == -1) return defaultValue;

        start += marker.length();
        int end = text.indexOf('\n', start);
        if (end == -1) end = text.length();

        String value = text.substring(start, end).trim().toLowerCase();
        return "true".equals(value) || "yes".equals(value);
    }

    private String extractText(String text, String marker, String defaultValue) {
        int start = text.indexOf(marker);
        if (start == -1) return defaultValue;

        start += marker.length();
        return text.substring(start).trim();
    }

    private String extractQuestion(Exercise exercise) {
        return exercise.getQuestionJson().getOrDefault("question", "").toString();
    }

    private String extractCorrectAnswer(Exercise exercise) {
        Object answer = exercise.getCorrectAnswerJson().get("answer");
        return answer != null ? answer.toString() : "";
    }

    private void saveAuditLog(Exercise exercise, Learner learner, String userAnswer, GradingResult result, long durationMs) {
        try {
            LlmGradingAudit audit = new LlmGradingAudit();
            audit.setExercise(exercise);
            audit.setLearner(learner);
            audit.setUserAnswerHash(hashAnswer(userAnswer));
            audit.setLlmScore(result.score());
            audit.setLlmFeedback(result.feedback());
            audit.setDurationMs((int) durationMs);
            auditRepo.save(audit);
        } catch (Exception e) {
            log.error("Failed to save LLM grading audit: {}", e.getMessage());
        }
    }

    private String hashAnswer(String answer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(answer.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 64);
        } catch (Exception e) {
            return "hash_error";
        }
    }

    /**
     * Grading result record
     * @param score 0.0 to 1.0
     * @param feedback Formative feedback text
     * @param correct Whether answer is correct
     * @param deterministic Whether grading is deterministic (always false for LLM)
     */
    public record GradingResult(double score, String feedback, boolean correct, boolean deterministic) {}
}
