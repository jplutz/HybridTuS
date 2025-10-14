package org.example.protushybrid.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfig {

    private int mutatingRequests = 60;  // Per minute
    private int llmRequests = 10;        // Per minute
    private int generateRequests = 5;    // Per minute for generation endpoints

    // Store buckets per IP/user (in production you'd use a distributed cache)
    private final Map<String, Bucket> mutatingBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> llmBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> generateBuckets = new ConcurrentHashMap<>();

    /**
     * Get or create bucket for mutating endpoints (POST, PUT, PATCH, DELETE)
     */
    public Bucket resolveMutatingBucket(String key) {
        return mutatingBuckets.computeIfAbsent(key, k -> createBucket(mutatingRequests));
    }

    /**
     * Get or create bucket for LLM endpoints
     */
    public Bucket resolveLlmBucket(String key) {
        return llmBuckets.computeIfAbsent(key, k -> createBucket(llmRequests));
    }

    /**
     * Get or create bucket for generation endpoints (stricter limits)
     */
    public Bucket resolveGenerateBucket(String key) {
        return generateBuckets.computeIfAbsent(key, k -> createBucket(generateRequests));
    }

    private Bucket createBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.intervally(requestsPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    // Getters and setters for configuration properties
    public int getMutatingRequests() {
        return mutatingRequests;
    }

    public void setMutatingRequests(int mutatingRequests) {
        this.mutatingRequests = mutatingRequests;
    }

    public int getLlmRequests() {
        return llmRequests;
    }

    public void setLlmRequests(int llmRequests) {
        this.llmRequests = llmRequests;
    }

    public int getGenerateRequests() {
        return generateRequests;
    }

    public void setGenerateRequests(int generateRequests) {
        this.generateRequests = generateRequests;
    }
}
