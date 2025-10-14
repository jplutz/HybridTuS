package org.example.protushybrid.controller.exercise;

import org.example.protushybrid.service.exercise.GradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GradeController {

    private final GradingService gradingService;
    public GradeController(GradingService gradingService) {
        this.gradingService = gradingService;
    }

    public record GradeRequest(Long exerciseId, Long learnerId, String userAnswer, Boolean explainReasoning) {}

    @PostMapping("/grade")
    public ResponseEntity<?> grade(@RequestBody GradeRequest req) {
        var res = gradingService.gradeSubmission(
                req.exerciseId(),
                req.learnerId(),
                req.userAnswer(),
                req.explainReasoning() != null && req.explainReasoning()
        );
        return ResponseEntity.ok(res);
    }
}
