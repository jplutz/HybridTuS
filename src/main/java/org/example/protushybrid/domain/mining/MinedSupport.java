package org.example.protushybrid.domain.mining;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "mined_supports", schema = "app")
public class MinedSupport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_key", nullable = false)
    private String clusterKey;

    @Column(name = "concept_id", nullable = false)
    private Long conceptId;

    @Column(name = "suffix", nullable = false)
    private String suffix; // e.g. "T>E" (canonical codes: T=Theory, E=Example)

    @Column(name = "next_type", nullable = false)
    private String nextType; // e.g. "A" (canonical: T, E, A, F, X, R, S, O, Test)

    @Column(name = "support", nullable = false)
    private Double support;

    @Column(name = "n_support", nullable = false)
    private Integer nSupport;

    @Column(name = "n_learners", nullable = false)
    private Integer nLearners; // Distinct learner count who exhibited this pattern

    @Column(name = "last_mined_at", nullable = false)
    private Instant lastMinedAt;

    public MinedSupport() {}

    public MinedSupport(String clusterKey, Long conceptId, String suffix, String nextType, Double support, Integer nSupport) {
        this.clusterKey = clusterKey;
        this.conceptId = conceptId;
        this.suffix = suffix;
        this.nextType = nextType;
        this.support = support;
        this.nSupport = nSupport;
        this.nLearners = 0; // Will be set by mining service
        this.lastMinedAt = Instant.now();
    }

    public MinedSupport(String clusterKey, Long conceptId, String suffix, String nextType, Double support, Integer nSupport, Integer nLearners) {
        this.clusterKey = clusterKey;
        this.conceptId = conceptId;
        this.suffix = suffix;
        this.nextType = nextType;
        this.support = support;
        this.nSupport = nSupport;
        this.nLearners = nLearners;
        this.lastMinedAt = Instant.now();
    }

    public void setClusterKey(String clusterKey) {
        this.clusterKey = clusterKey;
    }

    public void setConceptId(Long conceptId) {
        this.conceptId = conceptId;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public void setNextType(String nextType) {
        this.nextType = nextType;
    }

    public void setSupport(Double support) {
        this.support = support;
    }

    public void setNSupport(Integer nSupport) {
        this.nSupport = nSupport;
    }

    public void setNLearners(Integer nLearners) {
        this.nLearners = nLearners;
    }

    public void setLastMinedAt(Instant lastMinedAt) {
        this.lastMinedAt = lastMinedAt;
    }
}
