package org.example.protushybrid.service.recommendation.generation;

import java.util.*;

/**
 * FSLSM-based transition matrices (T_fslsm) for all 16 learning style clusters.
 * Each matrix encodes P(next_type | current_type) based on pedagogical theory.
 *
 * <p><b>DUAL-PURPOSE UTILITY:</b></p>
 * <ul>
 *   <li><b>PRODUCTION</b>: Used by {@link DefaultSequenceProvider} for cold-start recommendations
 *       when no mined patterns exist for a learner's cluster</li>
 *   <li><b>TESTING</b>: Used by {@link MatrixBasedSequenceGenerator} to generate synthetic
 *       learning session data</li>
 * </ul>
 *
 * <p>Clusters are defined by 4 FSLSM dimensions:</p>
 * <ul>
 *   <li>Active/Reflective (ACT/REF)</li>
 *   <li>Sensing/Intuitive (SEN/INT)</li>
 *   <li>Visual/Verbal (VIS/VRB)</li>
 *   <li>Sequential/Global (SEQ/GLO)</li>
 * </ul>
 *
 * <p>Also includes T_noise: pedagogically-biased noise matrix for exploration.</p>
 *
 * @see DefaultSequenceProvider
 * @see TransitionMatrix
 */
public class FslsmMatrices {

    // LO types used in transitions
    private static final List<String> LO_TYPES = List.of("T", "E", "A", "F", "Test");

    /**
     * Get FSLSM transition matrix for a given cluster key.
     *
     * @param clusterKey Cluster identifier (e.g. "ACTIVE_SENSING_VISUAL_SEQUENTIAL")
     * @param random Random instance for matrix sampling
     * @return TransitionMatrix for the cluster, or null if cluster not recognized
     */
    public static TransitionMatrix getMatrix(String clusterKey, Random random) {
        Map<String, Map<String, Double>> probabilities = switch (clusterKey) {
            case "ACTIVE_SENSING_VISUAL_SEQUENTIAL" -> activeSensingVisualSequential();
            case "ACTIVE_SENSING_VISUAL_GLOBAL" -> activeSensingVisualGlobal();
            case "ACTIVE_SENSING_VERBAL_SEQUENTIAL" -> activeSensingVerbalSequential();
            case "ACTIVE_SENSING_VERBAL_GLOBAL" -> activeSensingVerbalGlobal();
            case "ACTIVE_INTUITIVE_VISUAL_SEQUENTIAL" -> activeIntuitiveVisualSequential();
            case "ACTIVE_INTUITIVE_VISUAL_GLOBAL" -> activeIntuitiveVisualGlobal();
            case "ACTIVE_INTUITIVE_VERBAL_SEQUENTIAL" -> activeIntuitiveVerbalSequential();
            case "ACTIVE_INTUITIVE_VERBAL_GLOBAL" -> activeIntuitiveVerbalGlobal();
            case "REFLECTIVE_SENSING_VISUAL_SEQUENTIAL" -> reflectiveSensingVisualSequential();
            case "REFLECTIVE_SENSING_VISUAL_GLOBAL" -> reflectiveSensingVisualGlobal();
            case "REFLECTIVE_SENSING_VERBAL_SEQUENTIAL" -> reflectiveSensingVerbalSequential();
            case "REFLECTIVE_SENSING_VERBAL_GLOBAL" -> reflectiveSensingVerbalGlobal();
            case "REFLECTIVE_INTUITIVE_VISUAL_SEQUENTIAL" -> reflectiveIntuitiveVisualSequential();
            case "REFLECTIVE_INTUITIVE_VISUAL_GLOBAL" -> reflectiveIntuitiveVisualGlobal();
            case "REFLECTIVE_INTUITIVE_VERBAL_SEQUENTIAL" -> reflectiveIntuitiveVerbalSequential();
            case "REFLECTIVE_INTUITIVE_VERBAL_GLOBAL" -> reflectiveIntuitiveVerbalGlobal();
            default -> null;
        };

        if (probabilities == null) {
            return null;
        }

        return TransitionMatrix.of(probabilities, random);
    }

    /**
     * Get pedagogically-biased noise matrix (T_noise).
     * Encodes valid pedagogical transitions but without cluster-specific bias.
     * Used for exploration and phase-based drift.
     *
     * Bias: T→E, E→A, A→Test more likely than random
     */
    public static TransitionMatrix getNoiseMatrix(Random random) {
        Map<String, Map<String, Double>> probabilities = new LinkedHashMap<>();

        // T → prefer E and A (pedagogical flow)
        probabilities.put("T", Map.of(
            "T", 0.10,
            "E", 0.40,
            "A", 0.25,
            "F", 0.15,
            "Test", 0.10
        ));

        // E → prefer A (practice after example)
        probabilities.put("E", Map.of(
            "T", 0.15,
            "E", 0.15,
            "A", 0.40,
            "F", 0.15,
            "Test", 0.15
        ));

        // A → balanced, slight preference for Test
        probabilities.put("A", Map.of(
            "T", 0.15,
            "E", 0.25,
            "A", 0.20,
            "F", 0.15,
            "Test", 0.25
        ));

        // F → prefer back to T or E
        probabilities.put("F", Map.of(
            "T", 0.30,
            "E", 0.35,
            "A", 0.20,
            "F", 0.10,
            "Test", 0.05
        ));

        // Test → absorbing state
        probabilities.put("Test", Map.of(
            "T", 0.0,
            "E", 0.0,
            "A", 0.0,
            "F", 0.0,
            "Test", 1.0
        ));

        return TransitionMatrix.of(probabilities, random);
    }

    // ==================== ACTIVE + SENSING ====================

    /**
     * ACTIVE_SENSING_VISUAL_SEQUENTIAL:
     * Hands-on, concrete, visual, step-by-step learners.
     * Pattern: T → F (visual aid) → E (concrete example) → A (hands-on practice)
     */
    private static Map<String, Map<String, Double>> activeSensingVisualSequential() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.05, "E", 0.35, "A", 0.10, "F", 0.40, "Test", 0.10));
        m.put("E", Map.of("T", 0.05, "E", 0.05, "A", 0.60, "F", 0.20, "Test", 0.10));
        m.put("A", Map.of("T", 0.05, "E", 0.30, "A", 0.30, "F", 0.20, "Test", 0.15));
        m.put("F", Map.of("T", 0.20, "E", 0.40, "A", 0.30, "F", 0.05, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * ACTIVE_SENSING_VISUAL_GLOBAL:
     * Hands-on, concrete, visual, big-picture learners.
     * Pattern: Overview → visual aids → concrete examples → practice
     */
    private static Map<String, Map<String, Double>> activeSensingVisualGlobal() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.10, "E", 0.30, "A", 0.15, "F", 0.35, "Test", 0.10));
        m.put("E", Map.of("T", 0.10, "E", 0.10, "A", 0.55, "F", 0.15, "Test", 0.10));
        m.put("A", Map.of("T", 0.10, "E", 0.25, "A", 0.35, "F", 0.15, "Test", 0.15));
        m.put("F", Map.of("T", 0.15, "E", 0.45, "A", 0.25, "F", 0.10, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * ACTIVE_SENSING_VERBAL_SEQUENTIAL:
     * Hands-on, concrete, verbal, step-by-step learners.
     * Pattern: T → E (concrete examples) → A (immediate practice)
     */
    private static Map<String, Map<String, Double>> activeSensingVerbalSequential() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.10, "E", 0.45, "A", 0.20, "F", 0.10, "Test", 0.15));
        m.put("E", Map.of("T", 0.05, "E", 0.10, "A", 0.65, "F", 0.10, "Test", 0.10));
        m.put("A", Map.of("T", 0.10, "E", 0.35, "A", 0.30, "F", 0.10, "Test", 0.15));
        m.put("F", Map.of("T", 0.25, "E", 0.40, "A", 0.25, "F", 0.05, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * ACTIVE_SENSING_VERBAL_GLOBAL:
     * Hands-on, concrete, verbal, big-picture learners.
     * Pattern: Overview → examples → diverse practice
     */
    private static Map<String, Map<String, Double>> activeSensingVerbalGlobal() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.15, "E", 0.40, "A", 0.20, "F", 0.10, "Test", 0.15));
        m.put("E", Map.of("T", 0.10, "E", 0.15, "A", 0.55, "F", 0.10, "Test", 0.10));
        m.put("A", Map.of("T", 0.10, "E", 0.30, "A", 0.35, "F", 0.10, "Test", 0.15));
        m.put("F", Map.of("T", 0.20, "E", 0.45, "A", 0.20, "F", 0.10, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    // ==================== ACTIVE + INTUITIVE ====================

    /**
     * ACTIVE_INTUITIVE_VISUAL_SEQUENTIAL:
     * Hands-on, abstract, visual, step-by-step learners.
     * Pattern: T → A (quick to practice) with occasional visuals
     */
    private static Map<String, Map<String, Double>> activeIntuitiveVisualSequential() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.10, "E", 0.25, "A", 0.35, "F", 0.20, "Test", 0.10));
        m.put("E", Map.of("T", 0.10, "E", 0.10, "A", 0.55, "F", 0.15, "Test", 0.10));
        m.put("A", Map.of("T", 0.15, "E", 0.20, "A", 0.35, "F", 0.15, "Test", 0.15));
        m.put("F", Map.of("T", 0.30, "E", 0.25, "A", 0.30, "F", 0.10, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * ACTIVE_INTUITIVE_VISUAL_GLOBAL:
     * Hands-on, abstract, visual, big-picture learners.
     * Pattern: Explore visually, jump to practice
     */
    private static Map<String, Map<String, Double>> activeIntuitiveVisualGlobal() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.15, "E", 0.20, "A", 0.30, "F", 0.25, "Test", 0.10));
        m.put("E", Map.of("T", 0.15, "E", 0.10, "A", 0.50, "F", 0.15, "Test", 0.10));
        m.put("A", Map.of("T", 0.15, "E", 0.20, "A", 0.40, "F", 0.10, "Test", 0.15));
        m.put("F", Map.of("T", 0.25, "E", 0.30, "A", 0.30, "F", 0.10, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * ACTIVE_INTUITIVE_VERBAL_SEQUENTIAL:
     * Hands-on, abstract, verbal, step-by-step learners.
     * Pattern: T → A (quick to practice) minimal examples
     */
    private static Map<String, Map<String, Double>> activeIntuitiveVerbalSequential() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.15, "E", 0.20, "A", 0.45, "F", 0.10, "Test", 0.10));
        m.put("E", Map.of("T", 0.15, "E", 0.10, "A", 0.60, "F", 0.05, "Test", 0.10));
        m.put("A", Map.of("T", 0.20, "E", 0.20, "A", 0.35, "F", 0.10, "Test", 0.15));
        m.put("F", Map.of("T", 0.35, "E", 0.30, "A", 0.25, "F", 0.05, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * ACTIVE_INTUITIVE_VERBAL_GLOBAL:
     * Hands-on, abstract, verbal, big-picture learners.
     * Pattern: Overview → immediate practice, minimal examples
     */
    private static Map<String, Map<String, Double>> activeIntuitiveVerbalGlobal() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.20, "E", 0.15, "A", 0.45, "F", 0.10, "Test", 0.10));
        m.put("E", Map.of("T", 0.20, "E", 0.10, "A", 0.55, "F", 0.05, "Test", 0.10));
        m.put("A", Map.of("T", 0.20, "E", 0.15, "A", 0.45, "F", 0.05, "Test", 0.15));
        m.put("F", Map.of("T", 0.30, "E", 0.35, "A", 0.25, "F", 0.05, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    // ==================== REFLECTIVE + SENSING ====================

    /**
     * REFLECTIVE_SENSING_VISUAL_SEQUENTIAL:
     * Thoughtful, concrete, visual, step-by-step learners.
     * Pattern: E (observe first) → T → F (visual reflection) → A
     */
    private static Map<String, Map<String, Double>> reflectiveSensingVisualSequential() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.15, "E", 0.35, "A", 0.15, "F", 0.25, "Test", 0.10));
        m.put("E", Map.of("T", 0.25, "E", 0.15, "A", 0.35, "F", 0.15, "Test", 0.10));
        m.put("A", Map.of("T", 0.15, "E", 0.30, "A", 0.25, "F", 0.15, "Test", 0.15));
        m.put("F", Map.of("T", 0.25, "E", 0.30, "A", 0.30, "F", 0.10, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * REFLECTIVE_SENSING_VISUAL_GLOBAL:
     * Thoughtful, concrete, visual, big-picture learners.
     * Pattern: Explore examples and visuals before practice
     */
    private static Map<String, Map<String, Double>> reflectiveSensingVisualGlobal() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.20, "E", 0.30, "A", 0.15, "F", 0.25, "Test", 0.10));
        m.put("E", Map.of("T", 0.20, "E", 0.20, "A", 0.30, "F", 0.20, "Test", 0.10));
        m.put("A", Map.of("T", 0.15, "E", 0.25, "A", 0.30, "F", 0.15, "Test", 0.15));
        m.put("F", Map.of("T", 0.20, "E", 0.35, "A", 0.25, "F", 0.15, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * REFLECTIVE_SENSING_VERBAL_SEQUENTIAL:
     * Thoughtful, concrete, verbal, step-by-step learners.
     * Pattern: E → T (read, then understand) → A (careful practice)
     */
    private static Map<String, Map<String, Double>> reflectiveSensingVerbalSequential() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.20, "E", 0.40, "A", 0.20, "F", 0.10, "Test", 0.10));
        m.put("E", Map.of("T", 0.30, "E", 0.15, "A", 0.40, "F", 0.05, "Test", 0.10));
        m.put("A", Map.of("T", 0.20, "E", 0.30, "A", 0.30, "F", 0.05, "Test", 0.15));
        m.put("F", Map.of("T", 0.30, "E", 0.35, "A", 0.25, "F", 0.05, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * REFLECTIVE_SENSING_VERBAL_GLOBAL:
     * Thoughtful, concrete, verbal, big-picture learners.
     * Pattern: Read broadly, connect examples, then practice
     */
    private static Map<String, Map<String, Double>> reflectiveSensingVerbalGlobal() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.25, "E", 0.35, "A", 0.20, "F", 0.10, "Test", 0.10));
        m.put("E", Map.of("T", 0.25, "E", 0.20, "A", 0.35, "F", 0.10, "Test", 0.10));
        m.put("A", Map.of("T", 0.20, "E", 0.25, "A", 0.35, "F", 0.05, "Test", 0.15));
        m.put("F", Map.of("T", 0.25, "E", 0.40, "A", 0.20, "F", 0.10, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    // ==================== REFLECTIVE + INTUITIVE ====================

    /**
     * REFLECTIVE_INTUITIVE_VISUAL_SEQUENTIAL:
     * Thoughtful, abstract, visual, step-by-step learners.
     * Pattern: T → F (visualize concepts) → A (methodical practice)
     */
    private static Map<String, Map<String, Double>> reflectiveIntuitiveVisualSequential() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.20, "E", 0.20, "A", 0.25, "F", 0.25, "Test", 0.10));
        m.put("E", Map.of("T", 0.25, "E", 0.10, "A", 0.40, "F", 0.15, "Test", 0.10));
        m.put("A", Map.of("T", 0.25, "E", 0.20, "A", 0.30, "F", 0.10, "Test", 0.15));
        m.put("F", Map.of("T", 0.30, "E", 0.25, "A", 0.30, "F", 0.10, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * REFLECTIVE_INTUITIVE_VISUAL_GLOBAL:
     * Thoughtful, abstract, visual, big-picture learners.
     * Pattern: Explore theory and visuals, synthesize broadly
     */
    private static Map<String, Map<String, Double>> reflectiveIntuitiveVisualGlobal() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.25, "E", 0.15, "A", 0.25, "F", 0.25, "Test", 0.10));
        m.put("E", Map.of("T", 0.30, "E", 0.10, "A", 0.35, "F", 0.15, "Test", 0.10));
        m.put("A", Map.of("T", 0.25, "E", 0.15, "A", 0.35, "F", 0.10, "Test", 0.15));
        m.put("F", Map.of("T", 0.30, "E", 0.30, "A", 0.25, "F", 0.10, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * REFLECTIVE_INTUITIVE_VERBAL_SEQUENTIAL:
     * Thoughtful, abstract, verbal, step-by-step learners.
     * Pattern: T (deep reading) → A (careful practice)
     */
    private static Map<String, Map<String, Double>> reflectiveIntuitiveVerbalSequential() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.25, "E", 0.15, "A", 0.40, "F", 0.10, "Test", 0.10));
        m.put("E", Map.of("T", 0.30, "E", 0.10, "A", 0.45, "F", 0.05, "Test", 0.10));
        m.put("A", Map.of("T", 0.30, "E", 0.15, "A", 0.35, "F", 0.05, "Test", 0.15));
        m.put("F", Map.of("T", 0.35, "E", 0.30, "A", 0.25, "F", 0.05, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * REFLECTIVE_INTUITIVE_VERBAL_GLOBAL:
     * Thoughtful, abstract, verbal, big-picture learners.
     * Pattern: Read broadly, synthesize, then practice
     */
    private static Map<String, Map<String, Double>> reflectiveIntuitiveVerbalGlobal() {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("T", Map.of("T", 0.30, "E", 0.10, "A", 0.40, "F", 0.10, "Test", 0.10));
        m.put("E", Map.of("T", 0.35, "E", 0.10, "A", 0.40, "F", 0.05, "Test", 0.10));
        m.put("A", Map.of("T", 0.30, "E", 0.10, "A", 0.40, "F", 0.05, "Test", 0.15));
        m.put("F", Map.of("T", 0.35, "E", 0.35, "A", 0.20, "F", 0.05, "Test", 0.05));
        m.put("Test", Map.of("T", 0.0, "E", 0.0, "A", 0.0, "F", 0.0, "Test", 1.0));
        return m;
    }

    /**
     * Get list of all supported cluster keys.
     */
    public static List<String> getAllClusterKeys() {
        return List.of(
            "ACTIVE_SENSING_VISUAL_SEQUENTIAL",
            "ACTIVE_SENSING_VISUAL_GLOBAL",
            "ACTIVE_SENSING_VERBAL_SEQUENTIAL",
            "ACTIVE_SENSING_VERBAL_GLOBAL",
            "ACTIVE_INTUITIVE_VISUAL_SEQUENTIAL",
            "ACTIVE_INTUITIVE_VISUAL_GLOBAL",
            "ACTIVE_INTUITIVE_VERBAL_SEQUENTIAL",
            "ACTIVE_INTUITIVE_VERBAL_GLOBAL",
            "REFLECTIVE_SENSING_VISUAL_SEQUENTIAL",
            "REFLECTIVE_SENSING_VISUAL_GLOBAL",
            "REFLECTIVE_SENSING_VERBAL_SEQUENTIAL",
            "REFLECTIVE_SENSING_VERBAL_GLOBAL",
            "REFLECTIVE_INTUITIVE_VISUAL_SEQUENTIAL",
            "REFLECTIVE_INTUITIVE_VISUAL_GLOBAL",
            "REFLECTIVE_INTUITIVE_VERBAL_SEQUENTIAL",
            "REFLECTIVE_INTUITIVE_VERBAL_GLOBAL"
        );
    }
}
