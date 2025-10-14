package org.example.protushybrid.service.exercise;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.LearningObject;
import org.example.protushybrid.domain.exercise.Exercise;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.core.LearningObjectRepository;
import org.example.protushybrid.repository.exercise.ExerciseRepository;
import org.example.protushybrid.service.llm.AnthropicLlmClient;
import org.example.protushybrid.service.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for generating exercises using LLM based on concept content.
 * Supports multiple exercise types with structured output validation.
 */
@Service
public class ExerciseGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGenerationService.class);

    private final LlmClient llmClient;
    private final ConceptRepository conceptRepo;
    private final LearningObjectRepository loRepo;
    private final ExerciseRepository exerciseRepo;
    private final ObjectMapper objectMapper;

    public ExerciseGenerationService(
            LlmClient llmClient,
            ConceptRepository conceptRepo,
            LearningObjectRepository loRepo,
            ExerciseRepository exerciseRepo,
            ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.conceptRepo = conceptRepo;
        this.loRepo = loRepo;
        this.exerciseRepo = exerciseRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate an exercise for a concept using LLM
     * @param conceptId Target concept
     * @param type Exercise type (MC, GAP_FILL, FREE_TEXT, CODE_READ, CODE_WRITE)
     * @param difficulty Difficulty level (easy, medium, hard)
     * @param previousQuestions List of previously asked questions to avoid duplication
     * @return Generated exercise
     */
    @Transactional
    public Exercise generateExercise(Long conceptId, String type, String difficulty, List<String> previousQuestions) {
        log.info("Generating {} exercise for concept {} with difficulty {} (avoiding {} previous questions)",
                type, conceptId, difficulty, previousQuestions.size());

        // 1. Fetch concept
        Concept concept = conceptRepo.findById(conceptId)
                .orElseThrow(() -> new RuntimeException("Concept not found: " + conceptId));

        // 2. Build context from concept's learning objects
        String conceptContext = buildConceptContext(concept);

        // 3. Generate exercise using LLM with structured output
        Map<String, Object> exerciseData = generateWithLLM(conceptContext, type, difficulty, concept.getName(), previousQuestions);

        // 4. Create and save exercise entity
        Exercise exercise = new Exercise();
        exercise.setConcept(concept);
        exercise.setExerciseType(Exercise.ExerciseType.valueOf(type));
        exercise.setQuestionJson((Map<String, Object>) exerciseData.get("question"));
        exercise.setCorrectAnswerJson((Map<String, Object>) exerciseData.get("correctAnswer"));
        exercise.setMaxScore(1.0);

        // Set grading mode based on type
        if (type.equals("MC") || type.equals("GAP_FILL")) {
            exercise.setGradingMode(Exercise.GradingMode.DETERMINISTIC);
        } else {
            exercise.setGradingMode(Exercise.GradingMode.LLM);
        }

        exercise.setIsCheckpoint(false);

        Exercise saved = exerciseRepo.save(exercise);
        log.info("Generated and saved exercise {} for concept {}", saved.getId(), conceptId);

        return saved;
    }

    /**
     * Build context string from concept's learning objects
     */
    private String buildConceptContext(Concept concept) {
        StringBuilder context = new StringBuilder();
        context.append("Concept: ").append(concept.getName()).append("\n\n");

        if (concept.getDescription() != null && !concept.getDescription().isEmpty()) {
            context.append("Description: ").append(concept.getDescription()).append("\n\n");
        }

        // Get all learning objects for this concept (just list types, not full content)
        List<LearningObject> learningObjects = loRepo.findAll().stream()
                .filter(lo -> lo.getConcept() != null &&
                             lo.getConcept().getId().equals(concept.getId()))
                .toList();

        if (!learningObjects.isEmpty()) {
            context.append("Available Learning Materials:\n");
            for (LearningObject lo : learningObjects) {
                context.append("- [").append(lo.getType()).append("] ");
                if (lo.getTitle() != null) {
                    context.append(lo.getTitle());
                } else {
                    context.append(concept.getName()).append(" ").append(lo.getType());
                }
                context.append("\n");
            }
        }

        return context.toString();
    }

    /**
     * Generate exercise using LLM with structured JSON output
     */
    private Map<String, Object> generateWithLLM(String context, String type, String difficulty, String conceptTitle, List<String> previousQuestions) {
        String systemPrompt = buildSystemPrompt(type, difficulty);
        String userPrompt = buildUserPrompt(context, conceptTitle, previousQuestions);
        Map<String, Object> jsonSchema = buildJSONSchema(type);

        try {
            // Use structured output via AnthropicLlmClient
            if (llmClient instanceof AnthropicLlmClient anthropicClient) {
                String jsonResponse = anthropicClient.generateStructured(systemPrompt, userPrompt, jsonSchema);
                return objectMapper.readValue(jsonResponse, Map.class);
            } else {
                // Fallback for other LLM providers
                String response = llmClient.generate(systemPrompt + "\n\n" + userPrompt);
                return objectMapper.readValue(response, Map.class);
            }
        } catch (Exception e) {
            log.error("Failed to generate exercise with LLM: {}", e.getMessage());
            throw new RuntimeException("Exercise generation failed", e);
        }
    }

    /**
     * Build system prompt based on exercise type
     */
    private String buildSystemPrompt(String type, String difficulty) {
        String basePrompt = "You are an expert educational content creator. Generate a " + difficulty +
                          " difficulty practice exercise that tests conceptual understanding.\n\n";

        return switch (type) {
            case "MC" -> basePrompt + """
                Create a multiple-choice exercise with 2 questions for partial credit scoring.
                Each question should have 4 options with exactly one correct answer.
                Questions should test deep understanding, not just memorization.
                Avoid ambiguous or trick questions.
                """;

            case "GAP_FILL" -> basePrompt + """
                Create a fill-in-the-blank exercise with 2-3 gaps.
                Each gap should be ONE or TWO words maximum - short and unambiguous.
                Each gap should test a key concept or term.
                Provide multiple valid answers for each gap to allow flexibility (e.g., synonyms, abbreviations).
                The template should read naturally when gaps are filled.
                Use {{1}}, {{2}}, {{3}} to mark gap positions in the template.
                """;

            case "FREE_TEXT" -> basePrompt + """
                Create an open-ended question that requires explanation.
                The question should encourage critical thinking and application.
                Should be answerable in 2-3 sentences.
                """;

            case "CODE_READ" -> basePrompt + """
                Create a code comprehension exercise with a code snippet and a question about it.
                Provide a short code snippet (5-15 lines) that demonstrates a concept.
                Ask a question that tests understanding of what the code does, its behavior, or its output.
                The question should require analyzing and understanding the code, not just memorization.
                Use realistic, working code examples in an appropriate language for the concept.
                """;

            case "CODE_WRITE" -> basePrompt + """
                Create a code-writing exercise that asks the learner to implement a small function or code snippet.
                Provide clear specifications of what the code should do.
                Include expected behavior, inputs, outputs, or test cases.
                The problem should be solvable in 5-20 lines of code.
                Focus on applying the concept, not complex algorithmic challenges.
                Provide a sample solution that demonstrates correct implementation.
                """;

            default -> throw new IllegalArgumentException("Unsupported exercise type: " + type);
        };
    }

    /**
     * Build user prompt with concept context and previous questions to avoid duplication
     */
    private String buildUserPrompt(String context, String conceptTitle, List<String> previousQuestions) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format("""
                Based on the following concept content, create a practice exercise:

                %s

                The exercise should:
                1. Test understanding of the key concepts from "%s"
                2. Be clear and unambiguous
                3. Have definitive correct answers
                4. Avoid requiring external knowledge not provided in the content
                """, context, conceptTitle));

        // Add previous questions context if available
        if (previousQuestions != null && !previousQuestions.isEmpty()) {
            prompt.append("\n\n**IMPORTANT: Avoid generating questions similar to these previously asked questions:**\n");
            for (int i = 0; i < previousQuestions.size(); i++) {
                prompt.append(String.format("%d. %s\n", i + 1, previousQuestions.get(i)));
            }
            prompt.append("\nMake sure your new question tests different aspects of the concept and uses different phrasing.\n");
        }

        prompt.append("\nGenerate the exercise now.");
        return prompt.toString();
    }

    /**
     * Build JSON schema for structured LLM output based on exercise type
     */
    private Map<String, Object> buildJSONSchema(String type) {
        return switch (type) {
            case "MC" -> buildMCSchema();
            case "GAP_FILL" -> buildGapFillSchema();
            case "FREE_TEXT" -> buildFreeTextSchema();
            case "CODE_READ" -> buildCodeReadSchema();
            case "CODE_WRITE" -> buildCodeWriteSchema();
            default -> throw new IllegalArgumentException("Unsupported exercise type: " + type);
        };
    }

    private Map<String, Object> buildMCSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "question", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "questions", Map.of(
                            "type", "array",
                            "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                    "id", Map.of("type", "integer"),
                                    "text", Map.of("type", "string"),
                                    "options", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "string")
                                    ),
                                    "correctAnswer", Map.of("type", "string")
                                ),
                                "required", List.of("id", "text", "options", "correctAnswer")
                            )
                        )
                    ),
                    "required", List.of("questions")
                ),
                "correctAnswer", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "answers", Map.of(
                            "type", "array",
                            "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                    "questionId", Map.of("type", "integer"),
                                    "correctAnswer", Map.of("type", "string")
                                ),
                                "required", List.of("questionId", "correctAnswer")
                            )
                        )
                    ),
                    "required", List.of("answers")
                )
            ),
            "required", List.of("question", "correctAnswer")
        );
    }

    private Map<String, Object> buildGapFillSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "question", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "template", Map.of("type", "string"),
                        "gaps", Map.of(
                            "type", "array",
                            "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                    "id", Map.of("type", "integer"),
                                    "hint", Map.of("type", "string")
                                ),
                                "required", List.of("id")
                            )
                        )
                    ),
                    "required", List.of("template", "gaps")
                ),
                "correctAnswer", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "gaps", Map.of(
                            "type", "array",
                            "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                    "id", Map.of("type", "integer"),
                                    "validAnswers", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "string")
                                    )
                                ),
                                "required", List.of("id", "validAnswers")
                            )
                        )
                    ),
                    "required", List.of("gaps")
                )
            ),
            "required", List.of("question", "correctAnswer")
        );
    }

    private Map<String, Object> buildFreeTextSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "question", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "question", Map.of("type", "string")
                    ),
                    "required", List.of("question")
                ),
                "correctAnswer", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "sampleAnswer", Map.of("type", "string"),
                        "keyPoints", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string")
                        )
                    ),
                    "required", List.of("sampleAnswer", "keyPoints")
                )
            ),
            "required", List.of("question", "correctAnswer")
        );
    }

    private Map<String, Object> buildCodeReadSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "question", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "codeSnippet", Map.of("type", "string"),
                        "language", Map.of("type", "string"),
                        "question", Map.of("type", "string")
                    ),
                    "required", List.of("codeSnippet", "language", "question")
                ),
                "correctAnswer", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "answer", Map.of("type", "string"),
                        "explanation", Map.of("type", "string")
                    ),
                    "required", List.of("answer", "explanation")
                )
            ),
            "required", List.of("question", "correctAnswer")
        );
    }

    private Map<String, Object> buildCodeWriteSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "question", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "description", Map.of("type", "string"),
                        "language", Map.of("type", "string"),
                        "starterCode", Map.of("type", "string"),
                        "testCases", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string")
                        )
                    ),
                    "required", List.of("description", "language")
                ),
                "correctAnswer", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "sampleSolution", Map.of("type", "string"),
                        "explanation", Map.of("type", "string")
                    ),
                    "required", List.of("sampleSolution", "explanation")
                )
            ),
            "required", List.of("question", "correctAnswer")
        );
    }
}
