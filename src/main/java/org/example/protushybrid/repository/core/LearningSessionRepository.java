package org.example.protushybrid.repository.core;

import org.example.protushybrid.domain.core.LearningSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface LearningSessionRepository extends JpaRepository<LearningSession, String> {

    /**
     * Find active session for learner in concept.
     * Active = created within last 24 hours and not completed.
     */
    @Query("""
        SELECT s FROM LearningSession s
        WHERE s.learner.id = :learnerId
          AND s.concept.id = :conceptId
          AND s.completed = false
          AND s.createdAt > :since
        ORDER BY s.createdAt DESC
        """)
    Optional<LearningSession> findActiveSession(Long learnerId, Long conceptId, Instant since);

    /**
     * Find all sessions for a learner.
     */
    List<LearningSession> findByLearnerIdOrderByCreatedAtDesc(Long learnerId);

    /**
     * Find all completed sessions for a concept (for mining).
     */
    List<LearningSession> findByConceptIdAndCompletedTrue(Long conceptId);
}
