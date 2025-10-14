package org.example.protushybrid.domain.llm;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.exercise.Exercise;

import java.time.Instant;

/**
 * Audit log for LLM-graded exercise submissions.
 * Provides transparency and enables analysis of LLM grading behavior.
 */
@Entity
@Table(name = "llm_grading_audit", schema = "app")
@Getter
@Setter
public class LlmGradingAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    /**
     * Hash of user answer for deduplication and privacy
     */
    @Column(name = "user_answer_hash", nullable = false, length = 64)
    private String userAnswerHash;

    @Column(name = "llm_score")
    private Double llmScore;

    @Column(name = "llm_feedback", columnDefinition = "TEXT")
    private String llmFeedback;

    /**
     * LLM API call duration in milliseconds
     */
    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "ts", nullable = false)
    private Instant timestamp = Instant.now();
}
