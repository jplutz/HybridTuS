package org.example.protushybrid.controller.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class StatusController {

    @GetMapping("/api/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "status", "running",
                "version", "0.2",
                "app", "Protus Hybrid Prototype"
        );
    }
}
