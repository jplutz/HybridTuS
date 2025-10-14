package org.example.protushybrid.service.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Stub implementation. returns deterministic placeholder output. */
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "stub", matchIfMissing = true)
public class StubLlmClient implements LlmClient {

    @Override
    public String generate(String prompt) {
        return "[Stub response] " + prompt.substring(0, Math.min(60, prompt.length()));
    }
}
