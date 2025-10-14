package org.example.protushybrid.domain.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "learning_objects", schema = "app")
@Getter
@Setter
public class LearningObject {

    /**
     * Learning Object Type Constants
     * Based on FSLSM learning science principles for adaptive sequencing
     *
     * The 5 canonical types used in this system:
     * T, E, A, F, Test
     */
    public static class Type {
        /** Theory - Foundational explanations and conceptual frameworks */
        public static final String THEORY = "T";

        /** Example - Demonstrations and concrete applications */
        public static final String EXAMPLE = "E";

        /** Activity - Hands-on exercises, quizzes, practice problems */
        public static final String ACTIVITY = "A";

        /** Figure - Visual representation: diagrams, flowcharts, screenshots, maps
         * (recommended for Visual learners; accessible to all through UI) */
        public static final String FIGURE = "F";

        /** Test - Final knowledge checks and evaluations */
        public static final String TEST = "Test";

        private Type() {} // Prevent instantiation
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id")
    private Concept concept;

    /**
     * Learning object type - one of the Type constants
     * Stored as String for flexibility but should match Type constants
     */
    @Column(nullable = false, length = 255)
    private String type;

    /**
     * Optional custom title for the learning object.
     * If null, title should be auto-generated from type and concept name.
     */
    @Column(length = 255)
    private String title;

    @Column(name = "source_uri")
    private String sourceUri;

    @Column(name = "est_time_min")
    private Integer estTimeMin;

    /**
     * Content version number for A/B testing and rollback
     */
    @Column(nullable = false)
    private Integer version = 1;

    /**
     * Link to grading rubric for LLM-based assessment
     */
    @Column(name = "rubric_link", columnDefinition = "TEXT")
    private String rubricLink;
}
