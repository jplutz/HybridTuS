package org.example.protushybrid.service.recommendation.generation;

import java.util.*;

/**
 * Markov chain transition matrix for LO type sequences.
 * Stores P(next_type | current_type) probabilities.
 *
 * <p><b>DUAL-PURPOSE UTILITY:</b></p>
 * <ul>
 *   <li><b>PRODUCTION</b>: Used by {@link DefaultSequenceProvider} to sample next LO types
 *       during cold-start recommendations (via {@link FslsmMatrices})</li>
 *   <li><b>TESTING</b>: Used by {@link MatrixBasedSequenceGenerator} to generate synthetic
 *       learning sequences for testing and evaluation</li>
 * </ul>
 *
 * <p>Supports matrix blending (T_fslsm + T_noise) for phase-specific generation.</p>
 *
 * @see FslsmMatrices
 * @see Distribution
 * @see DefaultSequenceProvider
 */
public class TransitionMatrix {

    private final Map<String, Distribution> transitions;
    private final Random random;

    private TransitionMatrix(Map<String, Distribution> transitions, Random random) {
        this.transitions = new LinkedHashMap<>(transitions);
        this.random = random;
    }

    /**
     * Create transition matrix from nested probability map.
     * Each row (from-state) must have probabilities that sum to 1.0.
     *
     * @param probabilities Map of fromType -> (toType -> probability)
     * @param random Random instance for sampling
     */
    public static TransitionMatrix of(Map<String, Map<String, Double>> probabilities, Random random) {
        Map<String, Distribution> transitions = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Double>> entry : probabilities.entrySet()) {
            String fromType = entry.getKey();
            Map<String, Double> row = entry.getValue();

            // Validate row sums to ~1.0
            double sum = row.values().stream().mapToDouble(Double::doubleValue).sum();
            if (Math.abs(sum - 1.0) > 0.001) {
                throw new IllegalArgumentException(
                    String.format("Row '%s' probabilities must sum to 1.0, got: %.4f", fromType, sum)
                );
            }

            transitions.put(fromType, Distribution.of(row, random));
        }

        return new TransitionMatrix(transitions, random);
    }

    /**
     * Sample the next LO type given current type using this transition matrix.
     * Implements Markov chain: P(next | current).
     *
     * @param currentType Current LO type (e.g. "T", "E", "A")
     * @return Next LO type sampled from transition probabilities
     * @throws IllegalStateException if currentType not in matrix
     */
    public String sampleNext(String currentType) {
        Distribution dist = transitions.get(currentType);
        if (dist == null) {
            throw new IllegalStateException(
                "No transitions defined for current type: " + currentType +
                ". Available types: " + transitions.keySet()
            );
        }
        return dist.sample();
    }

    /**
     * Blend two transition matrices with adherence coefficient a_p.
     * Result: a_p * matrix1 + (1 - a_p) * matrix2, normalized row-wise.
     *
     * Each row is blended independently using Distribution.blend().
     * This is used to combine T_fslsm and T_noise matrices for phase-specific generation.
     *
     * @param matrix1 First matrix (typically T_fslsm)
     * @param matrix2 Second matrix (typically T_noise)
     * @param a_p Adherence coefficient in [0,1]
     * @param random Random instance for the result matrix
     * @return Blended transition matrix
     */
    public static TransitionMatrix blend(TransitionMatrix matrix1, TransitionMatrix matrix2,
                                         double a_p, Random random) {
        if (a_p < 0.0 || a_p > 1.0) {
            throw new IllegalArgumentException("Adherence a_p must be in [0,1], got: " + a_p);
        }

        // Collect all unique from-states
        Set<String> allFromTypes = new LinkedHashSet<>();
        allFromTypes.addAll(matrix1.transitions.keySet());
        allFromTypes.addAll(matrix2.transitions.keySet());

        // Blend each row
        Map<String, Distribution> blendedTransitions = new LinkedHashMap<>();
        for (String fromType : allFromTypes) {
            Distribution dist1 = matrix1.transitions.get(fromType);
            Distribution dist2 = matrix2.transitions.get(fromType);

            if (dist1 == null && dist2 == null) {
                throw new IllegalStateException("Both matrices missing row for: " + fromType);
            }

            // If one matrix is missing this row, use uniform distribution as fallback
            if (dist1 == null) {
                dist1 = Distribution.uniform(new ArrayList<>(dist2.getProbabilities().keySet()), random);
            }
            if (dist2 == null) {
                dist2 = Distribution.uniform(new ArrayList<>(dist1.getProbabilities().keySet()), random);
            }

            blendedTransitions.put(fromType, Distribution.blend(dist1, dist2, a_p, random));
        }

        return new TransitionMatrix(blendedTransitions, random);
    }

    /**
     * Get all from-types (states) in this matrix.
     */
    public Set<String> getFromTypes() {
        return Collections.unmodifiableSet(transitions.keySet());
    }

    /**
     * Get the probability distribution for a given from-type.
     * Returns null if from-type not in matrix.
     */
    public Distribution getTransitionDistribution(String fromType) {
        return transitions.get(fromType);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TransitionMatrix{\n");
        transitions.forEach((from, dist) -> {
            sb.append(String.format("  %s -> %s\n", from, dist.getProbabilities()));
        });
        sb.append("}");
        return sb.toString();
    }
}
