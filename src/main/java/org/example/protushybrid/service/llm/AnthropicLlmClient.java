package org.example.protushybrid.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude API client implementation.
 * Uses Messages API with structured outputs via JSON mode.
 */
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic")
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final int MAX_RETRIES = 2;

    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final int timeoutMs;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AnthropicLlmClient(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model:claude-3-7-sonnet-20250219}") String model,
            @Value("${llm.temperature:0.0}") double temperature,
            @Value("${llm.max-tokens:2048}") int maxTokens,
            @Value("${llm.timeout-ms:10000}") int timeoutMs,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutMs = timeoutMs;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(String prompt) {
        return generateWithRetry(prompt, null, 0);
    }

    /**
     * Generate with structured JSON output
     */
    public String generateStructured(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        return generateWithRetry(userPrompt, systemPrompt, 0, jsonSchema);
    }

    private String generateWithRetry(String prompt, String systemPrompt, int attempt) {
        return generateWithRetry(prompt, systemPrompt, attempt, null);
    }

    private String generateWithRetry(String prompt, String systemPrompt, int attempt, Map<String, Object> jsonSchema) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "temperature", temperature,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    )
            );

            // Add system prompt if provided
            if (systemPrompt != null) {
                requestBody = new java.util.HashMap<>(requestBody);
                requestBody.put("system", systemPrompt);
            }

            // Add JSON schema constraint if provided (for structured outputs)
            if (jsonSchema != null) {
                requestBody = new java.util.HashMap<>(requestBody);
                requestBody.put("tools", List.of(Map.of(
                        "name", "structured_output",
                        "description", "Generate structured JSON output",
                        "input_schema", jsonSchema
                )));
                requestBody.put("tool_choice", Map.of("type", "tool", "name", "structured_output"));
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, entity, String.class);
            long duration = System.currentTimeMillis() - startTime;

            log.debug("Anthropic API call completed in {}ms", duration);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return extractContent(response.getBody(), jsonSchema != null);
            } else {
                throw new RuntimeException("Unexpected response: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Anthropic API error (attempt {}/{}): {}", attempt + 1, MAX_RETRIES + 1, e.getMessage());

            if (attempt < MAX_RETRIES) {
                log.info("Retrying Anthropic API call...");
                try {
                    Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return generateWithRetry(prompt, systemPrompt, attempt + 1, jsonSchema);
            }

            throw new RuntimeException("Anthropic API failed after " + (MAX_RETRIES + 1) + " attempts", e);
        }
    }

    private String extractContent(String responseBody, boolean isStructured) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");

            if (content.isArray() && content.size() > 0) {
                JsonNode firstContent = content.get(0);

                // For structured output (tool use)
                if (isStructured && "tool_use".equals(firstContent.path("type").asText())) {
                    return objectMapper.writeValueAsString(firstContent.path("input"));
                }

                // For regular text output
                if ("text".equals(firstContent.path("type").asText())) {
                    return firstContent.path("text").asText();
                }
            }

            throw new RuntimeException("Unexpected response structure");

        } catch (Exception e) {
            log.error("Failed to parse Anthropic response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse LLM response", e);
        }
    }

    // Legacy comprehensive lesson generation methods removed
    // (Previously used SequenceTemplate which has been deleted)
}
