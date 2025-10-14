package org.example.protushybrid.domain.exercise;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.protushybrid.domain.core.Learner;

import java.time.Instant;

/**
 * Tracks which exercises a learner has attempted to prevent duplicates
 */
@Entity
@Table(
    name = "learner_exercise_attempts",
    schema = "app",
    uniqueConstraints = @UniqueConstraint(columnNames = {"learner_id", "exercise_id"})
)
@Getter
@Setter
public class LearnerExerciseAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt = Instant.now();
}
