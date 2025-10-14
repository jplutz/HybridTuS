package org.example.protushybrid.service.tracking;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.core.LearningObject;
import org.example.protushybrid.domain.tracking.Interaction;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.core.LearningObjectRepository;
import org.example.protushybrid.repository.tracking.InteractionRepository;
import org.example.protushybrid.service.mining.SequenceStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InteractionService anti-gaming filters.
 * Tests: duration filters (view ≥5s, graded ≥30s), bot detection,
 * retry bypass, rate limiting, mastery updates.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InteractionServiceTest {

    @Mock
    private InteractionRepository interactionRepo;
    @Mock
    private LearnerRepository learnerRepo;
    @Mock
    private LearningObjectRepository loRepo;
    @Mock
    private MasteryService masteryService;
    @Mock
    private SequenceStatsService statsService;

    @InjectMocks
    private InteractionService service;

    private Learner learner;
    private LearningObject lo;
    private Concept concept;

    @BeforeEach
    void setUp() {
        learner = new Learner();
        learner.setId(1L);

        concept = new Concept();
        concept.setId(10L);

        lo = new LearningObject();
        lo.setId(100L);
        lo.setConcept(concept);
        lo.setType("T");

        when(learnerRepo.findById(1L)).thenReturn(Optional.of(learner));
        when(loRepo.findById(100L)).thenReturn(Optional.of(lo));
    }

    // ========== DURATION FILTER TESTS ==========

    @Test
    @DisplayName("Duration filter: View interaction <5s rejected")
    void durationFilter_viewBelowMinimumRejected() {
        // Given: First interaction (not a retry) with duration < 5s
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(Collections.emptyList()); // Not a retry

        // When: duration = 3s (< 5s minimum for view)
        boolean result = service.record(1L, 100L, 0.8, null, 0, 3);

        // Then: Rejected
        assertThat(result).isFalse();
        verify(interactionRepo, never()).save(any(Interaction.class));
    }

    @Test
    @DisplayName("Duration filter: View interaction ≥5s accepted")
    void durationFilter_viewAboveMinimumAccepted() {
        // Given: First interaction with duration ≥ 5s
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(interactionRepo.save(any(Interaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: duration = 5s (exactly minimum)
        boolean result = service.record(1L, 100L, 0.8, null, 0, 5);

        // Then: Accepted
        assertThat(result).isTrue();
        verify(interactionRepo).save(any(Interaction.class));
    }

    @Test
    @DisplayName("Duration filter: Graded interaction <30s rejected")
    void durationFilter_gradedBelowMinimumRejected() {
        // Given: First graded interaction with duration < 30s
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // When: graded (scoreRaw=5) with duration = 20s (< 30s minimum)
        boolean result = service.record(1L, 100L, 1.0, (short) 5, 0, 20);

        // Then: Rejected
        assertThat(result).isFalse();
        verify(interactionRepo, never()).save(any(Interaction.class));
    }

    @Test
    @DisplayName("Duration filter: Graded interaction ≥30s accepted")
    void durationFilter_gradedAboveMinimumAccepted() {
        // Given: First graded interaction with duration ≥ 30s
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(interactionRepo.save(any(Interaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: graded with duration = 30s (exactly minimum)
        boolean result = service.record(1L, 100L, 1.0, (short) 5, 0, 30);

        // Then: Accepted
        assertThat(result).isTrue();
        verify(interactionRepo).save(any(Interaction.class));
    }

    @Test
    @DisplayName("Duration filter: Retry bypasses minimum duration")
    void durationFilter_retryBypassesMinimum() {
        // Given: Retry interaction (recent attempt exists)
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);

        Interaction previous = new Interaction();
        previous.setLearner(learner);
        previous.setLearningObject(lo);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(List.of(previous)); // Is a retry

        when(interactionRepo.save(any(Interaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: graded with duration = 1s (< 30s, but it's a retry)
        boolean result = service.record(1L, 100L, 1.0, (short) 5, 0, 1);

        // Then: Accepted (retry bypasses duration check)
        assertThat(result).isTrue();
        verify(interactionRepo).save(any(Interaction.class));
    }

    // ========== BOT DETECTION TESTS ==========

    @Test
    @DisplayName("Bot detection: ≥60 completions/hour rejected")
    void botDetection_threshold60Rejected() {
        // Given: Learner has 60 interactions in last hour
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(60L);

        // When
        boolean result = service.record(1L, 100L, 1.0, (short) 5, 0, 60);

        // Then: Rejected
        assertThat(result).isFalse();
        verify(interactionRepo, never()).save(any(Interaction.class));
    }

    @Test
    @DisplayName("Bot detection: <60 completions/hour accepted")
    void botDetection_belowThresholdAccepted() {
        // Given: Learner has 59 interactions in last hour
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(59L);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(interactionRepo.save(any(Interaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        boolean result = service.record(1L, 100L, 1.0, (short) 5, 0, 60);

        // Then: Accepted
        assertThat(result).isTrue();
        verify(interactionRepo).save(any(Interaction.class));
    }

    // ========== RATE LIMIT REPEATS TESTS ==========

    @Test
    @DisplayName("Rate limit: >20 repeats per hour rejected")
    void rateLimit_maxRepeatsRejected() {
        // Given: Learner has 20 recent attempts on this LO
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);

        // Create 20 interactions (at the limit)
        List<Interaction> interactions = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            interactions.add(new Interaction());
        }
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(interactions); // 20 attempts = max

        // When: Try 21st attempt
        boolean result = service.record(1L, 100L, 1.0, (short) 5, 0, 60);

        // Then: Rejected
        assertThat(result).isFalse();
        verify(interactionRepo, never()).save(any(Interaction.class));
    }

    @Test
    @DisplayName("Rate limit: ≤20 repeats per hour accepted")
    void rateLimit_belowMaxRepeatsAccepted() {
        // Given: Learner has 19 recent attempts on this LO
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);

        // Create 19 interactions (below limit)
        List<Interaction> interactions = new java.util.ArrayList<>();
        for (int i = 0; i < 19; i++) {
            interactions.add(new Interaction());
        }
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(interactions); // 19 attempts < max

        when(interactionRepo.save(any(Interaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: Try 20th attempt (within limit)
        boolean result = service.record(1L, 100L, 1.0, (short) 5, 0, 60);

        // Then: Accepted
        assertThat(result).isTrue();
        verify(interactionRepo).save(any(Interaction.class));
    }

    // ========== MASTERY UPDATE TESTS ==========

    @Test
    @DisplayName("Mastery update: Graded interaction triggers mastery update")
    void masteryUpdate_gradedTriggersUpdate() {
        // Given: Valid graded interaction
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(interactionRepo.save(any(Interaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: Record graded interaction with scoreRaw=5
        boolean result = service.record(1L, 100L, 1.0, (short) 5, 0, 60);

        // Then: Should call masteryService.updateMastery with normalized score 1.0
        assertThat(result).isTrue();
        verify(masteryService).updateMastery(eq(1L), eq(10L), eq(1.0));
    }

    @Test
    @DisplayName("Mastery update: View interaction does NOT trigger mastery update")
    void masteryUpdate_viewDoesNotTriggerUpdate() {
        // Given: Valid view interaction (scoreRaw = null)
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(interactionRepo.save(any(Interaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: Record view interaction (scoreRaw = null)
        boolean result = service.record(1L, 100L, 0.8, null, 0, 10);

        // Then: Should NOT call masteryService.updateMastery
        assertThat(result).isTrue();
        verify(masteryService, never()).updateMastery(anyLong(), anyLong(), anyDouble());
    }

    @Test
    @DisplayName("Mastery update: Partial score (scoreRaw=3) updates mastery")
    void masteryUpdate_partialScoreUpdates() {
        // Given: Valid graded interaction with partial score
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(interactionRepo.save(any(Interaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: Record with scoreRaw=3
        boolean result = service.record(1L, 100L, 0.5, (short) 3, 1, 60);

        // Then: Should update mastery with normalized score 0.5
        assertThat(result).isTrue();
        verify(masteryService).updateMastery(eq(1L), eq(10L), eq(0.5));
    }

    // ========== INTERACTION RECORDING TESTS ==========

    @Test
    @DisplayName("Record: Graded interaction saves action=COMPLETE")
    void record_gradedSavesComplete() {
        // Given
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(interactionRepo.save(any(Interaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: Graded interaction (scoreRaw != null)
        service.record(1L, 100L, 1.0, (short) 5, 2, 60);

        // Then: Should save with action=COMPLETE
        verify(interactionRepo).save(argThat(inter ->
                "COMPLETE".equals(inter.getAction()) &&
                inter.getScoreRaw() == 5 &&
                inter.getHintCount() == 2 &&
                inter.getDurationSeconds() == 60
        ));
    }

    @Test
    @DisplayName("Record: View interaction saves action=VIEW")
    void record_viewSavesView() {
        // Given
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(interactionRepo.save(any(Interaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: View interaction (scoreRaw = null)
        service.record(1L, 100L, 0.8, null, 0, 10);

        // Then: Should save with action=VIEW
        verify(interactionRepo).save(argThat(inter ->
                "VIEW".equals(inter.getAction()) &&
                inter.getScoreRaw() == null &&
                inter.getDurationSeconds() == 10
        ));
    }

    @Test
    @DisplayName("Record: Sequence stats updated after interaction")
    void record_sequenceStatsUpdated() {
        // Given
        when(interactionRepo.countByLearnerIdAndTimestampAfter(eq(1L), any(Instant.class)))
                .thenReturn(0L);
        when(interactionRepo.findRecentByLearnerAndLO(eq(1L), eq(100L), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(interactionRepo.save(any(Interaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        service.record(1L, 100L, 1.0, (short) 5, 0, 60);

        // Then: Should update sequence stats
        verify(statsService).updateStats(eq(1L), eq(10L), eq("T"));
    }

    // ========== AVERAGE CALCULATION TESTS ==========

    @Test
    @DisplayName("Average: Empty history returns 0.0")
    void average_emptyReturnsZero() {
        when(interactionRepo.findLast10ResultsByLearnerId(1L))
                .thenReturn(Collections.emptyList());

        double avg = service.calculateAverageLast10(1L);

        assertThat(avg).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Average: Calculates correct average of last 10")
    void average_calculatesCorrectly() {
        // Given: 10 interactions with scores [1.0, 0.8, 0.6, 0.4, 0.2, 1.0, 0.8, 0.6, 0.4, 0.2]
        List<Interaction> interactions = List.of(
                createInteractionWithScore(1.0),
                createInteractionWithScore(0.8),
                createInteractionWithScore(0.6),
                createInteractionWithScore(0.4),
                createInteractionWithScore(0.2),
                createInteractionWithScore(1.0),
                createInteractionWithScore(0.8),
                createInteractionWithScore(0.6),
                createInteractionWithScore(0.4),
                createInteractionWithScore(0.2)
        );
        when(interactionRepo.findLast10ResultsByLearnerId(1L)).thenReturn(interactions);

        // When
        double avg = service.calculateAverageLast10(1L);

        // Then: Average = (1.0 + 0.8 + 0.6 + 0.4 + 0.2 + 1.0 + 0.8 + 0.6 + 0.4 + 0.2) / 10 = 6.0 / 10 = 0.6
        assertThat(avg).isEqualTo(0.6);
    }

    @Test
    @DisplayName("Average: Handles null scores as 0.0")
    void average_handlesNullScores() {
        List<Interaction> interactions = List.of(
                createInteractionWithScore(1.0),
                createInteractionWithScore(null),
                createInteractionWithScore(0.5)
        );
        when(interactionRepo.findLast10ResultsByLearnerId(1L)).thenReturn(interactions);

        // When
        double avg = service.calculateAverageLast10(1L);

        // Then: Average = (1.0 + 0.0 + 0.5) / 3 = 0.5
        assertThat(avg).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
    }

    // ========== HELPER ==========

    private Interaction createInteractionWithScore(Double score) {
        Interaction inter = new Interaction();
        inter.setScore(score);
        return inter;
    }
}
