package org.example.protushybrid.repository.exercise;

import org.example.protushybrid.domain.exercise.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findByConceptId(Long conceptId);

    List<Exercise> findByExerciseType(Exercise.ExerciseType type);
}
