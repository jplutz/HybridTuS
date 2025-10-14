package org.example.protushybrid.domain.tracking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.core.LearningObject;

import java.time.Instant;

@Entity
@Table(name = "interactions", schema = "app")
@Getter
@Setter
public class Interaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id")
    private Concept concept;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lo_id")
    private LearningObject learningObject;

    /**
     * Action type: COMPLETE, VIEW, REVISIT, etc.
     */
    @Column(nullable = false, length = 64)
    private String action;

    private String result;  // pass | fail | score

    /**
     * Normalized score 0.0-1.0, used directly for EWMA mastery tracking
     */
    private Double score;

    /**
     * Raw score on 1-5 scale (kept for historical/audit purposes)
     * No longer used in EWMA calculation; score field is used directly
     */
    @Column(name = "score_raw")
    private Short scoreRaw;

    /**
     * Number of hints used during this interaction
     */
    @Column(name = "hint_count")
    private Integer hintCount = 0;

    /**
     * Learning object type (denormalized for quick filtering)
     */
    @Column(name = "lo_type", length = 32)
    private String loType;

    @Column(name = "duration_s")
    private Integer durationSeconds;

    @Column(name = "ts")
    private Instant timestamp = Instant.now();
}
