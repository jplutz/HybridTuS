package org.example.protushybrid.repository.mining;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.mining.SequenceStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SequenceStatsRepository extends JpaRepository<SequenceStats, Long> {

    List<SequenceStats> findByClusterKeyAndConcept(String clusterKey, Concept concept);

    @Query("SELECT SUM(s.eventCount) FROM SequenceStats s WHERE s.clusterKey = ?1 AND s.concept = ?2")
    Long sumEventCountByClusterKeyAndConcept(String clusterKey, Concept concept);
}
