package org.example.protushybrid.repository.tracking;

import org.example.protushybrid.domain.tracking.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    List<Interaction> findByLearnerIdOrderByTimestampAsc(Long learnerId);

    /**
     * Find interactions with eagerly fetched LearningObject and Concept for mining.
     * Prevents LazyInitializationException when mining runs in separate transaction.
     */
    @Query("SELECT i FROM Interaction i " +
           "JOIN FETCH i.learningObject lo " +
           "JOIN FETCH lo.concept " +
           "WHERE i.learner.id = :learnerId " +
           "ORDER BY i.timestamp ASC")
    List<Interaction> findByLearnerIdWithLOAndConceptOrderByTimestampAsc(@Param("learnerId") Long learnerId);

    /**
     * Find the last 10 interactions for a learner, ordered by timestamp descending
     */
    @Query("SELECT i FROM Interaction i WHERE i.learner.id = :learnerId ORDER BY i.timestamp DESC LIMIT 10")
    List<Interaction> findLast10ResultsByLearnerId(@Param("learnerId") Long learnerId);

    /**
     * Count interactions for a learner since a given timestamp
     * Used for bot detection (>60 completions/hour)
     */
    @Query("SELECT COUNT(i) FROM Interaction i WHERE i.learner.id = :learnerId AND i.timestamp >= :since")
    long countByLearnerIdAndTimestampAfter(@Param("learnerId") Long learnerId, @Param("since") Instant since);

    /**
     * Find recent interactions for rate limiting identical repeats
     */
    @Query("SELECT i FROM Interaction i WHERE i.learner.id = :learnerId AND i.learningObject.id = :loId " +
           "AND i.timestamp >= :since ORDER BY i.timestamp DESC")
    List<Interaction> findRecentByLearnerAndLO(@Param("learnerId") Long learnerId,
                                                @Param("loId") Long loId,
                                                @Param("since") Instant since);
}
