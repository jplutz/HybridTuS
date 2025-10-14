package org.example.protushybrid.domain.mining;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.protushybrid.domain.core.Concept;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Mined frequent sequence for a cluster×concept partition.
 * Stores patterns like ["theory", "example", "assessment"] with support metrics.
 */
@Entity
@Table(name = "frequent_sequences", schema = "app")
@Getter
@Setter
public class FrequentSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_key", nullable = false, length = 64)
    private String clusterKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id")
    private Concept concept;

    /**
     * Ordered sequence of LO types, e.g., ["theory", "example", "assessment"]
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "sequence_items",
            schema = "app",
            joinColumns = @JoinColumn(name = "sequence_id")
    )
    @OrderColumn(name = "item_order")
    @Column(name = "lo_type", length = 32)
    private List<String> seq = new ArrayList<>();

    /**
     * Support: fraction of learners in cluster who followed this sequence
     */
    @Column(name = "support", nullable = false)
    private Double support = 0.0;

    /**
     * Absolute count of learners who followed this sequence
     */
    @Column(name = "n_learners")
    private Integer nLearners = 0;

    /**
     * When this pattern was last mined
     */
    @Column(name = "last_mined_at")
    private Instant lastMinedAt = Instant.now();

    @Column(name = "mining_snapshot_id")
    private String miningSnapshotId;
}
