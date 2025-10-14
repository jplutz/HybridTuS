package org.example.protushybrid.controller.admin;

import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.mining.FrequentSequenceRepository;
import org.example.protushybrid.repository.mining.MiningViolationRepository;
import org.example.protushybrid.service.core.ClusterKeyUtil;
import org.example.protushybrid.service.mining.SequenceMiningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin monitoring and control endpoints.
 */
@RestController
@RequestMapping("/api/admin/monitoring")
public class MonitoringController {

    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);

    private final FrequentSequenceRepository freqRepo;
    private final LearnerRepository learnerRepo;
    private final ConceptRepository conceptRepo;
    private final MiningViolationRepository violationRepo;
    private final SequenceMiningService miningService;

    public MonitoringController(FrequentSequenceRepository freqRepo,
                                LearnerRepository learnerRepo,
                                ConceptRepository conceptRepo,
                                MiningViolationRepository violationRepo,
                                SequenceMiningService miningService) {
        this.freqRepo = freqRepo;
        this.learnerRepo = learnerRepo;
        this.conceptRepo = conceptRepo;
        this.violationRepo = violationRepo;
        this.miningService = miningService;
    }

    /**
     * Get recommendation coverage metrics.
     * Shows what percentage of recommendations use cohort data vs fallbacks.
     */
    @GetMapping("/coverage")
    public ResponseEntity<Map<String, Object>> getCoverage() {
        // Count partitions with mined sequences
        var allSequences = freqRepo.findAll();

        // Group by cluster × concept
        Map<String, Set<Long>> partitions = new HashMap<>();
        for (var seq : allSequences) {
            String key = seq.getClusterKey();
            Long conceptId = seq.getConcept() != null ? seq.getConcept().getId() : null;
            if (conceptId != null) {
                partitions.computeIfAbsent(key, k -> new HashSet<>()).add(conceptId);
            }
        }

        // Count total possible partitions
        var allLearners = learnerRepo.findAll();
        Set<String> allClusters = new HashSet<>();
        for (var learner : allLearners) {
            allClusters.add(ClusterKeyUtil.forLearner(learner));
        }

        int totalConcepts = (int) conceptRepo.count();
        int totalPossiblePartitions = allClusters.size() * totalConcepts;

        int partitionsWithData = 0;
        for (var entry : partitions.entrySet()) {
            partitionsWithData += entry.getValue().size();
        }

        double coverage = totalPossiblePartitions > 0
                ? (double) partitionsWithData / totalPossiblePartitions
                : 0.0;

        Map<String, Object> response = new HashMap<>();
        response.put("partitionsWithData", partitionsWithData);
        response.put("totalPossiblePartitions", totalPossiblePartitions);
        response.put("coveragePercent", coverage * 100);
        response.put("uniqueClusters", allClusters.size());
        response.put("totalConcepts", totalConcepts);

        return ResponseEntity.ok(response);
    }

    /**
     * Get partition size alerts (undersized partitions).
     * Returns partitions with < 8 learners or < 40 events.
     */
    @GetMapping("/alerts/undersized")
    public ResponseEntity<List<Map<String, Object>>> getUndersizedPartitions() {
        // This is a simplified version; full implementation would query sequence_stats
        List<Map<String, Object>> alerts = new ArrayList<>();

        var allSequences = freqRepo.findAll();
        Map<String, Integer> partitionCounts = new HashMap<>();

        for (var seq : allSequences) {
            String key = seq.getClusterKey() + "_" +
                        (seq.getConcept() != null ? seq.getConcept().getId() : "null");
            partitionCounts.put(key, seq.getNLearners() != null ? seq.getNLearners() : 0);
        }

        for (var entry : partitionCounts.entrySet()) {
            if (entry.getValue() < 8) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("partition", entry.getKey());
                alert.put("learnerCount", entry.getValue());
                alert.put("status", "undersized");
                alerts.add(alert);
            }
        }

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get mining violations for SME review.
     */
    @GetMapping("/violations")
    public ResponseEntity<List<Map<String, Object>>> getViolations(
            @RequestParam(required = false) Long conceptId,
            @RequestParam(defaultValue = "50") int limit) {

        var violations = conceptId != null
                ? violationRepo.findByConceptOrderByDetectedAtDesc(
                        conceptRepo.findById(conceptId).orElse(null))
                : violationRepo.findAll().stream()
                        .sorted(Comparator.comparing(v -> v.getDetectedAt(), Comparator.reverseOrder()))
                        .limit(limit)
                        .toList();

        List<Map<String, Object>> response = new ArrayList<>();
        for (var v : violations) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", v.getId());
            item.put("clusterKey", v.getClusterKey());
            item.put("conceptId", v.getConcept() != null ? v.getConcept().getId() : null);
            item.put("conceptName", v.getConcept() != null ? v.getConcept().getName() : null);
            item.put("sequence", v.getSeq());
            item.put("reason", v.getViolationReason());
            item.put("detectedAt", v.getDetectedAt());
            response.add(item);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get KPI metrics.
     */
    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> getKpis() {
        Map<String, Object> kpis = new HashMap<>();

        // Placeholder metrics
        kpis.put("totalLearners", learnerRepo.count());
        kpis.put("totalConcepts", conceptRepo.count());
        kpis.put("minedSequences", freqRepo.count());
        kpis.put("violations", violationRepo.count());

        // TODO: Add MAE, mastered-concepts/hour, fail-streak metrics

        return ResponseEntity.ok(kpis);
    }

    /**
     * Manually trigger mining for all clusters.
     */
    @PostMapping("/mining/run")
    public ResponseEntity<String> triggerMining() {
        log.info("Manual mining triggered via API");
        try {
            miningService.mineAllClusters();
            return ResponseEntity.ok("Mining completed successfully");
        } catch (Exception e) {
            log.error("Mining failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Mining failed: " + e.getMessage());
        }
    }
}
