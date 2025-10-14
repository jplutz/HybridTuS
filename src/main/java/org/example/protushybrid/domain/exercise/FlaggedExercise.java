package org.example.protushybrid.domain.exercise;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.protushybrid.domain.core.Learner;

import java.time.Instant;

/**
 * Represents an exercise that has been flagged by a learner for review.
 * Stores the exercise, learner, their answer, grading results, and reason for flagging.
 */
@Entity
@Table(name = "flagged_exercises", schema = "app")
@Getter
@Setter
public class FlaggedExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @Column(name = "user_answer", nullable = false, columnDefinition = "TEXT")
    private String userAnswer;

    @Column(name = "grading_score")
    private Double gradingScore;

    @Column(name = "grading_feedback", columnDefinition = "TEXT")
    private String gradingFeedback;

    @Column(name = "flag_reason", columnDefinition = "TEXT")
    private String flagReason;

    @Column(name = "flagged_at", nullable = false)
    private Instant flaggedAt = Instant.now();

    @Column(name = "reviewed", nullable = false)
    private Boolean reviewed = false;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewer_notes", columnDefinition = "TEXT")
    private String reviewerNotes;

    /**
     * Stores code content separately for CODE_WRITE and CODE_READ exercises
     * to enable better logging, display, and LLM grading
     */
    @Column(name = "code_content", columnDefinition = "TEXT")
    private String codeContent;
}
