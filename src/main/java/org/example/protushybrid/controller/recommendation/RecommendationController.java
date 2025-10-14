package org.example.protushybrid.controller.recommendation;

import org.example.protushybrid.controller.dto.RecommendationResponse;
import org.example.protushybrid.domain.core.LearningObject;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.service.recommendation.RecommendationService;
import org.example.protushybrid.service.recommendation.StuckDetectionService;
import org.example.protushybrid.service.tracking.MasteryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST API for adaptive recommendations.
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    private final RecommendationService recommendationService;
    private final StuckDetectionService stuckDetectionService;
    private final MasteryService masteryService;
    private final ConceptRepository conceptRepo;

    public RecommendationController(RecommendationService recommendationService,
                                    StuckDetectionService stuckDetectionService,
                                    MasteryService masteryService,
                                    ConceptRepository conceptRepo) {
        this.recommendationService = recommendationService;
        this.stuckDetectionService = stuckDetectionService;
        this.masteryService = masteryService;
        this.conceptRepo = conceptRepo;
    }

    /**
     * Get next recommended learning object for a learner.
     *
     * @param learnerId Learner ID
     * @param conceptId Target concept ID (optional; defaults to lowest mastery)
     * @return Recommended learning object with reasoning
     */
    @GetMapping("/next")
    public ResponseEntity<RecommendationResponse> getNext(
            @RequestParam Long learnerId,
            @RequestParam(required = false) Long conceptId) {

        log.info("Recommendation requested for learner {} in concept {}", learnerId, conceptId);

        // Determine target concept
        Long targetConceptId = conceptId;
        if (targetConceptId == null) {
            var mastery = masteryService.lowestMastery(learnerId);
            if (mastery != null) {
                targetConceptId = mastery.getConcept().getId();
            }
        }

        if (targetConceptId == null) {
            return ResponseEntity.notFound().build();
        }

        // Check if learner is stuck (for warning flag only - does not change recommendation)
        boolean isStuck = stuckDetectionService.isConsecutiveFail(learnerId, targetConceptId);
        boolean isMasteryDropping = stuckDetectionService.isMasteryDropping(learnerId, targetConceptId);

        if (isStuck) {
            log.info("Learner {} is stuck in concept {} (3 consecutive fails) - flagging for frontend warning", learnerId, targetConceptId);
        }

        if (isMasteryDropping) {
            log.info("Mastery dropping for learner {} in concept {} (≥0.15 drop) - flagging for frontend warning", learnerId, targetConceptId);
        }

        // Always get normal recommendation from mined patterns + cold-start
        // (stuck detection does not change recommendations - evaluation mode)
        Optional<LearningObject> recommendation = recommendationService.recommendNext(learnerId, targetConceptId);

        if (recommendation.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Set stuck flag for frontend warning, but recommendation is unchanged
        var response = RecommendationResponse.fromLearningObject(recommendation.get())
                .withReasoning("mined", null, null, null)
                .withIntervention(isStuck || isMasteryDropping);

        return ResponseEntity.ok(response);
    }

    /**
     * Get mastery status for a learner across all concepts.
     *
     * @param learnerId Learner ID
     * @return Map of concept IDs to mastery values and bands
     */
    @GetMapping("/mastery/{learnerId}")
    public ResponseEntity<?> getMasteryStatus(@PathVariable Long learnerId) {
        // TODO: Implement mastery status endpoint
        return ResponseEntity.ok("Not yet implemented");
    }

    /**
     * Get intervention recommendation for stuck learner.
     * Tries to find first available LO from intervention sequence.
     */
    private ResponseEntity<RecommendationResponse> getInterventionRecommendation(
            Long learnerId, Long conceptId, List<String> interventionTypes) {

        for (String loType : interventionTypes) {
            var lo = recommendationService.selectLearningObject(
                    conceptRepo.findById(conceptId).orElse(null),
                    loType
            );

            if (lo.isPresent()) {
                var response = RecommendationResponse.fromLearningObject(lo.get())
                        .withReasoning("intervention", null, null, null)
                        .withIntervention(true);
                return ResponseEntity.ok(response);
            }
        }

        // Fallback to normal recommendation if intervention LOs not available
        log.warn("Intervention LOs not available for concept {}, falling back to normal recommendation", conceptId);
        var recommendation = recommendationService.recommendNext(learnerId, conceptId);
        if (recommendation.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var response = RecommendationResponse.fromLearningObject(recommendation.get())
                .withReasoning("fallback", null, null, null)
                .withIntervention(true);
        return ResponseEntity.ok(response);
    }

    /**
     * Get lighter content recommendation for dropping mastery.
     * Prioritizes examples and definitions over assessments.
     */
    private ResponseEntity<RecommendationResponse> getLighterContentRecommendation(
            Long learnerId, Long conceptId, List<String> lighterTypes) {

        for (String loType : lighterTypes) {
            var lo = recommendationService.selectLearningObject(
                    conceptRepo.findById(conceptId).orElse(null),
                    loType
            );

            if (lo.isPresent()) {
                var response = RecommendationResponse.fromLearningObject(lo.get())
                        .withReasoning("lighter_content", null, null, null)
                        .withIntervention(false);
                return ResponseEntity.ok(response);
            }
        }

        // Fallback to normal recommendation if lighter LOs not available
        var recommendation = recommendationService.recommendNext(learnerId, conceptId);
        if (recommendation.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var response = RecommendationResponse.fromLearningObject(recommendation.get())
                .withReasoning("fallback", null, null, null)
                .withIntervention(false);
        return ResponseEntity.ok(response);
    }

}
