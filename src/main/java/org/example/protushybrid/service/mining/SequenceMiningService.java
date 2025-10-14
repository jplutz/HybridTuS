package org.example.protushybrid.service.mining;

import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.core.LearningSession;
import org.example.protushybrid.domain.mining.MinedSupport;
import org.example.protushybrid.domain.core.LearningObject;
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
import java.util.stream.Collectors;

/**
 * Session-based sequence mining for group learning patterns.
 *
 * Policy:
 * - Partitions by (cluster_key, concept_id, learner_id)
 * - Orders events by timestamp within sessions
 * - Collapses consecutive duplicates
 * - Excludes PRACTICE and NAVIGATION events
 * - Aggregates patterns across learners in each cluster
 * - Tracks distinct learner support (n_learners) and pattern frequency (n_support)
 * - Stores results in app.mined_supports with unique (cluster_key, concept_id, suffix, next_type)
 *
 * Thresholds:
 * - MIN_EVENTS = 30 (minimum interactions in partition)
 * - MIN_LEARNERS = 5 (minimum distinct learners)
 * - MIN_SUPPORT = 0.25 (minimum relative frequency)
 */
@Service
public class SequenceMiningService {

    private static final Logger log = LoggerFactory.getLogger(SequenceMiningService.class);

    // Mining thresholds
    private static final int MIN_SUFFIX = 1;
    private static final int MAX_SUFFIX = 3;
    private static final double MIN_SUPPORT = 0.25; // 25%
    private static final int MIN_LEARNERS = 5;
    private static final int MIN_EVENTS = 30;

    // LO types to exclude from mining (PRACTICE/NAVIGATION events)
    private static final Set<String> EXCLUDE = Set.of("PRACTICE", "NAVIGATION");

    private final LearningSessionRepository sessionRepo;
    private final LearnerRepository learnerRepo;
    private final ConceptRepository conceptRepo;
    private final LearningObjectRepository loRepo;
    private final MinedSupportRepository minedRepo;

    public SequenceMiningService(LearningSessionRepository sessionRepo,
                                 LearnerRepository learnerRepo,
                                 ConceptRepository conceptRepo,
                                 LearningObjectRepository loRepo,
                                 MinedSupportRepository minedRepo) {
        this.sessionRepo = sessionRepo;
        this.learnerRepo = learnerRepo;
        this.conceptRepo = conceptRepo;
        this.loRepo = loRepo;
        this.minedRepo = minedRepo;
    }

    /**
     * Mine all clusters across all concepts.
     * Called via POST /api/admin/mining/run or scheduled job.
     */
    @Transactional
    public void mineAllClusters() {
        log.info("Starting sequence mining for all clusters...");

        // Group learners by cluster
        Map<String, List<Learner>> byCluster = new HashMap<>();
        for (Learner l : learnerRepo.findAll()) {
            String key = ClusterKeyUtil.forLearner(l);
            byCluster.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
        }

        // Mine each cluster
        int totalPatterns = 0;
        for (Map.Entry<String, List<Learner>> entry : byCluster.entrySet()) {
            int patterns = mineCluster(entry.getKey(), entry.getValue());
            totalPatterns += patterns;
        }

        // Global fallback for sparse clusters
        int globalPatterns = mineCluster("GLOBAL", learnerRepo.findAll());
        totalPatterns += globalPatterns;

        log.info("Sequence mining complete. Mined {} patterns across all clusters.", totalPatterns);
    }

    /**
     * Mine patterns for a specific cluster across all concepts.
     *
     * @param clusterKey FSLSM cluster key (e.g., "ARN_SIP_VVP_SGN" or "GLOBAL")
     * @param clusterLearners Learners in this cluster
     * @return Number of patterns mined
     */
    private int mineCluster(String clusterKey, List<Learner> clusterLearners) {
        if (clusterLearners.isEmpty()) {
            log.debug("Skip mining cluster={} (no learners)", clusterKey);
            return 0;
        }

        Set<Long> learnerIds = clusterLearners.stream()
                .map(Learner::getId)
                .collect(Collectors.toSet());

        // Get all completed sessions for these learners (exclude synthetic test sessions)
        List<LearningSession> allSessions = sessionRepo.findAll().stream()
                .filter(s -> s.isCompleted()
                        && learnerIds.contains(s.getLearner().getId())
                        && !Boolean.TRUE.equals(s.getIsTestSet()))  // Exclude test sessions from mining
                .toList();

        if (allSessions.isEmpty()) {
            log.debug("Skip mining cluster={} (no completed sessions)", clusterKey);
            return 0;
        }

        // Partition by concept
        Map<Long, List<LearningSession>> byConceptId = allSessions.stream()
                .collect(Collectors.groupingBy(s -> s.getConcept().getId()));

        // Mine each concept partition
        int totalPatterns = 0;
        for (Map.Entry<Long, List<LearningSession>> entry : byConceptId.entrySet()) {
            Long conceptId = entry.getKey();
            List<LearningSession> conceptSessions = entry.getValue();

            int patterns = mineConceptPartition(clusterKey, conceptId, conceptSessions);
            totalPatterns += patterns;
        }

        return totalPatterns;
    }

    /**
     * Mine a single (cluster_key, concept_id) partition.
     *
     * Steps:
     * 1. Extract per-learner sequences from completed sessions
     * 2. Collapse consecutive duplicates
     * 3. Build suffix→next_type counts with distinct learner tracking
     * 4. Apply MIN_EVENTS, MIN_LEARNERS, MIN_SUPPORT thresholds
     * 5. Persist to mined_supports with uppercase normalization
     */
    private int mineConceptPartition(String clusterKey, Long conceptId, List<LearningSession> sessions) {
        // Threshold check: minimum events (count completions, not just visits)
        int totalEvents = sessions.stream()
                .mapToInt(s -> s.getCompletionSequence().size())
                .sum();

        Set<Long> uniqueLearners = sessions.stream()
                .map(s -> s.getLearner().getId())
                .collect(Collectors.toSet());

        if (uniqueLearners.size() < MIN_LEARNERS || totalEvents < MIN_EVENTS) {
            log.debug("Skip mining cluster={} concept={} (learners={}, events={})",
                    clusterKey, conceptId, uniqueLearners.size(), totalEvents);
            return 0;
        }

        // Extract per-learner sequences with duplicate collapsing
        record SuffixKey(List<String> suffix, String next) {}
        Map<SuffixKey, Set<Long>> learnerSupport = new HashMap<>(); // Track which learners exhibited each pattern
        Map<SuffixKey, Integer> patternCounts = new HashMap<>(); // Track how many times pattern appeared

        for (LearningSession session : sessions) {
            Long learnerId = session.getLearner().getId();

            // Get LO type sequence with duplicate collapsing
            List<String> typeSequence = extractAndCollapseSequence(session);

            if (typeSequence.isEmpty()) continue; // Need at least 1 type

            // Extract start-state pattern (empty suffix -> first LO)
            if (!typeSequence.isEmpty()) {
                String firstType = typeSequence.get(0);
                SuffixKey startKey = new SuffixKey(Collections.emptyList(), firstType);
                learnerSupport.computeIfAbsent(startKey, k -> new HashSet<>()).add(learnerId);
                patternCounts.merge(startKey, 1, Integer::sum);
            }

            // Extract all n-grams (suffix lengths 1..MAX_SUFFIX)
            for (int i = 1; i < typeSequence.size(); i++) {
                String next = typeSequence.get(i);

                // Try suffix lengths 1..MAX_SUFFIX
                for (int n = MIN_SUFFIX; n <= MAX_SUFFIX && n <= i; n++) {
                    int start = i - n;
                    List<String> suffix = typeSequence.subList(start, i);

                    SuffixKey key = new SuffixKey(suffix, next);
                    learnerSupport.computeIfAbsent(key, k -> new HashSet<>()).add(learnerId);
                    patternCounts.merge(key, 1, Integer::sum);
                }
            }
        }

        // Apply MIN_LEARNERS threshold and compute support
        int nLearners = uniqueLearners.size();
        List<MinedSupport> toSave = new ArrayList<>();

        for (Map.Entry<SuffixKey, Set<Long>> entry : learnerSupport.entrySet()) {
            int distinctLearners = entry.getValue().size();

            // Threshold: minimum distinct learners
            if (distinctLearners < MIN_LEARNERS) continue;

            // Compute support as fraction of learners in partition
            double support = distinctLearners / (double) nLearners;

            // Threshold: minimum support
            if (support < MIN_SUPPORT) continue;

            // Verify concept exists
            if (conceptRepo.findById(conceptId).isEmpty()) continue;

            // Build row with canonical form (already normalized by extractAndCollapseSequence)
            SuffixKey key = entry.getKey();
            // Empty suffix becomes empty string "" for start-state patterns
            String suffixStr = key.suffix().isEmpty() ? "" : String.join(">", key.suffix());
            String nextType = key.next();  // Already canonical
            int count = patternCounts.get(key);

            MinedSupport row = new MinedSupport(
                    clusterKey,
                    conceptId,
                    suffixStr,
                    nextType,
                    support,
                    count,
                    distinctLearners
            );
            toSave.add(row);
        }

        if (!toSave.isEmpty()) {
            // Remove old patterns for this partition
            List<MinedSupport> oldPatterns = minedRepo.findAll().stream()
                    .filter(ms -> ms.getClusterKey().equals(clusterKey) &&
                                  ms.getConceptId().equals(conceptId))
                    .toList();
            if (!oldPatterns.isEmpty()) {
                minedRepo.deleteAllInBatch(oldPatterns);
            }

            // Save new patterns
            minedRepo.saveAll(toSave);
            log.info("Mined cluster={} concept={}: {} patterns from {} learners",
                    clusterKey, conceptId, toSave.size(), nLearners);
        }

        return toSave.size();
    }

    /**
     * Extract LO type sequence from a session's completion sequence with LO-ID-level collapse.
     *
     * Policy:
     * - Uses completionSequence (done + revisited) instead of visitedLoIds (navigation)
     * - Collapse only contiguous repeats of the same LO ID (T1→T1→T1 becomes T1)
     * - Preserve different LO IDs of same type (T1→T2 stays as T→T)
     * - Preserve non-contiguous revisits (T1→E1→T1 stays as T→E→T)
     * - Captures Reflective/Global behavior (reading multiple theories)
     *
     * Example:
     * Input:  [T1, T1, T2, E1, A1, A1, A2]
     * Output: [T, T, E, A, A]  (T1→T1 collapsed, T2 kept, A1→A1 collapsed, A2 kept)
     *
     * Excludes PRACTICE and NAVIGATION events.
     * Normalizes to canonical forms (T, E, A, F, Test).
     *
     * @param session Learning session
     * @return Sequence of canonical LO types with LO-ID-level collapse
     */
    private List<String> extractAndCollapseSequence(LearningSession session) {
        List<String> typeSequence = new ArrayList<>();
        Long lastLoId = null;

        // Use completionSequence instead of visitedLoIds for mining
        for (Long loId : session.getCompletionSequence()) {
            LearningObject lo = loRepo.findById(loId).orElse(null);
            if (lo == null || lo.getType() == null) continue;

            String type = normalizeToCanonical(lo.getType());

            // Exclude PRACTICE and NAVIGATION
            if (EXCLUDE.contains(type)) continue;

            // Collapse only contiguous same-LO-ID repeats
            // Keep different LO IDs even if same type (T1→T2 = T→T)
            // Keep non-contiguous revisits (T1→E1→T1 = T→E→T)
            if (!loId.equals(lastLoId)) {
                typeSequence.add(type);
                lastLoId = loId;
            }
            // else: same LO ID repeated contiguously → skip (collapsed)
        }

        return typeSequence;
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
     * Recommend next LO type based on mined patterns with suffix backoff.
     *
     * @param clusterKey FSLSM cluster key
     * @param conceptId Target concept ID
     * @param recentTypes Recent LO types (suffix, should be canonical forms)
     * @return Recommended next type, or empty if no pattern found
     */
    @Transactional(readOnly = true)
    public Optional<String> recommendNext(String clusterKey, Long conceptId, List<String> recentTypes) {
        if (recentTypes == null || recentTypes.isEmpty()) return Optional.empty();

        // Try suffix lengths MAX_SUFFIX → ... → 1 (backoff)
        for (int n = Math.min(MAX_SUFFIX, recentTypes.size()); n >= MIN_SUFFIX; n--) {
            List<String> suffix = recentTypes.subList(recentTypes.size() - n, recentTypes.size());
            String suffixStr = String.join(">", suffix);  // Already canonical from caller

            // Try cluster-specific patterns first
            var rows = minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                    clusterKey, conceptId, suffixStr);
            if (!rows.isEmpty()) {
                return Optional.of(rows.get(0).getNextType());
            }

            // Fallback to GLOBAL cluster
            var global = minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                    "GLOBAL", conceptId, suffixStr);
            if (!global.isEmpty()) {
                return Optional.of(global.get(0).getNextType());
            }
        }

        return Optional.empty();
    }
}
