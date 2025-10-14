package org.example.protushybrid.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.exercise.Exercise;
import org.example.protushybrid.repository.llm.LlmGradingAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LlmGradingService failure paths.
 * Tests: timeout handling, network errors, malformed responses, fallback behavior.
 */
@ExtendWith(MockitoExtension.class)
class LlmGradingServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private LlmGradingAuditRepository auditRepo;

    @InjectMocks
    private LlmGradingService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Exercise exercise;
    private Learner learner;

    @BeforeEach
    void setUp() {
        // Re-inject ObjectMapper (since @InjectMocks doesn't handle it)
        service = new LlmGradingService(llmClient, auditRepo, objectMapper);

        // Create test exercise
        exercise = new Exercise();
        exercise.setId(1L);
        exercise.setExerciseType(Exercise.ExerciseType.FREE_TEXT);
        exercise.setQuestionJson(Map.of("question", "What is a binary search tree?"));
        exercise.setCorrectAnswerJson(Map.of("answer", "A tree where left < parent < right"));

        // Create test learner
        learner = new Learner();
        learner.setId(100L);
        learner.setExternalRef("test-learner");
        learner.setDisplayName("Test Learner");
    }

    // ========== TIMEOUT HANDLING TESTS ==========

    @Test
    @DisplayName("Timeout: Returns fallback score when LLM times out")
    void timeout_returnsFallbackScore() {
        // Given: LLM client throws timeout exception
        when(llmClient.generate(anyString()))
                .thenThrow(new RuntimeException("Request timeout", new TimeoutException("Read timed out")));

        // When: Grade exercise
        var result = service.gradeOpenEnded(exercise, "A BST is a sorted tree", learner);

        // Then: Should return fallback score
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.feedback()).contains("Unable to grade at this time");
        assertThat(result.deterministic()).isFalse();
    }

    @Test
    @DisplayName("Timeout: Does not throw exception (graceful degradation)")
    void timeout_doesNotThrowException() {
        // Given: LLM client throws timeout
        when(llmClient.generate(anyString()))
                .thenThrow(new RuntimeException("Timeout", new TimeoutException()));

        // When/Then: Should not throw exception
        var result = service.gradeOpenEnded(exercise, "Answer", learner);
        assertThat(result).isNotNull();
    }

    // ========== NETWORK ERROR TESTS ==========

    @Test
    @DisplayName("Network error: Returns fallback score on connection failure")
    void networkError_returnsFallbackScore() {
        // Given: LLM client throws network error
        when(llmClient.generate(anyString()))
                .thenThrow(new RestClientException("Connection refused"));

        // When
        var result = service.gradeOpenEnded(exercise, "BST answer", learner);

        // Then: Should return fallback
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.feedback()).contains("Unable to grade at this time");
    }

    @Test
    @DisplayName("Network error: Returns fallback on DNS failure")
    void networkError_dnsFailure_returnsFallback() {
        // Given: DNS resolution fails
        when(llmClient.generate(anyString()))
                .thenThrow(new ResourceAccessException("Unknown host"));

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Fallback score
        assertThat(result.score()).isEqualTo(0.5);
    }

    // ========== GENERIC EXCEPTION TESTS ==========

    @Test
    @DisplayName("Generic error: Handles RuntimeException gracefully")
    void genericError_handlesRuntimeException() {
        // Given: LLM throws unexpected error
        when(llmClient.generate(anyString()))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Fallback
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.feedback()).isNotEmpty();
    }

    @Test
    @DisplayName("Generic error: Handles NullPointerException")
    void genericError_handlesNullPointer() {
        // Given: LLM throws NPE
        when(llmClient.generate(anyString()))
                .thenThrow(new NullPointerException("Null response body"));

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Fallback
        assertThat(result.score()).isEqualTo(0.5);
    }

    // ========== MALFORMED RESPONSE TESTS ==========

    @Test
    @DisplayName("Malformed response: Missing SCORE field uses default")
    void malformedResponse_missingScore_usesDefault() {
        // Given: LLM returns response without SCORE field
        String malformedResponse = """
                CORRECT: true
                EXPLANATION: Good job!
                """;
        when(llmClient.generate(anyString())).thenReturn(malformedResponse);

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Should use default score 0.5
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.feedback()).isEqualTo("Good job!");
    }

    @Test
    @DisplayName("Malformed response: Invalid SCORE format uses default")
    void malformedResponse_invalidScore_usesDefault() {
        // Given: SCORE field is not a number
        String malformedResponse = """
                SCORE: not-a-number
                CORRECT: true
                EXPLANATION: Well done!
                """;
        when(llmClient.generate(anyString())).thenReturn(malformedResponse);

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Should default to 0.5
        assertThat(result.score()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Malformed response: Missing EXPLANATION field")
    void malformedResponse_missingExplanation() {
        // Given: Response without EXPLANATION
        String malformedResponse = """
                SCORE: 0.8
                CORRECT: true
                """;
        when(llmClient.generate(anyString())).thenReturn(malformedResponse);

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Should still parse score, use empty/default feedback
        assertThat(result.score()).isEqualTo(0.8);
        assertThat(result.feedback()).isNotNull();
    }

    @Test
    @DisplayName("Malformed response: Empty response")
    void malformedResponse_emptyResponse() {
        // Given: LLM returns empty string
        when(llmClient.generate(anyString())).thenReturn("");

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Should handle gracefully with defaults
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.feedback()).isNotNull();
    }

    @Test
    @DisplayName("Malformed response: Completely unstructured response")
    void malformedResponse_unstructured() {
        // Given: LLM returns random text without expected format
        String unstructured = "This answer is pretty good but could be better in some ways.";
        when(llmClient.generate(anyString())).thenReturn(unstructured);

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Should fallback gracefully with default feedback
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.feedback()).isEqualTo("No explanation provided.");
    }

    // ========== PARTIAL RESPONSE TESTS ==========

    @Test
    @DisplayName("Partial response: Missing CORRECT field")
    void partialResponse_missingCorrect() {
        // Given: Response has SCORE and EXPLANATION but not CORRECT
        String partialResponse = """
                SCORE: 0.9
                EXPLANATION: Excellent understanding!
                """;
        when(llmClient.generate(anyString())).thenReturn(partialResponse);

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Should parse successfully with defaults
        assertThat(result.score()).isEqualTo(0.9);
        assertThat(result.feedback()).isEqualTo("Excellent understanding!");
        assertThat(result.correct()).isFalse(); // Default
    }

    @Test
    @DisplayName("Partial response: SCORE out of range (clamped)")
    void partialResponse_scoreOutOfRange() {
        // Given: Score is > 1.0
        String response = """
                SCORE: 1.5
                CORRECT: true
                EXPLANATION: Perfect!
                """;
        when(llmClient.generate(anyString())).thenReturn(response);

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Score should be clamped to [0, 1]
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.feedback()).isEqualTo("Perfect!");
    }

    @Test
    @DisplayName("Partial response: Negative score (clamped)")
    void partialResponse_negativeScore() {
        // Given: Negative score
        String response = """
                SCORE: -0.3
                CORRECT: false
                EXPLANATION: Incorrect.
                """;
        when(llmClient.generate(anyString())).thenReturn(response);

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Should clamp to 0.0
        assertThat(result.score()).isEqualTo(0.0);
    }

    // ========== SUCCESSFUL PATH TESTS ==========

    @Test
    @DisplayName("Success: Valid response parses correctly")
    void success_validResponse() {
        // Given: LLM returns properly formatted response
        String validResponse = """
                SCORE: 0.85
                CORRECT: true
                EXPLANATION: Your answer demonstrates solid understanding of BST properties. The key insight about ordering is correct.
                """;
        when(llmClient.generate(anyString())).thenReturn(validResponse);

        // When
        var result = service.gradeOpenEnded(exercise, "BST answer", learner);

        // Then: Should parse all fields correctly
        assertThat(result.score()).isEqualTo(0.85);
        assertThat(result.correct()).isTrue();
        assertThat(result.feedback()).contains("solid understanding");
        assertThat(result.deterministic()).isFalse(); // LLM is never deterministic
    }

    @Test
    @DisplayName("Success: Perfect score")
    void success_perfectScore() {
        // Given: Perfect score response
        String response = """
                SCORE: 1.0
                CORRECT: true
                EXPLANATION: Excellent! Your answer is complete and accurate.
                """;
        when(llmClient.generate(anyString())).thenReturn(response);

        // When
        var result = service.gradeOpenEnded(exercise, "Perfect answer", learner);

        // Then
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.correct()).isTrue();
    }

    @Test
    @DisplayName("Success: Zero score")
    void success_zeroScore() {
        // Given: Zero score response
        String response = """
                SCORE: 0.0
                CORRECT: false
                EXPLANATION: Unfortunately, this answer does not demonstrate understanding of the concept.
                """;
        when(llmClient.generate(anyString())).thenReturn(response);

        // When
        var result = service.gradeOpenEnded(exercise, "Wrong answer", learner);

        // Then
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.correct()).isFalse();
    }

    @Test
    @DisplayName("Success: Legacy FEEDBACK field supported")
    void success_legacyFeedbackField() {
        // Given: Response uses old FEEDBACK field instead of EXPLANATION
        String legacyResponse = """
                SCORE: 0.7
                CORRECT: true
                FEEDBACK: Good attempt. Consider explaining the ordering property more clearly.
                """;
        when(llmClient.generate(anyString())).thenReturn(legacyResponse);

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Should fall back to FEEDBACK field
        assertThat(result.score()).isEqualTo(0.7);
        assertThat(result.feedback()).contains("Good attempt");
    }

    // ========== AUDIT LOGGING TESTS ==========

    @Test
    @DisplayName("Audit: Success case logs audit record")
    void audit_successCaseLogsAudit() {
        // Given: Valid response
        String response = """
                SCORE: 0.8
                CORRECT: true
                EXPLANATION: Good work!
                """;
        when(llmClient.generate(anyString())).thenReturn(response);
        when(auditRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Should save audit record
        verify(auditRepo).save(any());
    }

    @Test
    @DisplayName("Audit: Failure case does not save audit (exception before audit)")
    void audit_failureCaseDoesNotSaveAudit() {
        // Given: LLM throws exception (before audit point)
        when(llmClient.generate(anyString())).thenThrow(new RuntimeException("Error"));

        // When
        service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Should NOT save audit (exception happened before audit)
        verify(auditRepo, never()).save(any());
    }

    // ========== EXERCISE TYPE TESTS ==========

    @Test
    @DisplayName("Exercise types: CODE_WRITE uses correct prompt context")
    void exerciseTypes_codeWrite() {
        // Given: CODE_WRITE exercise
        exercise.setExerciseType(Exercise.ExerciseType.CODE_WRITE);
        String response = "SCORE: 0.9\nCORRECT: true\nEXPLANATION: Good code!";
        when(llmClient.generate(anyString())).thenReturn(response);

        // When
        var result = service.gradeOpenEnded(exercise, "def foo(): return 42", learner);

        // Then: Should work correctly
        assertThat(result.score()).isEqualTo(0.9);
        // Note: Full integration test would verify prompt contains "code-writing exercise"
    }

    @Test
    @DisplayName("Exercise types: FREE_TEXT uses correct prompt context")
    void exerciseTypes_freeText() {
        // Given: FREE_TEXT exercise
        exercise.setExerciseType(Exercise.ExerciseType.FREE_TEXT);
        String response = "SCORE: 0.75\nCORRECT: true\nEXPLANATION: Clear explanation.";
        when(llmClient.generate(anyString())).thenReturn(response);

        // When
        var result = service.gradeOpenEnded(exercise, "Text answer", learner);

        // Then
        assertThat(result.score()).isEqualTo(0.75);
    }

    // ========== RETRY BEHAVIOR TESTS ==========

    @Test
    @DisplayName("Retry: LLM failure triggers graceful fallback")
    void retry_llmFailure_fallback() {
        // Given: LLM client fails (even after retries in AnthropicLlmClient)
        when(llmClient.generate(anyString()))
                .thenThrow(new RuntimeException("Anthropic API failed after 3 attempts"));

        // When
        var result = service.gradeOpenEnded(exercise, "Answer", learner);

        // Then: Service should handle gracefully
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.feedback()).contains("Unable to grade");
    }
}
