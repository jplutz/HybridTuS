package org.example.protushybrid.service.tracking;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.core.LearningSession;
import org.example.protushybrid.domain.tracking.Mastery;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.core.LearningSessionRepository;
import org.example.protushybrid.repository.tracking.MasteryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MasteryService EWMA calculations and thresholds.
 * Tests: EWMA formula, score mapping, threshold bands, event count minimum.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MasteryServiceTest {

    @Mock
    private LearnerRepository learnerRepo;

    @Mock
    private ConceptRepository conceptRepo;

    @Mock
    private MasteryRepository masteryRepo;

    @Mock
    private LearningSessionRepository sessionRepo;

    @InjectMocks
    private MasteryService service;

    private Learner learner;
    private Concept concept;
    private Mastery.Pk pk;

    @BeforeEach
    void setUp() {
        learner = new Learner();
        learner.setId(1L);

        concept = new Concept();
        concept.setId(10L);

        pk = new Mastery.Pk();
        pk.setLearnerId(1L);
        pk.setConceptId(10L);

        when(learnerRepo.findById(1L)).thenReturn(Optional.of(learner));
        when(conceptRepo.findById(10L)).thenReturn(Optional.of(concept));
        when(sessionRepo.findAll()).thenReturn(new ArrayList<>()); // Default: no sessions
    }

    // ========== EWMA UPDATE TESTS ==========

    @Test
    @DisplayName("EWMA: Initial mastery starts at 0.0")
    void ewma_initialMasteryIsZero() {
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.empty());
        when(masteryRepo.save(any(Mastery.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateMastery(1L, 10L, 1.0); // Perfect score (normalized)

        ArgumentCaptor<Mastery> captor = ArgumentCaptor.forClass(Mastery.class);
        verify(masteryRepo).save(captor.capture());

        Mastery saved = captor.getValue();
        // α=0.4, score=1.0, prev=0.0 → new = 0.4*1.0 + 0.6*0.0 = 0.4
        assertThat(saved.getMastery()).isCloseTo(0.4, within(0.001));
        assertThat(saved.getEventCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("EWMA: Perfect score sequence reaches mastery")
    void ewma_perfectScoreSequence() {
        // Given: α=0.4, start at 0.0
        // Expected after perfect scores:
        // After 1: 0.4
        // After 2: 0.4*1.0 + 0.6*0.4 = 0.64
        // After 3: 0.4*1.0 + 0.6*0.64 = 0.784
        // After 4: 0.4*1.0 + 0.6*0.784 = 0.8704 (MASTERED)

        Mastery m = createMastery(0.0, 0);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // First score=1.0 (perfect)
        service.updateMastery(1L, 10L, 1.0);
        assertThat(m.getMastery()).isCloseTo(0.4, within(0.001));

        // Second score=1.0
        service.updateMastery(1L, 10L, 1.0);
        assertThat(m.getMastery()).isCloseTo(0.64, within(0.001));

        // Third score=1.0
        service.updateMastery(1L, 10L, 1.0);
        assertThat(m.getMastery()).isCloseTo(0.784, within(0.001));

        // Fourth score=1.0
        service.updateMastery(1L, 10L, 1.0);
        assertThat(m.getMastery()).isCloseTo(0.8704, within(0.001));
        assertThat(m.getEventCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("EWMA: Partial score (0.5) gives proportional outcome")
    void ewma_partialScoreGivesHalfOutcome() {
        Mastery m = createMastery(0.0, 0);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateMastery(1L, 10L, 0.5); // Partial score (normalized)

        // α=0.4, score=0.5, prev=0.0 → new = 0.4*0.5 + 0.6*0.0 = 0.2
        assertThat(m.getMastery()).isCloseTo(0.2, within(0.001));
    }

    @Test
    @DisplayName("EWMA: Failure (score=0.0) gives 0.0 outcome")
    void ewma_failureGivesZeroOutcome() {
        Mastery m = createMastery(0.8, 3);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateMastery(1L, 10L, 0.0); // Failure (normalized)

        // α=0.4, score=0.0, prev=0.8 → new = 0.4*0.0 + 0.6*0.8 = 0.48
        assertThat(m.getMastery()).isCloseTo(0.48, within(0.001));
    }

    @Test
    @DisplayName("EWMA: Mixed scores converge correctly")
    void ewma_mixedScoresConverge() {
        Mastery m = createMastery(0.0, 0);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Success (1.0), Fail (0.0), Success (1.0)
        service.updateMastery(1L, 10L, 1.0); // 0.4
        service.updateMastery(1L, 10L, 0.0); // 0.4*0.0 + 0.6*0.4 = 0.24
        service.updateMastery(1L, 10L, 1.0); // 0.4*1.0 + 0.6*0.24 = 0.544

        assertThat(m.getMastery()).isCloseTo(0.544, within(0.001));
        assertThat(m.getEventCount()).isEqualTo(3);
    }

    // ========== SCORE MAPPING TESTS ==========

    @Test
    @DisplayName("Score mapping: 1.0 scores map to 1.0 (success)")
    void scoreMapping_perfectScores() {
        Mastery m = createMastery(0.0, 0);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateMastery(1L, 10L, 1.0);
        double after1 = m.getMastery();

        m.setMastery(0.0); // Reset
        service.updateMastery(1L, 10L, 1.0);
        double after2 = m.getMastery();

        // Both should give same result (score=1.0)
        assertThat(after1).isEqualTo(after2);
        assertThat(after1).isCloseTo(0.4, within(0.001));
    }

    @Test
    @DisplayName("Score mapping: 0.5 score maps to 0.5 (partial)")
    void scoreMapping_partialScore() {
        Mastery m = createMastery(0.0, 0);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateMastery(1L, 10L, 0.5);

        assertThat(m.getMastery()).isCloseTo(0.2, within(0.001));
    }

    @Test
    @DisplayName("Score mapping: 0.0 score maps to 0.0 (fail)")
    void scoreMapping_failScores() {
        Mastery m = createMastery(0.0, 0);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateMastery(1L, 10L, 0.0);
        double after1 = m.getMastery();

        m.setMastery(0.0);
        service.updateMastery(1L, 10L, 0.0);
        double after2 = m.getMastery();

        // Both should give 0.0 (no increase from 0.0 baseline)
        assertThat(after1).isEqualTo(0.0);
        assertThat(after2).isEqualTo(0.0);
    }

    // ========== THRESHOLD BAND TESTS ==========

    @Test
    @DisplayName("Threshold: <0.65 is UNSAFE")
    void threshold_unsafeBelow065() {
        Mastery m = createMastery(0.64, 3);

        var band = service.getMasteryBand(m);

        assertThat(band).isEqualTo(MasteryService.MasteryBand.UNSAFE);
    }

    @Test
    @DisplayName("Threshold: 0.65-0.69 is WORKING")
    void threshold_workingRange() {
        Mastery m1 = createMastery(0.65, 3);
        Mastery m2 = createMastery(0.67, 3);
        Mastery m3 = createMastery(0.69, 3);

        assertThat(service.getMasteryBand(m1)).isEqualTo(MasteryService.MasteryBand.WORKING);
        assertThat(service.getMasteryBand(m2)).isEqualTo(MasteryService.MasteryBand.WORKING);
        assertThat(service.getMasteryBand(m3)).isEqualTo(MasteryService.MasteryBand.WORKING);
    }

    @Test
    @DisplayName("Threshold: ≥0.70 is MASTERED")
    void threshold_masteredAbove070() {
        Mastery m1 = createMastery(0.70, 3);
        Mastery m2 = createMastery(0.90, 3);
        Mastery m3 = createMastery(1.0, 3);

        assertThat(service.getMasteryBand(m1)).isEqualTo(MasteryService.MasteryBand.MASTERED);
        assertThat(service.getMasteryBand(m2)).isEqualTo(MasteryService.MasteryBand.MASTERED);
        assertThat(service.getMasteryBand(m3)).isEqualTo(MasteryService.MasteryBand.MASTERED);
    }

    @Test
    @DisplayName("Threshold: Boundary at 0.65 exactly")
    void threshold_boundaryAt065() {
        Mastery m = createMastery(0.65, 3);

        assertThat(service.getMasteryBand(m)).isEqualTo(MasteryService.MasteryBand.WORKING);
    }

    @Test
    @DisplayName("Threshold: Boundary at 0.70 exactly")
    void threshold_boundaryAt070() {
        Mastery m = createMastery(0.70, 3);

        assertThat(service.getMasteryBand(m)).isEqualTo(MasteryService.MasteryBand.MASTERED);
    }

    // ========== EVENT COUNT MINIMUM TESTS ==========

    @Test
    @DisplayName("Event count: Label suppressed with <3 events")
    void eventCount_suppressedBelowMinimum() {
        Mastery m0 = createMastery(0.9, 0);
        Mastery m1 = createMastery(0.9, 1);
        Mastery m2 = createMastery(0.9, 2);

        assertThat(service.getMasteryBand(m0)).isNull();
        assertThat(service.getMasteryBand(m1)).isNull();
        assertThat(service.getMasteryBand(m2)).isNull();
    }

    @Test
    @DisplayName("Event count: Label shown with ≥3 events")
    void eventCount_shownAtMinimum() {
        Mastery m3 = createMastery(0.9, 3);
        Mastery m4 = createMastery(0.9, 4);

        assertThat(service.getMasteryBand(m3)).isEqualTo(MasteryService.MasteryBand.MASTERED);
        assertThat(service.getMasteryBand(m4)).isEqualTo(MasteryService.MasteryBand.MASTERED);
    }

    @Test
    @DisplayName("isMastered: Requires both threshold AND min events")
    void isMastered_requiresBothConditions() {
        Mastery high2Events = createMastery(0.75, 2);
        Mastery high3Events = createMastery(0.75, 3);
        Mastery low3Events = createMastery(0.65, 3);

        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(high2Events));
        assertThat(service.isMastered(1L, 10L)).isFalse(); // Not enough events

        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(high3Events));
        assertThat(service.isMastered(1L, 10L)).isTrue(); // Both conditions met

        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(low3Events));
        assertThat(service.isMastered(1L, 10L)).isFalse(); // Below threshold
    }

    // ========== BOUNDARY EDGE CASES ==========

    @Test
    @DisplayName("Boundary: Mastery cannot exceed 1.0")
    void boundary_masteryCannotExceed1() {
        Mastery m = createMastery(0.95, 3);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Perfect score: α*1.0 + (1-α)*0.95 = 0.4 + 0.57 = 0.97
        service.updateMastery(1L, 10L, 1.0);

        assertThat(m.getMastery()).isLessThanOrEqualTo(1.0);
        assertThat(m.getMastery()).isCloseTo(0.97, within(0.001));
    }

    @Test
    @DisplayName("Boundary: Mastery cannot go negative")
    void boundary_masteryCannotGoNegative() {
        Mastery m = createMastery(0.05, 3);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Fail: α*0.0 + (1-α)*0.05 = 0.0 + 0.03 = 0.03
        service.updateMastery(1L, 10L, 0.0);

        assertThat(m.getMastery()).isGreaterThanOrEqualTo(0.0);
        assertThat(m.getMastery()).isCloseTo(0.03, within(0.001));
    }

    @Test
    @DisplayName("Boundary: Event count increments correctly")
    void boundary_eventCountIncrements() {
        Mastery m = createMastery(0.5, 0);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateMastery(1L, 10L, 1.0);
        assertThat(m.getEventCount()).isEqualTo(1);

        service.updateMastery(1L, 10L, 1.0);
        assertThat(m.getEventCount()).isEqualTo(2);

        service.updateMastery(1L, 10L, 1.0);
        assertThat(m.getEventCount()).isEqualTo(3);
    }

    // ========== SESSION AUTO-COMPLETION TESTS ==========

    @Test
    @DisplayName("Session completion: Sessions marked complete when crossing mastery threshold")
    void sessionCompletion_markedCompleteOnThresholdCross() {
        // Setup: mastery just below threshold
        Mastery m = createMastery(0.65, 2);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Create incomplete sessions
        LearningSession session1 = createSession("s1", false);
        LearningSession session2 = createSession("s2", false);
        when(sessionRepo.findAll()).thenReturn(List.of(session1, session2));

        // Update to cross threshold (0.65 -> 0.79 with score=1.0)
        // newMastery = 0.4*1.0 + 0.6*0.65 = 0.79
        service.updateMastery(1L, 10L, 1.0);

        // Verify both sessions marked as completed
        verify(sessionRepo, times(2)).save(any(LearningSession.class));
        assertThat(session1.isCompleted()).isTrue();
        assertThat(session2.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("Session completion: No completion when not enough events")
    void sessionCompletion_noCompletionWithInsufficientEvents() {
        // Setup: high mastery but only 2 events (need 3)
        Mastery m = createMastery(0.69, 1);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LearningSession session = createSession("s1", false);
        when(sessionRepo.findAll()).thenReturn(List.of(session));

        // Update crosses threshold but only reaches 2 events
        service.updateMastery(1L, 10L, 1.0);

        // Session should NOT be completed (need 3 events)
        verify(sessionRepo, never()).save(any(LearningSession.class));
        assertThat(session.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("Session completion: No re-completion when mastery fluctuates")
    void sessionCompletion_noRecompletionOnFluctuation() {
        // Setup: mastery below threshold
        Mastery m = createMastery(0.65, 3);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LearningSession session = createSession("s1", false);
        when(sessionRepo.findAll()).thenReturn(List.of(session));

        // First update: cross threshold (0.65 -> 0.79)
        service.updateMastery(1L, 10L, 1.0);
        assertThat(session.isCompleted()).isTrue();

        // Mastery drops (failure)
        service.updateMastery(1L, 10L, 0.0); // 0.79 -> 0.474

        // Mastery rises again (success)
        service.updateMastery(1L, 10L, 1.0); // 0.474 -> 0.6844

        // Should not trigger completion again (didn't cross threshold)
        verify(sessionRepo, times(1)).save(any(LearningSession.class));
    }

    @Test
    @DisplayName("Session completion: Only completes sessions for matching learner+concept")
    void sessionCompletion_onlyMatchingLearnersAndConcepts() {
        Mastery m = createMastery(0.65, 2);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Create sessions: matching, different learner, different concept, already complete
        LearningSession matchingSession = createSession("s1", false);
        LearningSession differentLearner = createSessionForLearner("s2", 999L, false);
        LearningSession differentConcept = createSessionForConcept("s3", 999L, false);
        LearningSession alreadyComplete = createSession("s4", true);

        when(sessionRepo.findAll()).thenReturn(List.of(
            matchingSession, differentLearner, differentConcept, alreadyComplete
        ));

        // Cross threshold
        service.updateMastery(1L, 10L, 1.0);

        // Only matching incomplete session should be completed
        assertThat(matchingSession.isCompleted()).isTrue();
        assertThat(differentLearner.isCompleted()).isFalse();
        assertThat(differentConcept.isCompleted()).isFalse();
        assertThat(alreadyComplete.isCompleted()).isTrue(); // Still true

        verify(sessionRepo, times(1)).save(matchingSession);
    }

    @Test
    @DisplayName("Session completion: No error when no sessions exist")
    void sessionCompletion_noErrorWithNoSessions() {
        Mastery m = createMastery(0.65, 2);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(sessionRepo.findAll()).thenReturn(List.of());

        // Cross threshold with no sessions - should not error
        service.updateMastery(1L, 10L, 1.0);

        verify(sessionRepo, never()).save(any(LearningSession.class));
    }

    @Test
    @DisplayName("Session completion: Threshold must be crossed, not just reached")
    void sessionCompletion_mustCrossNotJustReach() {
        // Setup: already above threshold
        Mastery m = createMastery(0.75, 3);
        when(masteryRepo.findById(any(Mastery.Pk.class))).thenReturn(Optional.of(m));
        when(masteryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LearningSession session = createSession("s1", false);
        when(sessionRepo.findAll()).thenReturn(List.of(session));

        // Update keeps mastery above threshold (0.75 -> 0.85)
        service.updateMastery(1L, 10L, 1.0);

        // Should NOT trigger completion (didn't cross, already above)
        verify(sessionRepo, never()).save(any(LearningSession.class));
        assertThat(session.isCompleted()).isFalse();
    }

    // ========== HELPER ==========

    private Mastery createMastery(double score, int eventCount) {
        Mastery m = new Mastery();
        m.setId(pk);
        m.setLearner(learner);
        m.setConcept(concept);
        m.setMastery(score);
        m.setEventCount(eventCount);
        return m;
    }

    private LearningSession createSession(String sessionId, boolean completed) {
        LearningSession session = new LearningSession();
        session.setSessionId(sessionId);
        session.setLearner(learner);
        session.setConcept(concept);
        session.setCompleted(completed);
        return session;
    }

    private LearningSession createSessionForLearner(String sessionId, Long learnerId, boolean completed) {
        Learner otherLearner = new Learner();
        otherLearner.setId(learnerId);
        LearningSession session = new LearningSession();
        session.setSessionId(sessionId);
        session.setLearner(otherLearner);
        session.setConcept(concept);
        session.setCompleted(completed);
        return session;
    }

    private LearningSession createSessionForConcept(String sessionId, Long conceptId, boolean completed) {
        Concept otherConcept = new Concept();
        otherConcept.setId(conceptId);
        LearningSession session = new LearningSession();
        session.setSessionId(sessionId);
        session.setLearner(learner);
        session.setConcept(otherConcept);
        session.setCompleted(completed);
        return session;
    }
}
