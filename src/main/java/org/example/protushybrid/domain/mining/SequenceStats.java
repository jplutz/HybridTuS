package org.example.protushybrid.domain.mining;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.protushybrid.domain.core.Concept;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight incremental counters for n-grams.
 * Triggers full mining when partition reaches thresholds.
 */
@Entity
@Table(name = "sequence_stats", schema = "app")
@Getter
@Setter
public class SequenceStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_key", nullable = false, length = 32)
    private String clusterKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id")
    private Concept concept;

    /**
     * The n-gram sequence
     */
    @ElementCollection
    @CollectionTable(name = "sequence_stats_ngram", schema = "app", joinColumns = @JoinColumn(name = "stats_id"))
    @Column(name = "item")
    @OrderColumn(name = "position")
    private List<String> ngram = new ArrayList<>();

    /**
     * Number of events contributing to this n-gram
     */
    @Column(name = "event_count")
    private Integer eventCount = 0;

    /**
     * Distinct learner IDs who have this n-gram
     */
    @ElementCollection
    @CollectionTable(name = "sequence_stats_learners", schema = "app", joinColumns = @JoinColumn(name = "stats_id"))
    @Column(name = "learner_id")
    private List<Long> learnerIds = new ArrayList<>();

    @Column(name = "last_updated")
    private Instant lastUpdated = Instant.now();
}
