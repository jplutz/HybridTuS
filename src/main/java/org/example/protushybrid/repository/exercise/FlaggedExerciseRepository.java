package org.example.protushybrid.repository.exercise;

import org.example.protushybrid.domain.exercise.FlaggedExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlaggedExerciseRepository extends JpaRepository<FlaggedExercise, Long> {

    /**
     * Find all flagged exercises ordered by flagged date (most recent first)
     */
    List<FlaggedExercise> findAllByOrderByFlaggedAtDesc();

    /**
     * Find flagged exercises by review status
     */
    List<FlaggedExercise> findByReviewedOrderByFlaggedAtDesc(Boolean reviewed);

    /**
     * Check if a specific exercise has been flagged by a specific learner
     */
    boolean existsByExerciseIdAndLearnerId(Long exerciseId, Long learnerId);
}
