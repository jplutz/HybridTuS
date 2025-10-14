package org.example.protushybrid.domain.mining;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.protushybrid.domain.core.Concept;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Records mining violations.
 * Violations include invalid sequences or anomalies detected during mining.
 */
@Entity
@Table(name = "mining_violations", schema = "app")
@Getter
@Setter
public class MiningViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_key", nullable = false, length = 64)
    private String clusterKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id")
    private Concept concept;

    /**
     * The problematic sequence
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "violation_sequence",
            schema = "app",
            joinColumns = @JoinColumn(name = "violation_id")
    )
    @OrderColumn(name = "item_order")
    @Column(name = "lo_type", length = 32)
    private List<String> seq = new ArrayList<>();

    /**
     * Reason for violation (e.g., "loops", "invalid_order", "missing_prerequisites")
     */
    @Column(name = "violation_reason", length = 255)
    private String violationReason;

    @Column(name = "detected_at")
    private Instant detectedAt = Instant.now();

    /**
     * Whether the violation has been reviewed by an SME
     */
    @Column(name = "reviewed")
    private Boolean reviewed = false;
}
