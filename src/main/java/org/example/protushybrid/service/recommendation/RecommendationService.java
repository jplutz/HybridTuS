package org.example.protushybrid.service.recommendation;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.core.LearningObject;
import org.example.protushybrid.domain.core.LearningSession;
import org.example.protushybrid.domain.mining.MinedSupport;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.core.LearningObjectRepository;
import org.example.protushybrid.repository.core.LearningSessionRepository;
import org.example.protushybrid.repository.mining.MinedSupportRepository;
import org.example.protushybrid.service.core.ClusterKeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Simplified recommendation engine using pure group-based mining with FSLSM cold-start.
 *
 * Algorithm:
 * 1. Query mined_supports for (cluster_key, concept_id, suffix) patterns
 * 2. Use suffix backoff (3 → 2 → 1 tokens)
 * 3. Fallback to GLOBAL cluster if no cluster-specific patterns
 * 4. Fallback to FSLSM cold-start defaults if no mined data exists
 *
 * Individual user mining has been REMOVED - all recommendations are based on
 * group patterns from completed learning sessions. Cold-start uses FSLSM transition matrices
 * to provide pedagogically sound recommendations even with zero historical data.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    // Suffix lengths to try (backoff)
    private static final int MAX_SUFFIX_LENGTH = 3;
    private static final int MIN_SUFFIX_LENGTH = 1;

    private final LearnerRepository learnerRepo;
    private final ConceptRepository conceptRepo;
    private final LearningObjectRepository loRepo;
    private final MinedSupportRepository minedRepo;
    private final LearningSessionRepository sessionRepo;
    private final DefaultSequenceProvider coldStartProvider;

    public RecommendationService(LearnerRepository learnerRepo,
                                 ConceptRepository conceptRepo,
                                 LearningObjectRepository loRepo,
                                 MinedSupportRepository minedRepo,
                                 LearningSessionRepository sessionRepo,
                                 DefaultSequenceProvider coldStartProvider) {
        this.learnerRepo = learnerRepo;
        this.conceptRepo = conceptRepo;
        this.loRepo = loRepo;
        this.minedRepo = minedRepo;
        this.sessionRepo = sessionRepo;
        this.coldStartProvider = coldStartProvider;
    }

    /**
     * Get recommendation as LO ID (for evaluation).
     *
     * @param learnerId Learner ID
     * @param conceptId Target concept ID
     * @param suffix Current LO type sequence
     * @param availableTypes Set of LO types that haven't been visited
     * @return Recommended LO ID, or null if none available
     */
    @Transactional(readOnly = true)
    public Long getRecommendation(Long learnerId, Long conceptId, List<String> suffix,
                                  Set<String> availableTypes) {
        Learner learner = learnerRepo.findById(learnerId).orElse(null);
        if (learner == null) return null;

        String clusterKey = ClusterKeyUtil.forLearner(learner);

        // Try mined patterns first
        String recommendedType = queryMinedPatterns(clusterKey, conceptId, suffix, availableTypes);

        if (recommendedType != null) {
            // Select specific LO of the recommended type
            Concept concept = conceptRepo.findById(conceptId).orElse(null);
            if (concept == null) return null;

            Optional<LearningObject> lo = selectLearningObject(concept, recommendedType);
            return lo.map(LearningObject::getId).orElse(null);
        }

        // Fallback to FSLSM cold-start
        return coldStartProvider.getDefaultRecommendation(learnerId, conceptId, suffix, availableTypes);
    }

    /**
     * Get detailed trace of recommendation process for console logging.
     * Shows FSLSM profile, content prefixes, and step-by-step reasoning.
     *
     * @param learnerId Learner ID
     * @param conceptId Target concept ID
     * @param limit Max number of patterns to show
     * @return List of trace messages
     */
    @Transactional(readOnly = true)
    public List<String> getDetailedTrace(Long learnerId, Long conceptId, int limit) {
        List<String> trace = new ArrayList<>();

        Learner learner = learnerRepo.findById(learnerId).orElse(null);
        if (learner == null) {
            trace.add("[ERROR] Learner not found: " + learnerId);
            return trace;
        }

        Concept concept = conceptRepo.findById(conceptId).orElse(null);
        if (concept == null) {
            trace.add("[ERROR] Concept not found: " + conceptId);
            return trace;
        }

        trace.add("[CONCEPT INFORMATION]");
        trace.add(String.format("   Concept ID: %d", conceptId));
        trace.add(String.format("   Concept Name: %s", concept.getName()));
        if (concept.getCourse() != null) {
            trace.add(String.format("   Course: %s (ID: %d)", concept.getCourse().getTitle(), concept.getCourse().getId()));
        }
        trace.add("");

        // 1. Show learner FSLSM profile
        String clusterKey = ClusterKeyUtil.forLearner(learner);
        trace.add("[LEARNER PROFILE - FSLSM]");
        trace.add(String.format("   Active/Reflective: %.1f %s",
                learner.getStyleActiveReflective(),
                learner.getStyleActiveReflective() >= 0 ? "(ACTIVE)" : "(REFLECTIVE)"));
        trace.add(String.format("   Sensing/Intuitive: %.1f %s",
                learner.getStyleSensingIntuitive(),
                learner.getStyleSensingIntuitive() >= 0 ? "(SENSING)" : "(INTUITIVE)"));
        trace.add(String.format("   Visual/Verbal: %.1f %s",
                learner.getStyleVisualVerbal(),
                learner.getStyleVisualVerbal() >= 0 ? "(VISUAL)" : "(VERBAL)"));
        trace.add(String.format("   Sequential/Global: %.1f %s",
                learner.getStyleSequentialGlobal(),
                learner.getStyleSequentialGlobal() >= 0 ? "(SEQUENTIAL)" : "(GLOBAL)"));
        trace.add(String.format("   Cluster Key: %s", clusterKey));
        trace.add("");

        // 2. Extract current suffix
        List<String> suffix = extractSuffix(learnerId, conceptId);
        trace.add("[CURRENT LEARNING SEQUENCE]");
        if (suffix.isEmpty()) {
            trace.add("   (No previous learning objects completed)");
        } else {
            trace.add("   Recent completions: " + String.join(" -> ", suffix));
        }
        trace.add("");

        // 3. Get available LO types and show content prefixes
        Set<String> allAvailableTypes = new HashSet<>();
        var allLOs = loRepo.findByConcept(concept);
        trace.add("[AVAILABLE LEARNING OBJECTS]");
        for (var lo : allLOs) {
            if (lo.getType() != null) {
                String canonicalType = normalizeToCanonical(lo.getType());
                allAvailableTypes.add(canonicalType);

                // Show content URI prefix
                String prefix = lo.getSourceUri() != null && lo.getSourceUri().contains(":")
                        ? lo.getSourceUri().substring(0, lo.getSourceUri().indexOf(":") + 1)
                        : "(no prefix)";
                String title = lo.getTitle() != null ? lo.getTitle() : "(auto-generated title)";
                trace.add(String.format("   Type %s [%s]: %s", canonicalType, prefix, title));
            }
        }
        if (allAvailableTypes.isEmpty()) {
            trace.add("   (No learning objects available)");
        }
        trace.add("");

        // 3b. Apply filtering logic to match recommendNext behavior
        Set<String> availableTypes = new HashSet<>(allAvailableTypes);
        Set<String> completedTypes = new HashSet<>(suffix);
        availableTypes.removeAll(completedTypes);

        trace.add("[FILTERING COMPLETED TYPES]");
        if (!completedTypes.isEmpty()) {
            trace.add(String.format("   Completed types in current session: %s", completedTypes));
            trace.add(String.format("   Types available for recommendation: %s", availableTypes));
        } else {
            trace.add("   No completed types yet - all types available");
        }

        if (availableTypes.isEmpty() && !suffix.isEmpty()) {
            trace.add("   [INFO] All types completed - allowing revisits");
            availableTypes = new HashSet<>(allAvailableTypes);

            if (!suffix.isEmpty()) {
                String mostRecent = suffix.get(suffix.size() - 1);
                availableTypes.remove(mostRecent);
                trace.add(String.format("   [INFO] Excluding most recent type: %s", mostRecent));

                if (availableTypes.isEmpty()) {
                    availableTypes.add(mostRecent);
                    trace.add("   [INFO] Only one type exists - including it anyway");
                }
            }
            trace.add(String.format("   Final available types: %s", availableTypes));
        }
        trace.add("");

        // 4. Try mined patterns
        trace.add("[SEARCHING MINED PATTERNS]");
        String recommendedType = queryMinedPatternsWithTrace(clusterKey, conceptId, suffix, availableTypes, trace, limit);

        // 5. Show recommendation result
        trace.add("");
        if (recommendedType != null) {
            trace.add("[RECOMMENDATION] " + recommendedType);

            // Show which LO will be selected
            Optional<LearningObject> selectedLO = selectLearningObject(concept, recommendedType);
            if (selectedLO.isPresent()) {
                LearningObject lo = selectedLO.get();
                String uri = lo.getSourceUri() != null ? lo.getSourceUri() : "(no URI)";
                trace.add(String.format("   Content source: %s", uri));
                if (uri.startsWith("classpath:")) {
                    trace.add(String.format("   Resource path: %s", uri.substring("classpath:".length())));
                }
            }
        } else {
            trace.add("[FALLBACK] No mined patterns found - using FSLSM cold-start");
            trace.add("   Strategy: FSLSM transition matrix samples next LO type probabilistically");
            trace.add(String.format("   Learner cluster: %s", clusterKey));

            if (suffix.isEmpty()) {
                trace.add("   Start state - samples from fixed distribution:");
                trace.add("      T (Theory): 60%, E (Example): 20%, A (Activity): 10%, F (Figure): 10%");
            } else {
                trace.add(String.format("   Current sequence: %s", String.join(" -> ", suffix)));
                trace.add(String.format("   Last LO type: %s (used as current state in transition matrix)", suffix.get(suffix.size() - 1)));
                trace.add("   Transition matrix samples next type based on learner's FSLSM profile");
            }

            trace.add(String.format("   Available LO types for selection: %s", availableTypes));
            trace.add("");
            trace.add("   [INFO] Cold-start uses probabilistic sampling - recommendation logged in server logs");
            trace.add("   Check backend logs for actual sampled type and selected LO details");
        }

        return trace;
    }

    /**
     * Query mined patterns with detailed trace logging.
     */
    private String queryMinedPatternsWithTrace(String clusterKey, Long conceptId, List<String> suffix,
                                               Set<String> availableTypes, List<String> trace, int limit) {
        // Check if any patterns exist for this cluster+concept at all
        var allPatternsForCluster = minedRepo.findAll().stream()
                .filter(p -> p.getClusterKey().equals(clusterKey) && p.getConceptId().equals(conceptId))
                .toList();

        trace.add(String.format("   Total patterns in DB for cluster=%s, concept=%d: %d",
                clusterKey, conceptId, allPatternsForCluster.size()));

        if (allPatternsForCluster.isEmpty()) {
            trace.add("   [WARNING] No mined patterns exist for this cluster+concept combination");
            trace.add("   Check that:");
            trace.add("      1. Data generation was run for this concept");
            trace.add("      2. Mining was executed after data generation");
            trace.add("      3. Concept ID matches the synthetic data concepts");
        }

        // Special case: empty suffix (start state)
        if (suffix.isEmpty()) {
            trace.add("   Trying suffix \"\" (start state)...");

            // Try cluster-specific start-state patterns
            var clusterPatterns = minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                    clusterKey, conceptId, "");

            if (!clusterPatterns.isEmpty()) {
                trace.add(String.format("      [+] Found %d cluster-specific start-state patterns for %s:",
                        Math.min(clusterPatterns.size(), limit), clusterKey));
                int shown = 0;
                for (MinedSupport pattern : clusterPatterns) {
                    if (shown++ >= limit) break;
                    boolean available = availableTypes.contains(pattern.getNextType());
                    String status = available ? "[AVAILABLE]" : "[not available]";
                    trace.add(String.format("         (start) -> %s (n_support: %d, support: %.2f, learners: %d) %s",
                            pattern.getNextType(),
                            pattern.getNSupport(), pattern.getSupport(), pattern.getNLearners(), status));

                    if (available) {
                        trace.add(String.format("      [SELECTED] %s", pattern.getNextType()));
                        return pattern.getNextType();
                    }
                }
            } else {
                trace.add("      [-] No cluster-specific start-state patterns found");
            }

            // Fallback to GLOBAL cluster start-state
            var globalPatterns = minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                    "GLOBAL", conceptId, "");

            if (!globalPatterns.isEmpty()) {
                trace.add(String.format("      [+] Found %d GLOBAL start-state patterns:",
                        Math.min(globalPatterns.size(), limit)));
                int shown = 0;
                for (MinedSupport pattern : globalPatterns) {
                    if (shown++ >= limit) break;
                    boolean available = availableTypes.contains(pattern.getNextType());
                    String status = available ? "[AVAILABLE]" : "[not available]";
                    trace.add(String.format("         (start) -> %s (n_support: %d, support: %.2f, learners: %d) %s",
                            pattern.getNextType(),
                            pattern.getNSupport(), pattern.getSupport(), pattern.getNLearners(), status));

                    if (available) {
                        trace.add(String.format("      [SELECTED] %s", pattern.getNextType()));
                        return pattern.getNextType();
                    }
                }
            } else {
                trace.add("      [-] No GLOBAL start-state patterns found");
            }

            return null;
        }

        // Try suffix lengths MAX → ... → MIN (backoff)
        for (int len = Math.min(suffix.size(), MAX_SUFFIX_LENGTH); len >= MIN_SUFFIX_LENGTH; len--) {
            List<String> trySuffix = suffix.subList(suffix.size() - len, suffix.size());
            String suffixStr = String.join(">", trySuffix);

            trace.add(String.format("   Trying suffix '%s' (length %d)...", suffixStr, len));

            // Try cluster-specific patterns
            var clusterPatterns = minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                    clusterKey, conceptId, suffixStr);

            if (!clusterPatterns.isEmpty()) {
                trace.add(String.format("      [+] Found %d cluster-specific patterns for %s:",
                        Math.min(clusterPatterns.size(), limit), clusterKey));
                int shown = 0;
                for (MinedSupport pattern : clusterPatterns) {
                    if (shown++ >= limit) break;
                    boolean available = availableTypes.contains(pattern.getNextType());
                    String status = available ? "[AVAILABLE]" : "[not available]";
                    trace.add(String.format("         %s -> %s (n_support: %d, support: %.2f, learners: %d) %s",
                            pattern.getSuffix(), pattern.getNextType(),
                            pattern.getNSupport(), pattern.getSupport(), pattern.getNLearners(), status));

                    if (available) {
                        trace.add(String.format("      [SELECTED] %s", pattern.getNextType()));
                        return pattern.getNextType();
                    }
                }
            } else {
                trace.add("      [-] No cluster-specific patterns found");
            }

            // Fallback to GLOBAL cluster
            var globalPatterns = minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                    "GLOBAL", conceptId, suffixStr);

            if (!globalPatterns.isEmpty()) {
                trace.add(String.format("      [+] Found %d GLOBAL patterns:",
                        Math.min(globalPatterns.size(), limit)));
                int shown = 0;
                for (MinedSupport pattern : globalPatterns) {
                    if (shown++ >= limit) break;
                    boolean available = availableTypes.contains(pattern.getNextType());
                    String status = available ? "[AVAILABLE]" : "[not available]";
                    trace.add(String.format("         %s -> %s (n_support: %d, support: %.2f, learners: %d) %s",
                            pattern.getSuffix(), pattern.getNextType(),
                            pattern.getNSupport(), pattern.getSupport(), pattern.getNLearners(), status));

                    if (available) {
                        trace.add(String.format("      [SELECTED] %s", pattern.getNextType()));
                        return pattern.getNextType();
                    }
                }
            } else {
                trace.add("      [-] No GLOBAL patterns found");
            }
        }

        return null;
    }

    /**
     * Recommend next learning object for a learner in a concept.
     *
     * @param learnerId Learner ID
     * @param conceptId Target concept ID
     * @return Recommended learning object, or empty if none available
     */
    @Transactional(readOnly = true)
    public Optional<LearningObject> recommendNext(Long learnerId, Long conceptId) {
        Learner learner = learnerRepo.findById(learnerId).orElse(null);
        if (learner == null) return Optional.empty();

        Concept concept = conceptRepo.findById(conceptId).orElse(null);
        if (concept == null) return Optional.empty();

        // Get learner's cluster key
        String clusterKey = ClusterKeyUtil.forLearner(learner);

        // Extract recent suffix from learner's current session
        List<String> suffix = extractSuffix(learnerId, conceptId);

        // Get all available LO types for this concept (canonical forms)
        Set<String> allAvailableTypes = new HashSet<>();
        var allLOs = loRepo.findByConcept(concept);
        for (var lo : allLOs) {
            if (lo.getType() != null) {
                allAvailableTypes.add(normalizeToCanonical(lo.getType()));
            }
        }

        if (allAvailableTypes.isEmpty()) {
            log.debug("No available LO types for concept {}", conceptId);
            return Optional.empty();
        }

        // Filter out types that have been completed in the current session to prevent immediate re-recommendation
        Set<String> availableTypes = new HashSet<>(allAvailableTypes);
        Set<String> completedTypes = new HashSet<>(suffix);
        availableTypes.removeAll(completedTypes);

        // If all types have been completed, allow revisits but prefer least recently used
        if (availableTypes.isEmpty() && !suffix.isEmpty()) {
            log.info("All LO types completed for learner {} in concept {}. Allowing revisits.", learnerId, conceptId);
            availableTypes = new HashSet<>(allAvailableTypes);

            // Optional: Remove only the most recently completed type to provide variety
            if (!suffix.isEmpty()) {
                String mostRecent = suffix.get(suffix.size() - 1);
                availableTypes.remove(mostRecent);
                log.info("Excluding most recently completed type: {}", mostRecent);

                // If that was the only type, allow it anyway
                if (availableTypes.isEmpty()) {
                    availableTypes.add(mostRecent);
                }
            }
        }

        // Try to get recommendation from mined patterns
        String recommendedType = queryMinedPatterns(clusterKey, conceptId, suffix, availableTypes);

        if (recommendedType == null) {
            log.info("No mined pattern found for learner {} in concept {}. Using FSLSM cold-start fallback.",
                    learnerId, conceptId);

            // Fallback to FSLSM-based cold-start recommendation
            Long coldStartLoId = coldStartProvider.getDefaultRecommendation(
                learnerId, conceptId, suffix, availableTypes
            );

            if (coldStartLoId != null) {
                return loRepo.findById(coldStartLoId);
            } else {
                // Ultimate fallback: return any unvisited LO
                log.warn("FSLSM cold-start also failed for learner {} concept {}", learnerId, conceptId);
                return selectAnyUnvisited(concept, suffix);
            }
        }

        // Select specific LO of the recommended type
        return selectLearningObject(concept, recommendedType);
    }

    /**
     * Extract recent LO types from learner's current active session.
     * Returns up to MAX_SUFFIX_LENGTH most recent types in canonical form.
     * Uses completionSequence (done + revisited) instead of visitedLoIds (navigation).
     */
    private List<String> extractSuffix(Long learnerId, Long conceptId) {
        var sessions = sessionRepo.findByLearnerIdOrderByCreatedAtDesc(learnerId);

        // Find most recent session for this concept
        for (LearningSession session : sessions) {
            if (session.getConcept().getId().equals(conceptId) && !session.isCompleted()) {
                // Extract LO types from completion sequence (canonical form)
                List<String> types = new ArrayList<>();
                for (Long loId : session.getCompletionSequence()) {
                    LearningObject lo = loRepo.findById(loId).orElse(null);
                    if (lo != null && lo.getType() != null) {
                        types.add(normalizeToCanonical(lo.getType()));
                    }
                }

                // Return last MAX_SUFFIX_LENGTH types
                int start = Math.max(0, types.size() - MAX_SUFFIX_LENGTH);
                return types.subList(start, types.size());
            }
        }

        return Collections.emptyList();
    }

    /**
     * Normalize LO type to canonical form.
     * Single-letter codes: uppercase (T, E, A, F, etc.)
     * Test: preserve as "Test" (canonical mixed-case form)
     * Legacy: convert to canonical (THEORY→T, TEST→Test, etc.)
     */
    private String normalizeToCanonical(String type) {
        if (type == null) return null;

        // Legacy → Canonical mappings
        return switch (type.toUpperCase()) {
            case "THEORY" -> "T";
            case "EXAMPLE" -> "E";
            case "ACTIVITY" -> "A";
            case "FIGURE" -> "F";
            case "TEST" -> "Test";  // Legacy TEST → canonical Test
            case "HINT" -> "X";
            // Already canonical single-letter forms
            case "T", "E", "A", "F", "X", "R", "S", "O" -> type.toUpperCase();
            default -> type.length() == 1 ? type.toUpperCase() : type;  // Single letter → uppercase, else preserve (handles "Test")
        };
    }

    /**
     * Query mined patterns with suffix backoff.
     * Tries cluster-specific patterns first, then GLOBAL fallback.
     * Handles empty suffix (start state) as special case with suffix="".
     */
    private String queryMinedPatterns(String clusterKey, Long conceptId, List<String> suffix,
                                      Set<String> availableTypes) {
        // Special case: empty suffix (start state)
        if (suffix.isEmpty()) {
            log.info("Empty suffix (start state) - querying patterns with suffix=\"\"");

            // Try cluster-specific start-state patterns
            String recommendation = queryPartition(clusterKey, conceptId, "", availableTypes);
            if (recommendation != null) {
                log.info("Recommended {} for cluster={} concept={} suffix=\"\" (start state)",
                        recommendation, clusterKey, conceptId);
                return recommendation;
            }

            // Fallback to GLOBAL cluster start-state
            recommendation = queryPartition("GLOBAL", conceptId, "", availableTypes);
            if (recommendation != null) {
                log.info("Recommended {} for GLOBAL concept={} suffix=\"\" (start state)",
                        recommendation, conceptId);
                return recommendation;
            }

            return null;
        }

        // Try suffix lengths MAX → ... → MIN (backoff)
        for (int len = Math.min(suffix.size(), MAX_SUFFIX_LENGTH); len >= MIN_SUFFIX_LENGTH; len--) {
            List<String> trySuffix = suffix.subList(suffix.size() - len, suffix.size());
            String suffixStr = String.join(">", trySuffix);

            // Try cluster-specific patterns
            String recommendation = queryPartition(clusterKey, conceptId, suffixStr, availableTypes);
            if (recommendation != null) {
                log.info("Recommended {} for cluster={} concept={} suffix={}",
                        recommendation, clusterKey, conceptId, suffixStr);
                return recommendation;
            }

            // Fallback to GLOBAL cluster
            recommendation = queryPartition("GLOBAL", conceptId, suffixStr, availableTypes);
            if (recommendation != null) {
                log.info("Recommended {} for GLOBAL concept={} suffix={}",
                        recommendation, conceptId, suffixStr);
                return recommendation;
            }
        }

        return null;
    }

    /**
     * Query a single partition for patterns matching the suffix.
     */
    private String queryPartition(String clusterKey, Long conceptId, String suffix, Set<String> availableTypes) {
        var patterns = minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                clusterKey, conceptId, suffix);

        // Return first pattern whose next_type is available
        for (MinedSupport pattern : patterns) {
            if (availableTypes.contains(pattern.getNextType())) {
                return pattern.getNextType();
            }
        }

        return null;
    }

    /**
     * Select specific LO instance of the chosen type.
     * Returns newest version of available LOs.
     * Handles canonical types (T, E, A, F, Test) and legacy types (THEORY, EXAMPLE, etc.)
     */
    public Optional<LearningObject> selectLearningObject(Concept concept, String loType) {
        log.debug("[selectLearningObject] Searching for type='{}' in concept={}", loType, concept.getId());

        // Try exact match first (handles canonical "Test" and single letters like "T")
        var candidates = loRepo.findByConceptAndType(concept, loType);
        log.debug("[selectLearningObject] Exact match '{}': found {} candidates", loType, candidates.size());

        if (candidates.isEmpty()) {
            // Try uppercase (for case-insensitive single letters: "t" → "T")
            candidates = loRepo.findByConceptAndType(concept, loType.toUpperCase());
            log.debug("[selectLearningObject] Uppercase match '{}': found {} candidates", loType.toUpperCase(), candidates.size());
        }

        if (candidates.isEmpty()) {
            // Try lowercase (for legacy uppercase: "TEST" → "test")
            candidates = loRepo.findByConceptAndType(concept, loType.toLowerCase());
            log.debug("[selectLearningObject] Lowercase match '{}': found {} candidates", loType.toLowerCase(), candidates.size());
        }

        // Sort by version descending (newest first)
        candidates.sort(Comparator.comparingInt(LearningObject::getVersion).reversed());

        if (!candidates.isEmpty()) {
            LearningObject selected = candidates.get(0);
            log.info("[selectLearningObject] Selected LO: id={}, type='{}', title='{}'",
                    selected.getId(), selected.getType(), selected.getTitle());
            return Optional.of(selected);
        }

        log.warn("[selectLearningObject] No LO found for type='{}' in concept={}", loType, concept.getId());
        return Optional.empty();
    }

    /**
     * Fallback when no mined patterns exist: return any unvisited LO.
     */
    private Optional<LearningObject> selectAnyUnvisited(Concept concept, List<String> visitedTypes) {
        var allLOs = loRepo.findByConcept(concept);

        Set<String> visited = new HashSet<>();
        for (String type : visitedTypes) {
            visited.add(type.toUpperCase());
        }

        // Find first LO not in visited types
        for (LearningObject lo : allLOs) {
            if (lo.getType() != null && !visited.contains(lo.getType().toUpperCase())) {
                return Optional.of(lo);
            }
        }

        // All visited, return first
        return allLOs.isEmpty() ? Optional.empty() : Optional.of(allLOs.get(0));
    }
}
