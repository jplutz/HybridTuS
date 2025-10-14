package org.example.protushybrid.service.mining;

import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.core.LearningObject;
import org.example.protushybrid.domain.core.LearningSession;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.core.LearningObjectRepository;
import org.example.protushybrid.repository.core.LearningSessionRepository;
import org.example.protushybrid.service.recommendation.DefaultSequenceProvider;
import org.example.protushybrid.service.recommendation.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Evaluation service for measuring recommendation system performance on hold-out test set.
 *
 * Metrics:
 * - Top-1 Accuracy: P(recommended LO type = actual next LO type)
 * - Coverage: % of test cases where recommendation was possible
 * - Per-cluster breakdown: Accuracy/coverage by FSLSM cluster
 * - Baseline Comparison: Pattern Mining vs FSLSM-only baseline
 *
 * Evaluation Protocol:
 * 1. Take hold-out test sessions (20% of generated data)
 * 2. For each session, evaluate recommendations at each step
 * 3. Compare recommended LO type vs actual next LO type
 * 4. Also evaluate FSLSM-only baseline for comparison
 * 5. Compute aggregate metrics and improvement over baseline
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final LearningSessionRepository sessionRepo;
    private final LearnerRepository learnerRepo;
    private final LearningObjectRepository loRepo;
    private final ConceptRepository conceptRepo;
    private final RecommendationService recommendationService;
    private final DefaultSequenceProvider defaultSequenceProvider;

    public EvaluationService(LearningSessionRepository sessionRepo,
                             LearnerRepository learnerRepo,
                             LearningObjectRepository loRepo,
                             ConceptRepository conceptRepo,
                             RecommendationService recommendationService,
                             DefaultSequenceProvider defaultSequenceProvider) {
        this.sessionRepo = sessionRepo;
        this.learnerRepo = learnerRepo;
        this.loRepo = loRepo;
        this.conceptRepo = conceptRepo;
        this.recommendationService = recommendationService;
        this.defaultSequenceProvider = defaultSequenceProvider;
    }

    /**
     * Evaluate recommendation system on hold-out test set.
     * Compares Pattern Mining approach vs FSLSM-only baseline.
     *
     * @param testSessionIds IDs of sessions in hold-out test set (session UUIDs)
     * @return EvaluationReport with accuracy, coverage, baseline comparison, and per-cluster metrics
     */
    public EvaluationReport evaluate(List<String> testSessionIds) {
        log.info("Starting evaluation on {} test sessions", testSessionIds.size());

        // Metrics for Pattern Mining approach
        int totalPredictions = 0;
        int correctPredictions = 0;
        int coveredPredictions = 0;
        Map<String, ClusterMetrics> clusterMetrics = new HashMap<>();

        // Metrics for FSLSM Baseline approach
        int baselineCorrect = 0;
        int baselineCovered = 0;
        Map<String, ClusterMetrics> baselineClusterMetrics = new HashMap<>();

        for (String sessionId : testSessionIds) {
            LearningSession session = sessionRepo.findById(sessionId).orElse(null);
            if (session == null || session.getCompletionSequence().isEmpty()) {
                continue;
            }

            Learner learner = learnerRepo.findById(session.getLearner().getId()).orElse(null);
            if (learner == null) {
                continue;
            }

            String clusterKey = computeClusterKey(learner);
            ClusterMetrics metrics = clusterMetrics.computeIfAbsent(
                clusterKey, k -> new ClusterMetrics()
            );
            ClusterMetrics baselineMetrics = baselineClusterMetrics.computeIfAbsent(
                clusterKey, k -> new ClusterMetrics()
            );

            // Evaluate each step in the session (except the last, which has no next LO)
            // Use completion sequence (done + revisited) instead of navigation sequence
            List<Long> sequence = session.getCompletionSequence();
            for (int i = 0; i < sequence.size() - 1; i++) {
                // Build suffix from [0...i]
                List<String> suffix = extractTypeSuffix(sequence.subList(0, i + 1));

                // Get actual next LO type (normalized to canonical form)
                Long nextLoId = sequence.get(i + 1);
                LearningObject nextLo = loRepo.findById(nextLoId).orElse(null);
                if (nextLo == null || nextLo.getType() == null) {
                    continue;
                }
                String actualType = normalizeToCanonical(nextLo.getType());

                // Get available LOs for this concept (filter out already visited)
                Set<String> availableTypes = getAvailableTypes(
                    session.getConcept().getId(), new HashSet<>(sequence.subList(0, i + 1))
                );

                totalPredictions++;
                metrics.total++;
                baselineMetrics.total++;

                // === 1. Evaluate Pattern Mining approach ===
                Long recommendedLoId = recommendationService.getRecommendation(
                    learner.getId(),
                    session.getConcept().getId(),
                    suffix,
                    availableTypes
                );

                if (recommendedLoId != null) {
                    coveredPredictions++;
                    metrics.covered++;

                    // Check if recommended LO type matches actual next type
                    LearningObject recommendedLo = loRepo.findById(recommendedLoId).orElse(null);
                    if (recommendedLo != null) {
                        String recommendedType = normalizeToCanonical(recommendedLo.getType());
                        if (recommendedType != null && recommendedType.equals(actualType)) {
                            correctPredictions++;
                            metrics.correct++;
                        }
                    }
                }

                // === 2. Evaluate FSLSM Baseline approach ===
                Long baselineLoId = defaultSequenceProvider.getDefaultRecommendation(
                    learner.getId(),
                    session.getConcept().getId(),
                    suffix,
                    availableTypes
                );

                if (baselineLoId != null) {
                    baselineCovered++;
                    baselineMetrics.covered++;

                    // Check if baseline recommendation matches actual next type
                    LearningObject baselineLo = loRepo.findById(baselineLoId).orElse(null);
                    if (baselineLo != null) {
                        String baselineType = normalizeToCanonical(baselineLo.getType());
                        if (baselineType != null && baselineType.equals(actualType)) {
                            baselineCorrect++;
                            baselineMetrics.correct++;
                        }
                    }
                }
            }
        }

        // Compute aggregate metrics for Pattern Mining
        double accuracy = totalPredictions > 0 ?
            (double) correctPredictions / totalPredictions : 0.0;
        double coverage = totalPredictions > 0 ?
            (double) coveredPredictions / totalPredictions : 0.0;

        // Compute aggregate metrics for FSLSM Baseline
        double baselineAccuracy = totalPredictions > 0 ?
            (double) baselineCorrect / totalPredictions : 0.0;
        double baselineCoverage = totalPredictions > 0 ?
            (double) baselineCovered / totalPredictions : 0.0;

        // Compute improvement metrics
        double absoluteImprovement = accuracy - baselineAccuracy;
        double relativeImprovement = baselineAccuracy > 0 ?
            (accuracy - baselineAccuracy) / baselineAccuracy : 0.0;

        // Compute per-cluster metrics
        Map<String, ClusterReport> clusterReports = new LinkedHashMap<>();
        for (Map.Entry<String, ClusterMetrics> entry : clusterMetrics.entrySet()) {
            String cluster = entry.getKey();
            ClusterMetrics m = entry.getValue();

            double clusterAccuracy = m.total > 0 ? (double) m.correct / m.total : 0.0;
            double clusterCoverage = m.total > 0 ? (double) m.covered / m.total : 0.0;

            clusterReports.put(cluster, new ClusterReport(
                m.total, m.covered, m.correct, clusterCoverage, clusterAccuracy
            ));
        }

        // Compute per-cluster baseline metrics
        Map<String, ClusterReport> baselineClusterReports = new LinkedHashMap<>();
        for (Map.Entry<String, ClusterMetrics> entry : baselineClusterMetrics.entrySet()) {
            String cluster = entry.getKey();
            ClusterMetrics m = entry.getValue();

            double clusterAccuracy = m.total > 0 ? (double) m.correct / m.total : 0.0;
            double clusterCoverage = m.total > 0 ? (double) m.covered / m.total : 0.0;

            baselineClusterReports.put(cluster, new ClusterReport(
                m.total, m.covered, m.correct, clusterCoverage, clusterAccuracy
            ));
        }

        EvaluationReport report = new EvaluationReport(
            testSessionIds.size(),
            totalPredictions,
            coveredPredictions,
            correctPredictions,
            coverage,
            accuracy,
            clusterReports,
            baselineCovered,
            baselineCorrect,
            baselineCoverage,
            baselineAccuracy,
            baselineClusterReports,
            absoluteImprovement,
            relativeImprovement
        );

        log.info("Evaluation complete:");
        log.info("  - Sessions: {}", testSessionIds.size());
        log.info("  - Total predictions: {}", totalPredictions);
        log.info("");
        log.info("  Pattern Mining:");
        log.info("    - Coverage: {}/{} ({} %)", coveredPredictions, totalPredictions, String.format("%.1f", coverage * 100));
        log.info("    - Top-1 Accuracy: {}/{} ({} %)", correctPredictions, totalPredictions, String.format("%.1f", accuracy * 100));
        log.info("");
        log.info("  FSLSM Baseline:");
        log.info("    - Coverage: {}/{} ({} %)", baselineCovered, totalPredictions, String.format("%.1f", baselineCoverage * 100));
        log.info("    - Top-1 Accuracy: {}/{} ({} %)", baselineCorrect, totalPredictions, String.format("%.1f", baselineAccuracy * 100));
        log.info("");
        log.info("  Improvement:");
        log.info("    - Absolute: {} percentage points", String.format("%.1f", absoluteImprovement * 100));
        log.info("    - Relative: {} % better than baseline", String.format("%.1f", relativeImprovement * 100));
        log.info("  - Clusters evaluated: {}", clusterReports.size());

        return report;
    }

    /**
     * Extract LO type suffix from sequence of LO IDs with LO-ID-level collapse.
     * Matches mining logic: same-LO-ID contiguous collapse, preserves different-LO same-type transitions.
     *
     * Example:
     * Input LO IDs:  [T1, T1, T2, E1, A1]
     * Output types:  [T, T, E, A]  (T1→T1 collapsed, T1→T2 kept as T→T)
     */
    private List<String> extractTypeSuffix(List<Long> loIds) {
        List<String> types = new ArrayList<>();
        Long lastLoId = null;

        for (Long loId : loIds) {
            LearningObject lo = loRepo.findById(loId).orElse(null);
            if (lo == null || lo.getType() == null) {
                continue;
            }
            String type = normalizeToCanonical(lo.getType());

            // Collapse only contiguous same-LO-ID repeats
            if (!loId.equals(lastLoId)) {
                types.add(type);
                lastLoId = loId;
            }
        }

        return types;
    }

    /**
     * Normalize LO type to canonical form.
     * Matches the normalization in SequenceMiningService for consistency.
     */
    private String normalizeToCanonical(String type) {
        if (type == null) return null;

        return switch (type.toUpperCase()) {
            case "THEORY" -> "T";
            case "EXAMPLE" -> "E";
            case "ACTIVITY" -> "A";
            case "FIGURE" -> "F";
            case "TEST" -> "Test";
            case "HINT" -> "X";
            case "T", "E", "A", "F", "X", "R", "S", "O" -> type.toUpperCase();
            default -> type.length() == 1 ? type.toUpperCase() : type;
        };
    }

    /**
     * Get available LO types for a concept (excluding already visited).
     * Returns canonical forms (T, E, A, F, Test).
     */
    private Set<String> getAvailableTypes(Long conceptId, Set<Long> visitedLoIds) {
        List<LearningObject> allLos = loRepo.findByConceptId(conceptId);
        return allLos.stream()
            .filter(lo -> !visitedLoIds.contains(lo.getId()))
            .map(LearningObject::getType)
            .filter(Objects::nonNull)
            .map(this::normalizeToCanonical)
            .collect(Collectors.toSet());
    }

    /**
     * Compute FSLSM cluster key from learner's style scores.
     */
    private String computeClusterKey(Learner learner) {
        String d1 = learner.getStyleActiveReflective() >= 0 ? "ACTIVE" : "REFLECTIVE";
        String d2 = learner.getStyleSensingIntuitive() >= 0 ? "SENSING" : "INTUITIVE";
        String d3 = learner.getStyleVisualVerbal() >= 0 ? "VISUAL" : "VERBAL";
        String d4 = learner.getStyleSequentialGlobal() >= 0 ? "SEQUENTIAL" : "GLOBAL";

        return String.format("%s_%s_%s_%s", d1, d2, d3, d4);
    }

    /**
     * Internal metrics accumulator for a cluster.
     */
    private static class ClusterMetrics {
        int total = 0;      // Total predictions attempted
        int covered = 0;    // Predictions where recommendation was possible
        int correct = 0;    // Predictions where type matched
    }

    /**
     * Evaluation report for a single cluster.
     */
    public record ClusterReport(
        int totalPredictions,
        int coveredPredictions,
        int correctPredictions,
        double coverage,
        double accuracy
    ) {}

    /**
     * Overall evaluation report with baseline comparison.
     */
    public record EvaluationReport(
        int testSessions,
        int totalPredictions,
        int coveredPredictions,
        int correctPredictions,
        double coverage,
        double accuracy,
        Map<String, ClusterReport> clusterMetrics,
        // Baseline metrics
        int baselineCoveredPredictions,
        int baselineCorrectPredictions,
        double baselineCoverage,
        double baselineAccuracy,
        Map<String, ClusterReport> baselineClusterMetrics,
        // Improvement metrics
        double absoluteImprovement,
        double relativeImprovement
    ) {
        /**
         * Format as human-readable string for logging/display.
         */
        public String toDetailedString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Evaluation Report:\n");
            sb.append(String.format("  Test Sessions: %d\n", testSessions));
            sb.append(String.format("  Total Predictions: %d\n", totalPredictions));
            sb.append("\n  Pattern Mining:\n");
            sb.append(String.format("    Coverage: %.1f%% (%d/%d)\n",
                coverage * 100, coveredPredictions, totalPredictions));
            sb.append(String.format("    Top-1 Accuracy: %.1f%% (%d/%d)\n",
                accuracy * 100, correctPredictions, totalPredictions));
            sb.append("\n  FSLSM Baseline:\n");
            sb.append(String.format("    Coverage: %.1f%% (%d/%d)\n",
                baselineCoverage * 100, baselineCoveredPredictions, totalPredictions));
            sb.append(String.format("    Top-1 Accuracy: %.1f%% (%d/%d)\n",
                baselineAccuracy * 100, baselineCorrectPredictions, totalPredictions));
            sb.append("\n  Improvement:\n");
            sb.append(String.format("    Absolute: %.1f percentage points\n",
                absoluteImprovement * 100));
            sb.append(String.format("    Relative: %.1f%% better than baseline\n",
                relativeImprovement * 100));
            sb.append("\n  Per-Cluster Metrics (Pattern Mining):\n");

            clusterMetrics.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String cluster = entry.getKey();
                    ClusterReport r = entry.getValue();
                    ClusterReport baseline = baselineClusterMetrics.get(cluster);
                    sb.append(String.format("    %s:\n", cluster));
                    sb.append(String.format("      Mining Accuracy: %.1f%% (%d/%d)\n",
                        r.accuracy * 100, r.correctPredictions, r.totalPredictions));
                    if (baseline != null) {
                        sb.append(String.format("      Baseline Accuracy: %.1f%% (%d/%d)\n",
                            baseline.accuracy * 100, baseline.correctPredictions, baseline.totalPredictions));
                        double improvement = r.accuracy - baseline.accuracy;
                        sb.append(String.format("      Improvement: %+.1f pp\n",
                            improvement * 100));
                    }
                });

            return sb.toString();
        }
    }
}
