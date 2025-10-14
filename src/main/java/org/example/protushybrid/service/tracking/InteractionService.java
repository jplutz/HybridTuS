package org.example.protushybrid.service.tracking;

import org.example.protushybrid.domain.tracking.Interaction;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.core.LearningObjectRepository;
import org.example.protushybrid.repository.tracking.InteractionRepository;
import org.example.protushybrid.service.mining.SequenceStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Records learner interactions with anti-gaming filters.
 *
 * Anti-gaming rules:
 * - Ignore events with time < 5s (view) or < 30s (graded) unless retry
 * - Rate-limit credit for identical repeats (max 3 per hour)
 * - Exclude bot-like patterns (> 60 completions/hour)
 */
@Service
public class InteractionService {

    private static final Logger log = LoggerFactory.getLogger(InteractionService.class);

    private static final int MIN_DURATION_VIEW_SEC = 5;
    private static final int MIN_DURATION_GRADED_SEC = 30;
    private static final int BOT_THRESHOLD_PER_HOUR = 60;
    private static final int MAX_REPEATS_PER_HOUR = 20; // Allow up to 20 exercises per hour per LO (reasonable for practice)

    private final InteractionRepository interactions;
    private final LearnerRepository learners;
    private final LearningObjectRepository los;
    private final MasteryService masteryService;
    private final SequenceStatsService statsService;

    public InteractionService(InteractionRepository interactions,
                              LearnerRepository learners,
                              LearningObjectRepository los,
                              MasteryService masteryService,
                              SequenceStatsService statsService) {
        this.interactions = interactions;
        this.learners = learners;
        this.los = los;
        this.masteryService = masteryService;
        this.statsService = statsService;
    }

    /**
     * Record an interaction with anti-gaming filters.
     *
     * @param learnerId Learner ID
     * @param loId Learning object ID
     * @param score Score achieved (0.0-1.0), used directly for EWMA mastery tracking
     * @param scoreRaw Raw score on 1-5 scale (nullable for non-graded, kept for audit/historical purposes)
     * @param hintCount Number of hints used
     * @param durationSec Time spent in seconds
     * @return true if interaction was recorded, false if filtered out
     */
    @Transactional
    public boolean record(Long learnerId, Long loId, Double score, Short scoreRaw,
                          Integer hintCount, Integer durationSec) {
        var learner = learners.findById(learnerId)
                .orElseThrow(() -> new RuntimeException("Learner not found: " + learnerId));
        var lo = los.findById(loId)
                .orElseThrow(() -> new RuntimeException("Learning object not found: " + loId));
        var concept = lo.getConcept();

        // Anti-gaming filter: bot detection
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long recentCount = interactions.countByLearnerIdAndTimestampAfter(learnerId, oneHourAgo);
        if (recentCount >= BOT_THRESHOLD_PER_HOUR) {
            log.warn("Bot-like behavior detected for learner {}: {} completions in last hour",
                    learnerId, recentCount);
            return false;
        }

        // Anti-gaming filter: minimum duration (unless it's a retry)
        boolean isGraded = scoreRaw != null;
        int minDuration = isGraded ? MIN_DURATION_GRADED_SEC : MIN_DURATION_VIEW_SEC;
        var recent = interactions.findRecentByLearnerAndLO(learnerId, loId, oneHourAgo);
        boolean isRetry = !recent.isEmpty();

        if (!isRetry && durationSec != null && durationSec < minDuration) {
            log.debug("Interaction filtered: duration {}s < minimum {}s for learner {} on LO {}",
                    durationSec, minDuration, learnerId, loId);
            return false;
        }

        // Anti-gaming filter: rate limit repeats
        if (recent.size() >= MAX_REPEATS_PER_HOUR) {
            log.debug("Rate limit reached for learner {} on LO {}: {} attempts in last hour",
                    learnerId, loId, recent.size());
            return false;
        }

        // Record interaction
        var inter = new Interaction();
        inter.setLearner(learner);
        inter.setLearningObject(lo);
        inter.setConcept(concept);
        inter.setAction(scoreRaw != null ? "COMPLETE" : "VIEW"); // COMPLETE for graded, VIEW for non-graded
        inter.setResult("score");
        inter.setScore(score);
        inter.setScoreRaw(scoreRaw);
        inter.setHintCount(hintCount != null ? hintCount : 0);
        inter.setLoType(lo.getType());
        inter.setDurationSeconds(durationSec != null ? durationSec : 0);
        interactions.save(inter);

        log.debug("Recorded interaction for learner {} on LO {} with score {} (scoreRaw: {})",
                learnerId, loId, score, scoreRaw);

        // Update mastery only if graded (scoreRaw != null indicates graded interaction)
        if (scoreRaw != null && score != null && concept != null) {
            masteryService.updateMastery(learnerId, concept.getId(), score);
            log.debug("Updated mastery for learner {} in concept {} with score {}", learnerId, concept.getId(), score);
        }

        // Update sequence stats for incremental mining
        if (concept != null && lo.getType() != null) {
            statsService.updateStats(learnerId, concept.getId(), lo.getType());
        }

        return true;
    }

    /**
     * Calculate average score of last N interactions for the learner (across all concepts).
     */
    public double calculateAverageLast10(Long learnerId) {
        var last10 = interactions.findLast10ResultsByLearnerId(learnerId);
        if (last10.isEmpty()) {
            return 0.0;
        }
        return last10.stream()
                .mapToDouble(i -> i.getScore() != null ? i.getScore() : 0.0)
                .average()
                .orElse(0.0);
    }
}
