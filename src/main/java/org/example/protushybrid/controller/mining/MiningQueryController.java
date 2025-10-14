package org.example.protushybrid.controller.mining;

import org.example.protushybrid.domain.mining.MinedSupport;
import org.example.protushybrid.repository.mining.MinedSupportRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mining")
public class MiningQueryController {

    private final MinedSupportRepository minedRepo;

    public MiningQueryController(MinedSupportRepository minedRepo) {
        this.minedRepo = minedRepo;
    }

    public record RecommendationDto(String nextType, Double support, Integer nSupport, String source) {}

    @GetMapping("/recommend")
    public ResponseEntity<RecommendationDto> recommend(
            @RequestParam String clusterKey,
            @RequestParam Long conceptId,
            @RequestParam String suffix
    ) {
        String sfx = suffix.toUpperCase();
        List<MinedSupport> rows = minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(clusterKey, conceptId, sfx);
        String source = "CLUSTER";
        if (rows.isEmpty()) {
            rows = minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc("GLOBAL", conceptId, sfx);
            source = "GLOBAL";
        }
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        var top = rows.get(0);
        return ResponseEntity.ok(new RecommendationDto(top.getNextType(), top.getSupport(), top.getNSupport(), source));
    }
}
