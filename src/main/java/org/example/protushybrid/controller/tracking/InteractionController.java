package org.example.protushybrid.controller.tracking;

import org.example.protushybrid.controller.dto.InteractionRequest;
import org.example.protushybrid.service.tracking.InteractionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

    private final InteractionService service;

    public InteractionController(InteractionService service) {
        this.service = service;
    }

    /**
     * Record an interaction event.
     * Returns 200 if recorded, 202 if filtered out by anti-gaming rules.
     */
    @PostMapping
    public ResponseEntity<String> record(@RequestBody InteractionRequest req) {
        boolean recorded = service.record(
                req.learnerId,
                req.loId,
                req.conceptId,
                req.score,
                req.scoreRaw,
                req.hintCount,
                req.durationSec
        );

        if (recorded) {
            return ResponseEntity.ok("Interaction recorded");
        } else {
            return ResponseEntity.status(202).body("Interaction filtered (anti-gaming)");
        }
    }

}
