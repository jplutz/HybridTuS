package org.example.protushybrid.service.mining;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.mining.SequenceStats;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.tracking.InteractionRepository;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.mining.SequenceStatsRepository;
import org.example.protushybrid.service.core.ClusterKeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Incremental sequence mining statistics.
 * Updates lightweight counters per interaction and triggers full mining at thresholds.
 *
 * Thresholds:
 * - ≥50 new events in partition
 * - ≥5 new learners in partition
 * - Debounce: 10 minutes between full mines for same partition
 */
@Service
public class SequenceStatsService {

    private static final Logger log = LoggerFactory.getLogger(SequenceStatsService.class);

    private static final int EVENT_THRESHOLD = 50;
    private static final int LEARNER_THRESHOLD = 5;
    private static final int DEBOUNCE_MINUTES = 10;

    private final SequenceStatsRepository statsRepo;
    private final InteractionRepository interactionRepo;
    private final LearnerRepository learnerRepo;
    private final ConceptRepository conceptRepo;
    private final SequenceMiningService miningService;

    // Track last mining time per partition to debounce
    private final Map<String, Instant> lastMiningTime = new HashMap<>();

    public SequenceStatsService(SequenceStatsRepository statsRepo,
                                InteractionRepository interactionRepo,
                                LearnerRepository learnerRepo,
                                ConceptRepository conceptRepo,
                                SequenceMiningService miningService) {
        this.statsRepo = statsRepo;
        this.interactionRepo = interactionRepo;
        this.learnerRepo = learnerRepo;
        this.conceptRepo = conceptRepo;
        this.miningService = miningService;
    }

    /**
     * Update incremental counters for a new interaction.
     * Generates n-grams from recent path and increments counters.
     * Triggers full mining if thresholds are met.
     *
     * @param learnerId Learner ID
     * @param conceptId Concept ID
     * @param loType Learning object type just completed
     */
    @Transactional
    public void updateStats(Long learnerId, Long conceptId, String loType) {
        Learner learner = learnerRepo.findById(learnerId).orElse(null);
        Concept concept = conceptRepo.findById(conceptId).orElse(null);
        if (learner == null || concept == null) return;

        String clusterKey = ClusterKeyUtil.forLearner(learner);

        // Get recent path for this learner+concept
        List<String> recentPath = getRecentPath(learnerId, conceptId, 4);

        // Generate n-grams (2-4) and update counters
        for (int n = 2; n <= Math.min(4, recentPath.size()); n++) {
            String[] ngram = recentPath.subList(recentPath.size() - n, recentPath.size()).toArray(String[]::new);
            updateCounter(clusterKey, concept, ngram, learnerId);
        }

        // Check if thresholds met and trigger mining
        checkAndTriggerMining(clusterKey, concept);
    }

    /**
     * Update counter for a specific n-gram.
     */
    private void updateCounter(String clusterKey, Concept concept, String[] ngram, Long learnerId) {
        List<String> ngramList = Arrays.asList(ngram);

        // Find existing by filtering in code (JPA doesn't support collection comparison)
        var allStats = statsRepo.findByClusterKeyAndConcept(clusterKey, concept);
        Optional<SequenceStats> existing = allStats.stream()
                .filter(s -> s.getNgram().equals(ngramList))
                .findFirst();

        SequenceStats stats;
        if (existing.isPresent()) {
            stats = existing.get();
            stats.setEventCount(stats.getEventCount() + 1);

            // Add learner ID if not already present
            Set<Long> learnerSet = new HashSet<>(stats.getLearnerIds());
            learnerSet.add(learnerId);
            stats.setLearnerIds(new ArrayList<>(learnerSet));
        } else {
            stats = new SequenceStats();
            stats.setClusterKey(clusterKey);
            stats.setConcept(concept);
            stats.setNgram(new ArrayList<>(ngramList));
            stats.setEventCount(1);
            stats.setLearnerIds(new ArrayList<>(List.of(learnerId)));
        }

        stats.setLastUpdated(Instant.now());
        statsRepo.save(stats);
    }

    /**
     * Check if mining thresholds are met and trigger full mining if needed.
     * Debounces to avoid excessive mining.
     */
    private void checkAndTriggerMining(String clusterKey, Concept concept) {
        String partitionKey = clusterKey + "_" + concept.getId();

        // Debounce check
        Instant lastMined = lastMiningTime.get(partitionKey);
        if (lastMined != null &&
            ChronoUnit.MINUTES.between(lastMined, Instant.now()) < DEBOUNCE_MINUTES) {
            return; // Too soon to remine
        }

        // Check thresholds
        Long totalEvents = statsRepo.sumEventCountByClusterKeyAndConcept(clusterKey, concept);
        if (totalEvents == null) totalEvents = 0L;

        var allStats = statsRepo.findByClusterKeyAndConcept(clusterKey, concept);
        Set<Long> uniqueLearners = new HashSet<>();
        for (var stat : allStats) {
            uniqueLearners.addAll(stat.getLearnerIds());
        }

        boolean shouldRemine = totalEvents >= EVENT_THRESHOLD || uniqueLearners.size() >= LEARNER_THRESHOLD;

        if (shouldRemine) {
            log.info("Triggering remine for partition {}: {} events, {} learners",
                    partitionKey, totalEvents, uniqueLearners.size());
            lastMiningTime.put(partitionKey, Instant.now());
            triggerReminingAsync(clusterKey);
        }
    }

    /**
     * Trigger full mining asynchronously to avoid blocking interaction recording.
     */
    @Async
    public void triggerReminingAsync(String clusterKey) {
        try {
            // Mines all clusters for simplicity (acceptable for prototype scale)
            miningService.mineAllClusters();
            log.info("Completed remining triggered by cluster {}", clusterKey);
        } catch (Exception e) {
            log.error("Error during async remining for cluster {}: {}", clusterKey, e.getMessage(), e);
        }
    }

    /**
     * Get recent interaction path for a learner in a concept.
     */
    private List<String> getRecentPath(Long learnerId, Long conceptId, int maxLength) {
        var interactions = interactionRepo.findByLearnerIdOrderByTimestampAsc(learnerId);
        List<String> path = new ArrayList<>();

        for (var inter : interactions) {
            if (inter.getLearningObject() != null &&
                inter.getLearningObject().getConcept() != null &&
                conceptId.equals(inter.getLearningObject().getConcept().getId()) &&
                inter.getLoType() != null) {
                path.add(inter.getLoType().toLowerCase());
            }
        }

        int start = Math.max(0, path.size() - maxLength);
        return path.subList(start, path.size());
    }
}
