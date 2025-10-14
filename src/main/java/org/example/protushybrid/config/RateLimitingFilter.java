package org.example.protushybrid.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(2) // Run after ApiTokenFilter (which is Order 1 by default)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitConfig rateLimitConfig;

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    public RateLimitingFilter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String method = request.getMethod();
        String path = request.getRequestURI();

        // Skip rate limiting for GET requests and actuator endpoints
        if ("GET".equals(method) || path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only rate limit mutating methods
        if (!MUTATING_METHODS.contains(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Use IP address as key (in production, you might use user ID)
        String key = getClientIP(request);

        // Determine which bucket to use based on the endpoint
        Bucket bucket;
        String limitType;

        if (isLlmEndpoint(path)) {
            // Strictest limit for LLM endpoints (chat, grading with explanation)
            bucket = rateLimitConfig.resolveLlmBucket(key);
            limitType = "LLM";
        } else if (isGenerateEndpoint(path)) {
            // Strict limit for generation endpoints
            bucket = rateLimitConfig.resolveGenerateBucket(key);
            limitType = "Generate";
        } else {
            // Default limit for other mutating endpoints
            bucket = rateLimitConfig.resolveMutatingBucket(key);
            limitType = "Mutating";
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Add rate limit headers
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            response.addHeader("X-Rate-Limit-Type", limitType);
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
            response.getWriter().write(String.format(
                    "{\"error\":\"Rate limit exceeded\",\"type\":\"%s\",\"retryAfterSeconds\":%d}",
                    limitType,
                    waitForRefill
            ));
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    /**
     * Check if endpoint uses LLM (chat, grading)
     */
    private boolean isLlmEndpoint(String path) {
        return path.equals("/api/chat") ||
               path.equals("/api/grade") ||
               path.startsWith("/api/exercises/grade");
    }

    /**
     * Check if endpoint is for generation (exercises, practice data)
     */
    private boolean isGenerateEndpoint(String path) {
        return path.contains("/generate") ||
               path.contains("/data/generate");
    }
}
