package org.example.protushybrid.repository.mining;

import org.example.protushybrid.domain.mining.MinedSupport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MinedSupportRepository extends JpaRepository<MinedSupport, Long> {

    @Query("SELECT m FROM MinedSupport m WHERE m.clusterKey = ?1 AND m.conceptId = ?2 AND m.suffix = ?3 ORDER BY m.nSupport DESC")
    List<MinedSupport> findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
            String clusterKey,
            Long conceptId,
            String suffix
    );

    Optional<MinedSupport> findByClusterKeyAndConceptIdAndSuffixAndNextType(
            String clusterKey,
            Long conceptId,
            String suffix,
            String nextType
    );
}
