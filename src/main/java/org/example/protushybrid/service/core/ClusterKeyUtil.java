package org.example.protushybrid.service.core;

import org.example.protushybrid.domain.core.Learner;

/**
 * Utility for computing FSLSM cluster keys from learner profiles.
 *
 * Format: {ACTIVE|REFLECTIVE}_{SENSING|INTUITIVE}_{VISUAL|VERBAL}_{SEQUENTIAL|GLOBAL}
 * Example: "ACTIVE_SENSING_VISUAL_SEQUENTIAL"
 *
 * This format matches:
 * - Database schema (fslsm_profiles.cluster_key generated column)
 * - FslsmProfile entity
 * - All data generation and matrix-based sequence generation
 *
 * IMPORTANT: All cluster key generation MUST use this utility to ensure consistency.
 */
public final class ClusterKeyUtil {
    /**
     * Compute FSLSM cluster key from learner's style scores.
     *
     * @param learner Learner with FSLSM scores (range [-11, +11])
     * @return Cluster key in format: "{ACTIVE|REFLECTIVE}_{SENSING|INTUITIVE}_{VISUAL|VERBAL}_{SEQUENTIAL|GLOBAL}"
     */
    public static String forLearner(Learner learner) {
        if (learner == null) {
            throw new IllegalArgumentException("Learner cannot be null");
        }

        String activeReflective = learner.getStyleActiveReflective() >= 0 ? "ACTIVE" : "REFLECTIVE";
        String sensingIntuitive = learner.getStyleSensingIntuitive() >= 0 ? "SENSING" : "INTUITIVE";
        String visualVerbal = learner.getStyleVisualVerbal() >= 0 ? "VISUAL" : "VERBAL";
        String sequentialGlobal = learner.getStyleSequentialGlobal() >= 0 ? "SEQUENTIAL" : "GLOBAL";

        return String.format("%s_%s_%s_%s",
            activeReflective, sensingIntuitive, visualVerbal, sequentialGlobal);
    }
}
