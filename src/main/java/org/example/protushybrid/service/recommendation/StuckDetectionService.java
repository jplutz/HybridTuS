package org.example.protushybrid.service.recommendation;

import org.example.protushybrid.domain.tracking.Interaction;
import org.example.protushybrid.domain.tracking.Mastery;
import org.example.protushybrid.repository.tracking.InteractionRepository;
import org.example.protushybrid.repository.tracking.MasteryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Detects when learners are stuck and provides intervention strategies.
 *
 * Detection rules:
 * - 3 consecutive fails (score ≤ 0.4) in same concept → intervention
 * - Mastery drop ≥ 0.15 over last 5 events → lighter content
 */
@Service
public class StuckDetectionService {

    private static final Logger log = LoggerFactory.getLogger(StuckDetectionService.class);

    private static final int CONSECUTIVE_FAIL_THRESHOLD = 3;
    private static final double MASTERY_DROP_THRESHOLD = 0.15;
    private static final int MASTERY_DROP_WINDOW = 5;

    private final InteractionRepository interactionRepo;
    private final MasteryRepository masteryRepo;

    public StuckDetectionService(InteractionRepository interactionRepo,
                                 MasteryRepository masteryRepo) {
        this.interactionRepo = interactionRepo;
        this.masteryRepo = masteryRepo;
    }

    /**
     * Check if learner is stuck in a concept based on recent failures.
     * @return true if 3 consecutive fails (score ≤ 0.4) detected
     */
    public boolean isConsecutiveFail(Long learnerId, Long conceptId) {
        var interactions = interactionRepo.findByLearnerIdOrderByTimestampAsc(learnerId);

        // Filter to this concept and get last attempts
        List<Double> recentScores = interactions.stream()
                .filter(i -> i.getConcept() != null &&
                             conceptId.equals(i.getConcept().getId()) &&
                             i.getScore() != null)
                .map(Interaction::getScore)
                .toList();

        if (recentScores.size() < CONSECUTIVE_FAIL_THRESHOLD) {
            return false;
        }

        // Check last 3 scores (0.4 threshold = Fair/Needs Work boundary)
        int start = Math.max(0, recentScores.size() - CONSECUTIVE_FAIL_THRESHOLD);
        List<Double> lastThree = recentScores.subList(start, recentScores.size());

        boolean allFailed = lastThree.stream().allMatch(score -> score <= 0.4);

        if (allFailed) {
            log.warn("Consecutive fail detected for learner {} in concept {} (scores ≤ 0.4)",
                    learnerId, conceptId);
        }

        return allFailed;
    }

    /**
     * Check if learner's mastery has dropped significantly in recent events.
     * @return true if mastery dropped ≥ 0.15 over last 5 events
     */
    public boolean isMasteryDropping(Long learnerId, Long conceptId) {
        var pk = new Mastery.Pk();
        pk.setLearnerId(learnerId);
        pk.setConceptId(conceptId);

        var masteryOpt = masteryRepo.findById(pk);
        if (masteryOpt.isEmpty()) return false;

        var mastery = masteryOpt.get();
        if (mastery.getEventCount() < MASTERY_DROP_WINDOW) {
            return false; // Not enough data
        }

        // Get interactions for this concept
        var interactions = interactionRepo.findByLearnerIdOrderByTimestampAsc(learnerId);
        var conceptInteractions = interactions.stream()
                .filter(i -> i.getConcept() != null &&
                             conceptId.equals(i.getConcept().getId()) &&
                             i.getScore() != null)
                .toList();

        if (conceptInteractions.size() < MASTERY_DROP_WINDOW + 1) {
            return false;
        }

        // Check if recent performance is significantly worse than current mastery
        // Use direct scores (0.0-1.0) for transparent comparison
        int start = Math.max(0, conceptInteractions.size() - MASTERY_DROP_WINDOW);
        List<Double> recentScores = conceptInteractions.subList(start, conceptInteractions.size())
                .stream()
                .map(Interaction::getScore)
                .toList();

        double recentAvg = recentScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double currentMastery = mastery.getMastery();
        boolean dropping = (currentMastery - recentAvg) >= MASTERY_DROP_THRESHOLD;

        if (dropping) {
            log.warn("Mastery dropping detected for learner {} in concept {}: current={}, recent_avg={}",
                    learnerId, conceptId, currentMastery, recentAvg);
        }

        return dropping;
    }

    /**
     * Get intervention recommendation for stuck learner.
     * Returns recommended sequence of LO types.
     */
    public List<String> getInterventionSequence() {
        // Force sequence: DEFINITION → GUIDED_PRACTICE (with hints) → CHECKPOINT
        return List.of("theory", "guided_practice", "checkpoint");
    }

    /**
     * Get lighter content recommendation for dropping mastery.
     * Returns list of lighter LO types to prioritize.
     */
    public List<String> getLighterContentTypes() {
        // Prioritize examples and definitions over assessments
        return List.of("example", "theory", "overview");
    }
}
