package org.example.protushybrid.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.Course;
import org.example.protushybrid.domain.core.LearningObject;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.core.CourseRepository;
import org.example.protushybrid.repository.core.LearningObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Chat service that provides context-aware LLM interactions.
 * Supports both general learning chat and practice-mode chat with exercise context.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final LlmClient llmClient;
    private final ConceptRepository conceptRepo;
    private final CourseRepository courseRepo;
    private final LearningObjectRepository loRepo;
    private final ObjectMapper objectMapper;

    public ChatService(LlmClient llmClient,
                       ConceptRepository conceptRepo,
                       CourseRepository courseRepo,
                       LearningObjectRepository loRepo,
                       ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.conceptRepo = conceptRepo;
        this.courseRepo = courseRepo;
        this.loRepo = loRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Handle chat message with full context awareness
     */
    public ChatResponse handleChat(ChatRequest request) {
        try {
            String prompt;

            if (request.exerciseContext() != null) {
                // Practice mode: include exercise context
                prompt = buildPracticeChatPrompt(request);
            } else {
                // General learning: include course context only
                prompt = buildLearningChatPrompt(request);
            }

            String llmResponse = llmClient.generate(prompt);
            return new ChatResponse(llmResponse);

        } catch (Exception e) {
            log.error("Chat handling failed: {}", e.getMessage());
            return new ChatResponse("I'm having trouble responding right now. Please try again.");
        }
    }

    /**
     * Build prompt for practice mode with exercise context
     */
    private String buildPracticeChatPrompt(ChatRequest request) {
        String courseContext = buildCourseContext(request.conceptId());
        ExerciseContext ex = request.exerciseContext();

        return String.format("""
                You are a teaching assistant helping a student with practice exercises.

                COURSE CONTEXT:
                %s

                CURRENT EXERCISE:
                Type: %s
                Question: %s
                Student's Answer: %s
                Grading Result: Score %.2f/1.0 - %s

                STUDENT QUESTION:
                %s

                IMPORTANT GUIDELINES:
                - Do NOT reveal the full correct answer directly
                - Guide the student to understand the concept
                - Reference the course material when helpful
                - Encourage them to think through the problem
                - Be encouraging but pedagogical
                - Keep your response concise (2-3 paragraphs max)
                """,
                courseContext,
                ex.type(),
                formatQuestion(ex.question()),
                ex.userAnswer() != null ? ex.userAnswer() : "(not yet answered)",
                ex.gradingScore() != null ? ex.gradingScore() : 0.0,
                ex.gradingFeedback() != null ? ex.gradingFeedback() : "Not yet graded",
                request.userText()
        );
    }

    /**
     * Build prompt for general learning chat
     */
    private String buildLearningChatPrompt(ChatRequest request) {
        String courseContext = buildCourseContext(request.conceptId());
        String phase = request.contextPhase() != null ? request.contextPhase() : "GENERAL";

        return String.format("""
                You are a teaching assistant helping a student learn programming.

                COURSE CONTEXT:
                %s

                CURRENT PHASE: %s

                STUDENT QUESTION:
                %s

                GUIDELINES:
                - Provide clear, educational explanations
                - Reference the course material
                - Use examples when helpful
                - Be encouraging and supportive
                - Keep responses concise (2-4 paragraphs)
                """,
                courseContext,
                phase,
                request.userText()
        );
    }

    /**
     * Build comprehensive course context for the concept
     */
    private String buildCourseContext(Long conceptId) {
        try {
            Concept concept = conceptRepo.findById(conceptId)
                    .orElseThrow(() -> new RuntimeException("Concept not found: " + conceptId));

            Course course = courseRepo.findById(concept.getCourse().getId())
                    .orElseThrow(() -> new RuntimeException("Course not found"));

            // Get all concepts in the course for context
            List<Concept> allConcepts = conceptRepo.findByCourseIdOrderByOrderIndex(course.getId());

            // Get learning objects for this concept
            List<LearningObject> learningObjects = loRepo.findByConceptId(conceptId);

            StringBuilder context = new StringBuilder();
            context.append("COURSE: ").append(course.getTitle()).append("\n");
            if (course.getDescription() != null) {
                context.append("Course Description: ").append(course.getDescription()).append("\n");
            }
            context.append("\nCONCEPTS IN THIS COURSE:\n");
            for (Concept c : allConcepts) {
                String marker = c.getId().equals(conceptId) ? " ← CURRENT" : "";
                context.append("- ").append(c.getName());
                if (c.getDescription() != null) {
                    context.append(": ").append(c.getDescription());
                }
                context.append(marker).append("\n");
            }

            context.append("\nCURRENT CONCEPT CONTENT:\n");
            context.append("Title: ").append(concept.getName()).append("\n");
            if (concept.getDescription() != null) {
                context.append("Description: ").append(concept.getDescription()).append("\n");
            }

            // Include learning object content summaries
            context.append("\nAvailable Learning Materials:\n");
            for (LearningObject lo : learningObjects) {
                context.append("- ").append(lo.getType()).append(" module");
                if (lo.getEstTimeMin() != null) {
                    context.append(" (").append(lo.getEstTimeMin()).append(" min)");
                }
                context.append("\n");
            }

            return context.toString();

        } catch (Exception e) {
            log.warn("Failed to build course context: {}", e.getMessage());
            return "Current topic: Programming concepts";
        }
    }

    /**
     * Format exercise question for display
     */
    private String formatQuestion(String questionJson) {
        try {
            // Try to parse and extract question text
            Map<String, Object> parsed = objectMapper.readValue(questionJson, Map.class);

            // Handle different question formats
            if (parsed.containsKey("questions")) {
                // 2-question MC format
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> questions = (List<Map<String, Object>>) parsed.get("questions");
                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < questions.size(); i++) {
                    formatted.append("Q").append(i + 1).append(": ")
                            .append(questions.get(i).get("text")).append("\n");
                }
                return formatted.toString();
            } else if (parsed.containsKey("template")) {
                // Gap-fill format
                return parsed.get("template").toString();
            } else if (parsed.containsKey("question")) {
                // Simple question format
                return parsed.get("question").toString();
            }

            // Fallback: return as-is
            return questionJson;
        } catch (Exception e) {
            // If parsing fails, return raw JSON
            return questionJson;
        }
    }

    /**
     * Chat request with optional exercise context
     */
    public record ChatRequest(
            Long conceptId,
            String contextPhase,
            String userText,
            ExerciseContext exerciseContext
    ) {}

    /**
     * Exercise context for practice-mode chat
     */
    public record ExerciseContext(
            Long exerciseId,
            String type,
            String question,
            String userAnswer,
            Double gradingScore,
            String gradingFeedback
    ) {}

    /**
     * Chat response
     */
    public record ChatResponse(String text) {}
}
