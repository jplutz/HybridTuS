package org.example.protushybrid.controller.core;

import org.example.protushybrid.controller.dto.StyleUpdateRequest;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.tracking.Mastery;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.tracking.MasteryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CRUD interface for learner management and style updates.
 */
@RestController
@RequestMapping("/api/learners")
public class LearnerController {

    private final LearnerRepository learners;
    private final MasteryRepository masteryRepo;

    public LearnerController(LearnerRepository learners, MasteryRepository masteryRepo) {
        this.learners = learners;
        this.masteryRepo = masteryRepo;
    }

    /** Create learner */
    @PostMapping
    public ResponseEntity<Learner> create(@RequestBody Learner l) {
        return ResponseEntity.ok(learners.save(l));
    }

    /** Retrieve all learners */
    @GetMapping
    public List<Learner> all() {
        return learners.findAll();
    }

    /** Retrieve specific learner */
    @GetMapping("/{id}")
    public ResponseEntity<Learner> one(@PathVariable Long id) {
        return learners.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Update FSLSM parameters (partial) */
    @PatchMapping("/{id}/styles")
    public ResponseEntity<Learner> updateStyles(
            @PathVariable Long id,
            @RequestBody StyleUpdateRequest body) {

        var learner = learners.findById(id).orElseThrow();

        if (body.styleActiveReflective != null)
            learner.setStyleActiveReflective(body.styleActiveReflective.doubleValue());
        if (body.styleSensingIntuitive != null)
            learner.setStyleSensingIntuitive(body.styleSensingIntuitive.doubleValue());
        if (body.styleVisualVerbal != null)
            learner.setStyleVisualVerbal(body.styleVisualVerbal.doubleValue());
        if (body.styleSequentialGlobal != null)
            learner.setStyleSequentialGlobal(body.styleSequentialGlobal.doubleValue());

        learners.save(learner);
        return ResponseEntity.ok(learner);
    }

    /**
     * Get mastery data for all concepts for a learner
     * GET /api/learners/{id}/mastery
     * Returns: Map of concept ID -> mastery value (0.0-1.0)
     */
    @GetMapping("/{id}/mastery")
    public ResponseEntity<Map<Long, Double>> getMasteryData(@PathVariable Long id) {
        var learner = learners.findById(id).orElse(null);
        if (learner == null) {
            return ResponseEntity.notFound().build();
        }

        // Get all mastery records for this learner
        List<Mastery> masteryList = masteryRepo.findByLearner(learner);

        // Convert to map: conceptId -> mastery value
        Map<Long, Double> masteryMap = new HashMap<>();
        for (Mastery m : masteryList) {
            if (m.getConcept() != null) {
                masteryMap.put(m.getConcept().getId(), m.getMastery());
            }
        }

        return ResponseEntity.ok(masteryMap);
    }
}
