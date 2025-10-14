package org.example.protushybrid.domain.tracking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.Learner;

@Entity
@Table(name = "mastery", schema = "app")
@Getter
@Setter
public class Mastery {
    @EmbeddedId
    private Pk id = new Pk();

    @MapsId("learnerId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @MapsId("conceptId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id", nullable = false)
    private Concept concept;

    /**
     * EWMA mastery probability estimate (0.0 to 1.0)
     * Updated with α=0.4, p̂₀=0.5
     */
    @Column(nullable = false)
    private Double mastery = 0.5;

    /**
     * Number of graded events contributing to this estimate
     * Status labels suppressed until eventCount >= 3
     */
    @Column(name = "event_count", nullable = false)
    private Integer eventCount = 0;

    /**
     * Timestamp of last mastery update
     */
    @Column(name = "last_updated")
    private java.time.Instant lastUpdated;

    @Embeddable
    @Getter @Setter
    public static class Pk implements java.io.Serializable {
        @Column(name = "learner_id") private Long learnerId;
        @Column(name = "concept_id") private Long conceptId;
    }
}
