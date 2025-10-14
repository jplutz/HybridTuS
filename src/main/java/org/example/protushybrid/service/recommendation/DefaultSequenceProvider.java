package org.example.protushybrid.service.recommendation;

import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.core.LearningObject;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.core.LearningObjectRepository;
import org.example.protushybrid.service.core.ClusterKeyUtil;
import org.example.protushybrid.service.recommendation.generation.FslsmMatrices;
import org.example.protushybrid.service.recommendation.generation.TransitionMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Provides FSLSM-based default recommendations when no mined patterns exist (cold-start).
 *
 * Cold-Start Strategy:
 * 1. Compute learner's FSLSM cluster from their learning style profile
 * 2. Load T_fslsm matrix for that cluster
 * 3. Use current LO type as state, sample next type from transition probabilities
 * 4. Select an available LO of the recommended type
 *
 * This approach ensures pedagogically sound recommendations even with zero historical data.
 * As mining accumulates patterns, the system transitions from FSLSM defaults to data-driven.
 */
@Service
public class DefaultSequenceProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultSequenceProvider.class);

    private final LearnerRepository learnerRepo;
    private final LearningObjectRepository loRepo;
    private final Random random = new Random(123); // Fixed seed for reproducible evaluation

    // Cache for FSLSM matrices (thread-safe)
    private final Map<String, TransitionMatrix> matrixCache = new HashMap<>();

    // Start state distribution: prefer T (Theory) when no history
    // Aligned with 5 core types supported by FSLSM matrices
    private static final Map<String, Double> START_DISTRIBUTION = Map.of(
        "T", 0.60,    // Theory (most common starting point)
        "E", 0.20,    // Example (concrete learners may start here)
        "A", 0.10,    // Activity (active learners jump to practice)
        "F", 0.10,    // Figure (visual learners prefer diagrams)
        "Test", 0.0   // Test (never start with assessment)
    );

    public DefaultSequenceProvider(LearnerRepository learnerRepo,
                                   LearningObjectRepository loRepo) {
        this.learnerRepo = learnerRepo;
        this.loRepo = loRepo;
    }

    /**
     * Get FSLSM-based recommendation when no mined patterns exist.
     *
     * @param learnerId Learner ID (to determine FSLSM cluster)
     * @param conceptId Concept ID (to filter available LOs)
     * @param currentSuffix Current LO type sequence (may be empty for start state)
     * @param availableTypes Set of LO types that are still available (not yet visited)
     * @return Recommended LO ID, or null if no suitable LO available
     */
    public Long getDefaultRecommendation(Long learnerId, Long conceptId,
                                         List<String> currentSuffix,
                                         Set<String> availableTypes) {
        // Get learner and compute FSLSM cluster
        Learner learner = learnerRepo.findById(learnerId).orElse(null);
        if (learner == null) {
            log.warn("Learner {} not found for FSLSM cold-start", learnerId);
            return null;
        }

        String clusterKey = computeClusterKey(learner);

        // Get or load T_fslsm matrix for this cluster
        TransitionMatrix fslsmMatrix = getMatrix(clusterKey);
        if (fslsmMatrix == null) {
            log.warn("No FSLSM matrix found for cluster: {}", clusterKey);
            return null;
        }

        // Determine next LO type using FSLSM matrix
        String nextType;
        if (currentSuffix.isEmpty()) {
            // Start state: sample from start distribution
            nextType = sampleStartState();
            log.info("FSLSM cold-start (start state): sampled type {} for cluster {}", nextType, clusterKey);
        } else {
            // Use last LO type as current state, sample next from matrix
            String currentType = currentSuffix.get(currentSuffix.size() - 1);
            try {
                nextType = fslsmMatrix.sampleNext(currentType);
                log.info("FSLSM cold-start: sampled {} -> {} for cluster {}", currentType, nextType, clusterKey);
            } catch (IllegalStateException e) {
                // Current type not in matrix (e.g., legacy types), fall back to start
                log.warn("Type {} not in FSLSM matrix for {}, using start state", currentType, clusterKey);
                nextType = sampleStartState();
            }
        }

        // Filter to only available types
        if (!availableTypes.contains(nextType)) {
            log.info("FSLSM recommended type {} not in availableTypes: {}. Finding alternative...", nextType, availableTypes);
            // Recommended type not available, try to find best alternative
            nextType = selectAlternativeType(nextType, availableTypes, fslsmMatrix);
            if (nextType == null) {
                log.warn("FSLSM cold-start: no available types for learner {} concept {} (availableTypes: {})",
                         learnerId, conceptId, availableTypes);
                return null;
            }
            log.info("Selected alternative type: {}", nextType);
        }

        // Select a random LO of the recommended type
        log.info("[COLD-START] Querying database: conceptId={}, nextType='{}'", conceptId, nextType);
        List<LearningObject> candidates = loRepo.findByConceptIdAndType(conceptId, nextType);
        log.info("[COLD-START] Found {} candidates for type '{}'", candidates.size(), nextType);

        if (!candidates.isEmpty()) {
            for (LearningObject lo : candidates) {
                log.info("[COLD-START]   Candidate: id={}, type='{}', title='{}'",
                        lo.getId(), lo.getType(), lo.getTitle());
            }
        }

        if (candidates.isEmpty()) {
            log.warn("No LOs of type {} found for concept {}", nextType, conceptId);
            return null;
        }

        LearningObject recommendedLo = candidates.get(random.nextInt(candidates.size()));
        log.info("[COLD-START] Selected LO: id={}, type='{}', title='{}' (expected type: {})",
                 recommendedLo.getId(), recommendedLo.getType(), recommendedLo.getTitle(), nextType);

        return recommendedLo.getId();
    }

    /**
     * Sample the first LO type from start distribution when no history exists.
     */
    private String sampleStartState() {
        double r = random.nextDouble();
        double cumulative = 0.0;

        for (Map.Entry<String, Double> entry : START_DISTRIBUTION.entrySet()) {
            cumulative += entry.getValue();
            if (r < cumulative) {
                return entry.getKey();
            }
        }

        return "T"; // Fallback to Theory
    }

    /**
     * Select alternative type when recommended type is not available.
     * Strategy: Sample from current state distribution, pick next most likely available type.
     */
    private String selectAlternativeType(String recommendedType, Set<String> availableTypes,
                                         TransitionMatrix matrix) {
        if (availableTypes.isEmpty()) {
            return null;
        }

        // If only one type available, return it
        if (availableTypes.size() == 1) {
            return availableTypes.iterator().next();
        }

        // Try sampling a few times to find an available type
        List<String> availableList = new ArrayList<>(availableTypes);
        return availableList.get(random.nextInt(availableList.size()));
    }

    /**
     * Get or load FSLSM matrix for a cluster (with caching).
     */
    private synchronized TransitionMatrix getMatrix(String clusterKey) {
        return matrixCache.computeIfAbsent(clusterKey, key ->
            FslsmMatrices.getMatrix(key, random)
        );
    }

    /**
     * Compute FSLSM cluster key from learner's style scores.
     * Delegates to ClusterKeyUtil for consistency.
     */
    private String computeClusterKey(Learner learner) {
        return ClusterKeyUtil.forLearner(learner);
    }

    /**
     * Check if cold-start is needed for a learner-concept pair.
     * Cold-start is needed when no mined patterns exist for the learner's cluster.
     *
     * @param clusterKey FSLSM cluster key
     * @param conceptId Concept ID
     * @return true if cold-start should be used
     */
    public boolean isColdStart(String clusterKey, Long conceptId) {
        // This would typically check if mined_supports table has patterns for this cluster+concept
        // For now, simplified to always allow cold-start as fallback
        return true;
    }
}
