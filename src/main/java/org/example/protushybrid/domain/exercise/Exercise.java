package org.example.protushybrid.domain.exercise;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.protushybrid.domain.core.Concept;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Exercise definition with JSON-based content and grading modes.
 * Supports multiple exercise types with deterministic and LLM-based grading.
 *
 * Exercises are dynamically generated assessments linked directly to concepts,
 * separate from static "Test" learning objects in the learning sequence.
 */
@Entity
@Table(name = "exercises", schema = "app")
@Getter
@Setter
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id", nullable = false)
    private Concept concept;

    @Column(name = "exercise_type", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private ExerciseType exerciseType;

    /**
     * Question content as JSON (includes text, options, figures, etc.)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> questionJson;

    /**
     * Correct answer structure as JSON
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_answer_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> correctAnswerJson;

    @Column(name = "max_score", nullable = false)
    private Double maxScore = 1.0;

    @Column(name = "grading_mode", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private GradingMode gradingMode;

    @Column(name = "feedback_prompt", columnDefinition = "TEXT")
    private String feedbackPrompt;

    /**
     * Checkpoint flag: lightweight comprehension checks that don't count toward mastery
     */
    @Column(name = "is_checkpoint", nullable = false)
    private Boolean isCheckpoint = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public enum ExerciseType {
        MC,         // Multiple choice
        GAP_FILL,   // Fill in the blank
        FREE_TEXT,  // Open-ended text response
        CODE_WRITE, // Write code
        CODE_READ   // Code comprehension
    }

    public enum GradingMode {
        DETERMINISTIC,  // Exact match or rule-based
        LLM,            // AI-graded
        HYBRID          // Both deterministic + LLM feedback
    }
}
