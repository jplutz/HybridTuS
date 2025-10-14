package org.example.protushybrid.repository.mining;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.mining.FrequentSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FrequentSequenceRepository extends JpaRepository<FrequentSequence, Long> {

    /**
     * Find all sequences for a cluster×concept partition, ordered by support descending.
     */
    List<FrequentSequence> findByClusterKeyAndConceptOrderBySupportDesc(String clusterKey, Concept concept);

    /**
     * Find all sequences for a cluster (across all concepts).
     */
    List<FrequentSequence> findByClusterKeyOrderBySupportDesc(String clusterKey);

    /**
     * Find all sequences for a concept (across all clusters).
     */
    List<FrequentSequence> findByConceptOrderBySupportDesc(Concept concept);

    /**
     * Delete all sequences for a cluster×concept partition.
     */
    void deleteByClusterKeyAndConcept(String clusterKey, Concept concept);
}
