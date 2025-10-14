package org.example.protushybrid.service.recommendation.generation;

import java.util.*;

/**
 * Probability distribution over discrete outcomes (LO types).
 * Supports probabilistic sampling and distribution blending.
 *
 * <p><b>DUAL-PURPOSE UTILITY:</b></p>
 * <ul>
 *   <li><b>PRODUCTION</b>: Used by {@link TransitionMatrix} for sampling next LO types
 *       during cold-start recommendations in {@link DefaultSequenceProvider}</li>
 *   <li><b>TESTING</b>: Used by {@link MatrixBasedSequenceGenerator} to generate synthetic
 *       learning sequences with realistic probabilities</li>
 * </ul>
 *
 * <p>Provides methods for uniform distribution, custom probability maps, and blending
 * multiple distributions with adherence coefficients.</p>
 *
 * @see TransitionMatrix
 * @see FslsmMatrices
 * @see DefaultSequenceProvider
 */
public class Distribution {

    private final Map<String, Double> probabilities;
    private final List<String> outcomes;
    private final Random random;

    private Distribution(Map<String, Double> probabilities, Random random) {
        this.probabilities = new LinkedHashMap<>(probabilities);
        this.outcomes = new ArrayList<>(probabilities.keySet());
        this.random = random;

        // Validate probabilities sum to ~1.0
        double sum = probabilities.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalArgumentException("Probabilities must sum to 1.0, got: " + sum);
        }
    }

    /**
     * Create distribution from probability map.
     * Probabilities must sum to 1.0 (±0.001 tolerance).
     */
    public static Distribution of(Map<String, Double> probabilities, Random random) {
        return new Distribution(probabilities, random);
    }

    /**
     * Create uniform distribution over given outcomes.
     */
    public static Distribution uniform(List<String> outcomes, Random random) {
        double p = 1.0 / outcomes.size();
        Map<String, Double> probabilities = new LinkedHashMap<>();
        for (String outcome : outcomes) {
            probabilities.put(outcome, p);
        }
        return new Distribution(probabilities, random);
    }

    /**
     * Sample an outcome from this distribution.
     */
    public String sample() {
        double r = random.nextDouble();
        double cumulative = 0.0;

        for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
            cumulative += entry.getValue();
            if (r < cumulative) {
                return entry.getKey();
            }
        }

        // Fallback (should never happen with valid probabilities)
        return outcomes.get(outcomes.size() - 1);
    }

    /**
     * Blend two distributions with adherence coefficient a_p.
     * Result: a_p * dist1 + (1 - a_p) * dist2, normalized.
     */
    public static Distribution blend(Distribution dist1, Distribution dist2, double a_p, Random random) {
        if (a_p < 0.0 || a_p > 1.0) {
            throw new IllegalArgumentException("Adherence a_p must be in [0,1], got: " + a_p);
        }

        // Collect all unique outcomes
        Set<String> allOutcomes = new LinkedHashSet<>();
        allOutcomes.addAll(dist1.probabilities.keySet());
        allOutcomes.addAll(dist2.probabilities.keySet());

        // Blend probabilities
        Map<String, Double> blended = new LinkedHashMap<>();
        for (String outcome : allOutcomes) {
            double p1 = dist1.probabilities.getOrDefault(outcome, 0.0);
            double p2 = dist2.probabilities.getOrDefault(outcome, 0.0);
            blended.put(outcome, a_p * p1 + (1 - a_p) * p2);
        }

        // Normalize
        double sum = blended.values().stream().mapToDouble(Double::doubleValue).sum();
        blended.replaceAll((k, v) -> v / sum);

        return new Distribution(blended, random);
    }

    public Map<String, Double> getProbabilities() {
        return Collections.unmodifiableMap(probabilities);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Distribution{\n");
        probabilities.forEach((k, v) -> sb.append(String.format("  %s: %.3f\n", k, v)));
        sb.append("}");
        return sb.toString();
    }
}
