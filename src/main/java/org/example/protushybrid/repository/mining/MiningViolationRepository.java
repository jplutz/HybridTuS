package org.example.protushybrid.repository.mining;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.mining.MiningViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MiningViolationRepository extends JpaRepository<MiningViolation, Long> {

    /**
     * Find all violations for a concept, ordered by detection time descending.
     */
    List<MiningViolation> findByConceptOrderByDetectedAtDesc(Concept concept);

    /**
     * Find unreviewed violations.
     */
    List<MiningViolation> findByReviewedFalseOrderByDetectedAtDesc();

    /**
     * Count unreviewed violations.
     */
    long countByReviewedFalse();
}
