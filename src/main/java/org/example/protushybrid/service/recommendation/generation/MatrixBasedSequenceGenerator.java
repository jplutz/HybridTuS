package org.example.protushybrid.service.recommendation.generation;

import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.core.LearningObject;
import org.example.protushybrid.service.core.ClusterKeyUtil;

import java.util.*;

/**
 * Generates LO sequences using FSLSM transition matrices with phase-based adherence.
 * Implements Markov chain sampling with temporal evolution (T_phase = a_p·T_fslsm + (1-a_p)·T_noise).
 *
 * Phases:
 * - T₀ (a_p=0.95): Strong FSLSM adherence (early learners follow theory)
 * - T₁ (a_p=0.70): Moderate adherence (established learners with variation)
 * - T₂ (a_p=0.20): Weak adherence (experienced learners explore freely)
 */
public class MatrixBasedSequenceGenerator {

    private final Random random;
    private final TransitionMatrix noiseMatrix;
    private final Map<String, TransitionMatrix> fslsmMatrixCache;

    // Start state distribution: prefer T (Theory) as first LO
    // Aligned with 5 core types supported by FSLSM matrices
    private static final Map<String, Double> START_DISTRIBUTION = Map.of(
        "T", 0.60,    // Theory (most common starting point)
        "E", 0.20,    // Example (concrete learners may start here)
        "A", 0.10,    // Activity (active learners jump to practice)
        "F", 0.10,    // Figure (visual learners prefer diagrams)
        "Test", 0.0   // Test (never start with assessment)
    );

    public MatrixBasedSequenceGenerator(Random random) {
        this.random = random;
        this.noiseMatrix = FslsmMatrices.getNoiseMatrix(random);
        this.fslsmMatrixCache = new HashMap<>();
    }

    /**
     * Generate a sequence of LO IDs for a learner based on their FSLSM profile.
     *
     * @param learner Learner with FSLSM profile (determines cluster and T_fslsm)
     * @param availableLOs Learning objects to select from (must contain LOs of each type)
     * @param adherence Adherence coefficient a_p in [0,1] (0.95 for T₀, 0.70 for T₁, 0.20 for T₂)
     * @param targetLength Target sequence length (will generate this many LO visits)
     * @return List of LO IDs representing the generated sequence
     */
    public List<Long> generateSequence(Learner learner, List<LearningObject> availableLOs,
                                       double adherence, int targetLength) {
        if (targetLength <= 0) {
            throw new IllegalArgumentException("Target length must be positive, got: " + targetLength);
        }
        if (adherence < 0.0 || adherence > 1.0) {
            throw new IllegalArgumentException("Adherence must be in [0,1], got: " + adherence);
        }

        // Get or create T_fslsm for learner's cluster
        String clusterKey = computeClusterKey(learner);
        TransitionMatrix fslsmMatrix = getFslsmMatrix(clusterKey);

        // Blend T_fslsm and T_noise with phase-specific adherence
        TransitionMatrix phaseMatrix = TransitionMatrix.blend(fslsmMatrix, noiseMatrix, adherence, random);

        // Build LO type index for fast lookup
        Map<String, List<LearningObject>> losByType = indexByType(availableLOs);

        // Generate sequence
        List<Long> sequence = new ArrayList<>();
        String currentType = sampleStartState();

        for (int i = 0; i < targetLength; i++) {
            // Sample an LO of current type
            LearningObject lo = sampleLO(currentType, losByType);
            if (lo != null) {
                sequence.add(lo.getId());
            }

            // Transition to next type using phase matrix
            currentType = phaseMatrix.sampleNext(currentType);

            // If we hit Test, consider it absorbing state (stop or stay)
            if (currentType.equals("Test") && random.nextDouble() < 0.8) {
                // 80% chance to end session after Test
                break;
            }
        }

        return sequence;
    }

    /**
     * Compute FSLSM cluster key from learner's style scores.
     * Delegates to ClusterKeyUtil for consistency.
     */
    private String computeClusterKey(Learner learner) {
        return ClusterKeyUtil.forLearner(learner);
    }

    /**
     * Get or load T_fslsm matrix for a cluster.
     */
    private TransitionMatrix getFslsmMatrix(String clusterKey) {
        return fslsmMatrixCache.computeIfAbsent(clusterKey, key -> {
            TransitionMatrix matrix = FslsmMatrices.getMatrix(key, random);
            if (matrix == null) {
                throw new IllegalStateException("No FSLSM matrix defined for cluster: " + key);
            }
            return matrix;
        });
    }

    /**
     * Sample the first LO type from start distribution.
     * Prefer T (Theory) as first LO (60% probability).
     */
    private String sampleStartState() {
        Distribution startDist = Distribution.of(START_DISTRIBUTION, random);
        return startDist.sample();
    }

    /**
     * Index available LOs by type for fast lookup.
     */
    private Map<String, List<LearningObject>> indexByType(List<LearningObject> los) {
        Map<String, List<LearningObject>> index = new HashMap<>();
        for (LearningObject lo : los) {
            index.computeIfAbsent(lo.getType(), k -> new ArrayList<>()).add(lo);
        }
        return index;
    }

    /**
     * Sample a random LO of the given type from available pool.
     * Returns null if no LOs of this type are available.
     */
    private LearningObject sampleLO(String type, Map<String, List<LearningObject>> losByType) {
        List<LearningObject> candidates = losByType.get(type);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Generate multiple sequences for a learner (for multiple sessions).
     * Allows revisits: each sequence independently samples from available LOs.
     *
     * @param learner Learner with FSLSM profile
     * @param availableLOs Learning objects to select from
     * @param adherence Phase-specific adherence coefficient
     * @param numSequences Number of sequences to generate
     * @param minLength Minimum sequence length (Poisson parameter)
     * @param maxLength Maximum sequence length (hard cap)
     * @return List of sequences (each sequence is a list of LO IDs)
     */
    public List<List<Long>> generateMultipleSequences(Learner learner, List<LearningObject> availableLOs,
                                                      double adherence, int numSequences,
                                                      int minLength, int maxLength) {
        List<List<Long>> sequences = new ArrayList<>();

        for (int i = 0; i < numSequences; i++) {
            // Sample length from Poisson-like distribution
            int length = sampleSequenceLength(minLength, maxLength);
            List<Long> sequence = generateSequence(learner, availableLOs, adherence, length);
            sequences.add(sequence);
        }

        return sequences;
    }

    /**
     * Sample sequence length from Poisson-like distribution.
     * Mean = minLength, capped at maxLength.
     */
    private int sampleSequenceLength(int minLength, int maxLength) {
        // Simple approximation: truncated normal distribution
        double mean = minLength;
        double stdDev = minLength * 0.3; // 30% variation

        double length = mean + random.nextGaussian() * stdDev;
        length = Math.max(minLength, Math.min(maxLength, length));

        return (int) Math.round(length);
    }
}
