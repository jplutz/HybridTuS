package org.example.protushybrid.controller.admin;

import org.example.protushybrid.domain.core.GeneratedLo;
import org.example.protushybrid.repository.core.GeneratedLoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/generate")
public class GenerateController {
    private final GeneratedLoRepository generatedRepo;
    public GenerateController(GeneratedLoRepository generatedRepo) {
        this.generatedRepo = generatedRepo;
    }

    public record PracticeRequest(Long conceptId, Long baseLoId, String prompt) {}
    public record PracticeResponse(Long id) {}

    @PostMapping("/practice")
    public ResponseEntity<PracticeResponse> generatePractice(@RequestBody PracticeRequest req) {
        Map<String, Object> content = new HashMap<>();
        content.put("type", "MC");
        content.put("question", "Select the correct option.");
        content.put("options", new String[]{ "A", "B", "C", "D" });
        content.put("answer", "A");

        GeneratedLo row = new GeneratedLo();
        row.setConceptId(req.conceptId());
        row.setBaseLoId(req.baseLoId());
        row.setType("ACTIVITY");
        row.setContentJson(content);
        row.setPromptHash(sha256(req.prompt() == null ? "" : req.prompt()));
        generatedRepo.save(row);
        return ResponseEntity.ok(new PracticeResponse(row.getId()));
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
