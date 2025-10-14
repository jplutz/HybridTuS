package org.example.protushybrid.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiTokenFilter.
 * Tests: valid token, missing token, invalid token, actuator bypass, error format.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiTokenFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ApiTokenFilter filter;

    private StringWriter responseWriter;
    private static final String VALID_TOKEN = "test-token-12345";

    @BeforeEach
    void setUp() throws IOException {
        // Set the api.token field using reflection
        ReflectionTestUtils.setField(filter, "apiToken", VALID_TOKEN);

        // Mock response writer
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    // ========== VALID TOKEN TESTS ==========

    @Test
    @DisplayName("Valid token: Request proceeds through filter chain")
    void validToken_requestProceeds() throws ServletException, IOException {
        // Given: Request with valid token
        when(request.getRequestURI()).thenReturn("/api/recommendations/next");
        when(request.getHeader("X-API-Token")).thenReturn(VALID_TOKEN);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: Filter chain continues, no status set
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    // ========== MISSING TOKEN TESTS ==========

    @Test
    @DisplayName("Missing token: Returns 403 Forbidden")
    void missingToken_returns403() throws ServletException, IOException {
        // Given: Request without token header
        when(request.getRequestURI()).thenReturn("/api/recommendations/next");
        when(request.getHeader("X-API-Token")).thenReturn(null);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: Should return 403 and not proceed
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Missing token: Returns JSON error message")
    void missingToken_returnsJsonError() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/recommendations/next");
        when(request.getHeader("X-API-Token")).thenReturn(null);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: Error message in JSON format
        verify(response).setContentType("application/json");
        String responseBody = responseWriter.toString();
        assertThat(responseBody).isEqualTo("{\"error\":\"Invalid or missing API token\"}");
    }

    // ========== INVALID TOKEN TESTS ==========

    @Test
    @DisplayName("Invalid token: Returns 403 Forbidden")
    void invalidToken_returns403() throws ServletException, IOException {
        // Given: Request with wrong token
        when(request.getRequestURI()).thenReturn("/api/recommendations/next");
        when(request.getHeader("X-API-Token")).thenReturn("wrong-token");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: Should return 403
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Invalid token: Returns JSON error message")
    void invalidToken_returnsJsonError() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/recommendations/next");
        when(request.getHeader("X-API-Token")).thenReturn("wrong-token");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: Error message
        String responseBody = responseWriter.toString();
        assertThat(responseBody).isEqualTo("{\"error\":\"Invalid or missing API token\"}");
    }

    // ========== ACTUATOR BYPASS TESTS ==========

    @Test
    @DisplayName("Actuator bypass: Health endpoint skips token check")
    void actuatorBypass_healthEndpoint() throws ServletException, IOException {
        // Given: Request to /actuator/health (no token)
        when(request.getRequestURI()).thenReturn("/actuator/health");
        when(request.getHeader("X-API-Token")).thenReturn(null);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: Should proceed without token validation
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Actuator bypass: Metrics endpoint skips token check")
    void actuatorBypass_metricsEndpoint() throws ServletException, IOException {
        // Given: Request to /actuator/metrics (no token)
        when(request.getRequestURI()).thenReturn("/actuator/metrics");
        when(request.getHeader("X-API-Token")).thenReturn(null);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: Should proceed
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Actuator bypass: Does NOT bypass for non-actuator paths")
    void actuatorBypass_doesNotBypassApiPaths() throws ServletException, IOException {
        // Given: Request to /api/actuator-like-path (NOT actuator, no token)
        when(request.getRequestURI()).thenReturn("/api/actuator-like-path");
        when(request.getHeader("X-API-Token")).thenReturn(null);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: Should NOT proceed (token required)
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(request, response);
    }

    // ========== DIFFERENT API PATHS TESTS ==========

    @Test
    @DisplayName("API paths: All require valid token")
    void apiPaths_requireToken() throws ServletException, IOException {
        String[] paths = {
                "/api/recommendations/next",
                "/api/interactions",
                "/api/concepts",
                "/api/learners/1"
        };

        for (String path : paths) {
            // Reset mocks for each iteration
            reset(request, response, filterChain);
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

            // Given: Path with valid token
            when(request.getRequestURI()).thenReturn(path);
            when(request.getHeader("X-API-Token")).thenReturn(VALID_TOKEN);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then: Should proceed
            verify(filterChain).doFilter(request, response);
            verify(response, never()).setStatus(anyInt());
        }
    }

    @Test
    @DisplayName("Token case sensitivity: Token must match exactly")
    void tokenCaseSensitivity_exactMatch() throws ServletException, IOException {
        // Given: Token with different case
        when(request.getRequestURI()).thenReturn("/api/recommendations/next");
        when(request.getHeader("X-API-Token")).thenReturn("TEST-TOKEN-12345"); // Wrong case

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: Should reject (case-sensitive)
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Token whitespace: Token must not have leading/trailing spaces")
    void tokenWhitespace_noTrimming() throws ServletException, IOException {
        // Given: Token with extra whitespace
        when(request.getRequestURI()).thenReturn("/api/recommendations/next");
        when(request.getHeader("X-API-Token")).thenReturn(" test-token-12345 "); // Extra spaces

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: Should reject (no automatic trimming)
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(request, response);
    }
}
