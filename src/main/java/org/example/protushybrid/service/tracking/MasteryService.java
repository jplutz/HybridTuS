package org.example.protushybrid.service.tracking;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.LearningSession;
import org.example.protushybrid.domain.tracking.Mastery;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.core.LearningSessionRepository;
import org.example.protushybrid.repository.tracking.MasteryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Manages mastery tracking using EWMA (Exponentially Weighted Moving Average).
 *
 * Score usage: Uses direct normalized score (0.0-1.0) from grading for transparent mastery tracking
 * Update rule: p̂ₜ = α·yₜ + (1-α)·p̂ₜ₋₁, where α=0.4, yₜ = normalized score (0.0-1.0)
 * Initial: p̂₀ = 0.0 (0% - learner starts with no demonstrated knowledge)
 * Bands: <0.65 UNSAFE, 0.65-0.69 WORKING, ≥0.70 MASTERED
 * Status labels suppressed until eventCount >= 3
 *
 * Expected exercises with perfect scores (score=1.0):
 * - 2 exercises → 64% (just below Working)
 * - 3 exercises → 78% (Mastered!)
 * - 4 exercises → 87% (Mastered)
 */
@Service
public class MasteryService {
    private static final Logger log = LoggerFactory.getLogger(MasteryService.class);

    private static final double ALPHA = 0.4;  // Smoothing factor (recent weight)
    private static final double INITIAL_MASTERY = 0.0;  // Start at 0% - no demonstrated knowledge yet
    private static final int MIN_EVENTS_FOR_LABEL = 3;

    private static final double THRESHOLD_UNSAFE = 0.65;
    private static final double THRESHOLD_MASTERED = 0.70;

    private final LearnerRepository learners;
    private final ConceptRepository concepts;
    private final MasteryRepository masteryRepo;
    private final LearningSessionRepository sessionRepo;

    public MasteryService(LearnerRepository learners,
                         ConceptRepository concepts,
                         MasteryRepository masteryRepo,
                         LearningSessionRepository sessionRepo) {
        this.learners = learners;
        this.concepts = concepts;
        this.masteryRepo = masteryRepo;
        this.sessionRepo = sessionRepo;
    }

    /**
     * Ensure mastery rows exist for all concepts for this learner
     */
    @Transactional
    public void ensureRowsForLearner(Long learnerId) {
        var learner = learners.findById(learnerId).orElseThrow();
        var existing = masteryRepo.findByLearner(learner);
        if (!existing.isEmpty()) return;

        for (Concept c : concepts.findAll()) {
            var m = new Mastery();
            var pk = new Mastery.Pk();
            pk.setLearnerId(learner.getId());
            pk.setConceptId(c.getId());
            m.setId(pk);
            m.setLearner(learner);
            m.setConcept(c);
            m.setMastery(INITIAL_MASTERY);
            m.setEventCount(0);
            masteryRepo.save(m);
        }
    }

    /**
     * Update mastery for a concept based on a graded outcome.
     * Uses EWMA: p̂ₜ = α·yₜ + (1-α)·p̂ₜ₋₁
     *
     * @param learnerId Learner ID
     * @param conceptId Concept ID
     * @param score Normalized score on 0.0-1.0 scale from grading
     */
    @Transactional
    public void updateMastery(Long learnerId, Long conceptId, double score) {
        var learner = learners.findById(learnerId).orElseThrow();
        var concept = concepts.findById(conceptId).orElseThrow();

        var pk = new Mastery.Pk();
        pk.setLearnerId(learnerId);
        pk.setConceptId(conceptId);

        Mastery mastery = masteryRepo.findById(pk).orElseGet(() -> {
            var m = new Mastery();
            m.setId(pk);
            m.setLearner(learner);
            m.setConcept(concept);
            m.setMastery(INITIAL_MASTERY);
            m.setEventCount(0);
            return m;
        });

        // Clamp score to [0.0, 1.0] for safety
        double clampedScore = Math.max(0.0, Math.min(1.0, score));

        // EWMA update - use score directly
        double prevMastery = mastery.getMastery();
        double newMastery = ALPHA * clampedScore + (1.0 - ALPHA) * prevMastery;
        int prevEventCount = mastery.getEventCount();
        int newEventCount = prevEventCount + 1;

        mastery.setMastery(newMastery);
        mastery.setEventCount(newEventCount);
        mastery.setLastUpdated(Instant.now());

        masteryRepo.save(mastery);

        log.debug("Updated mastery for learner {} concept {}: score={}, prev={}, new={}, events={}",
                learnerId, conceptId, clampedScore, prevMastery, newMastery, newEventCount);

        // Auto-complete sessions when mastery threshold is crossed (from below to above)
        checkAndCompleteSessionsOnMastery(learnerId, conceptId, prevMastery, newMastery, newEventCount);
    }

    /**
     * Auto-complete all incomplete sessions for a learner-concept pair when mastery threshold is crossed.
     * Only triggers when mastery crosses from below THRESHOLD_MASTERED to above it.
     * This ensures:
     * - Sessions are only completed once when mastery is first achieved
     * - If mastery drops and rises again, sessions aren't re-completed (already completed)
     * - New sessions started after mastery is high won't be auto-completed
     *
     * @param learnerId Learner ID
     * @param conceptId Concept ID
     * @param prevMastery Previous mastery score (before update)
     * @param newMastery New mastery score (after update)
     * @param eventCount Current event count
     */
    private void checkAndCompleteSessionsOnMastery(Long learnerId, Long conceptId,
                                                   double prevMastery, double newMastery, int eventCount) {
        // Only trigger if we have enough events and crossed the threshold (from below to above)
        boolean crossedThreshold = prevMastery < THRESHOLD_MASTERED && newMastery >= THRESHOLD_MASTERED;
        boolean hasEnoughEvents = eventCount >= MIN_EVENTS_FOR_LABEL;

        if (crossedThreshold && hasEnoughEvents) {
            // Find all incomplete sessions for this learner-concept pair
            List<LearningSession> incompleteSessions = sessionRepo.findAll().stream()
                    .filter(s -> !s.isCompleted()
                              && s.getLearner().getId().equals(learnerId)
                              && s.getConcept().getId().equals(conceptId))
                    .toList();

            if (!incompleteSessions.isEmpty()) {
                log.info("Mastery threshold crossed (%.2f -> %.2f) for learner {} in concept {}. Auto-completing {} session(s).",
                        prevMastery, newMastery, learnerId, conceptId, incompleteSessions.size());

                // Mark all incomplete sessions as completed
                for (LearningSession session : incompleteSessions) {
                    session.setCompleted(true);
                    sessionRepo.save(session);
                    log.debug("Marked session {} as completed due to mastery achievement", session.getSessionId());
                }
            }
        }
    }


    /**
     * Get mastery band for display purposes.
     * Returns null if eventCount < MIN_EVENTS_FOR_LABEL
     */
    public MasteryBand getMasteryBand(Mastery mastery) {
        if (mastery.getEventCount() < MIN_EVENTS_FOR_LABEL) {
            return null; // Suppress label
        }

        double p = mastery.getMastery();
        if (p < THRESHOLD_UNSAFE) return MasteryBand.UNSAFE;
        if (p < THRESHOLD_MASTERED) return MasteryBand.WORKING;
        return MasteryBand.MASTERED;
    }

    /**
     * Get concept with lowest mastery for this learner
     */
    public Mastery lowestMastery(Long learnerId) {
        var list = masteryRepo.findOrderedByLowest(learnerId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Check if learner has mastered a concept
     */
    public boolean isMastered(Long learnerId, Long conceptId) {
        var pk = new Mastery.Pk();
        pk.setLearnerId(learnerId);
        pk.setConceptId(conceptId);

        return masteryRepo.findById(pk)
                .map(m -> m.getEventCount() >= MIN_EVENTS_FOR_LABEL && m.getMastery() >= THRESHOLD_MASTERED)
                .orElse(false);
    }

    public enum MasteryBand {
        UNSAFE,    // < 0.65
        WORKING,   // 0.65 - 0.79
        MASTERED   // >= 0.80
    }
}
