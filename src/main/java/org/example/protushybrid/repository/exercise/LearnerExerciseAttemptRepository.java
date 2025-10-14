package org.example.protushybrid.repository.exercise;

import org.example.protushybrid.domain.exercise.LearnerExerciseAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LearnerExerciseAttemptRepository extends JpaRepository<LearnerExerciseAttempt, Long> {

    /**
     * Get all exercise IDs attempted by a learner for a specific concept
     */
    @Query("""
        SELECT DISTINCT a.exercise.id
        FROM LearnerExerciseAttempt a
        WHERE a.learner.id = :learnerId
        AND a.exercise.concept.id = :conceptId
    """)
    List<Long> findAttemptedExerciseIds(@Param("learnerId") Long learnerId, @Param("conceptId") Long conceptId);

    /**
     * Check if a learner has attempted a specific exercise
     */
    boolean existsByLearnerIdAndExerciseId(Long learnerId, Long exerciseId);
}
