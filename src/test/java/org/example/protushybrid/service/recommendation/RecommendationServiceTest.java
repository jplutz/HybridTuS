package org.example.protushybrid.service.recommendation;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.core.LearningObject;
import org.example.protushybrid.domain.core.LearningSession;
import org.example.protushybrid.domain.mining.MinedSupport;
import org.example.protushybrid.repository.core.*;
import org.example.protushybrid.repository.mining.MinedSupportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RecommendationService.
 * Tests: suffix matching, backoff, cluster fallback, cold-start, tie-breaking.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceTest {

    @Mock
    private LearnerRepository learnerRepo;
    @Mock
    private ConceptRepository conceptRepo;
    @Mock
    private LearningObjectRepository loRepo;
    @Mock
    private MinedSupportRepository minedRepo;
    @Mock
    private LearningSessionRepository sessionRepo;
    @Mock
    private DefaultSequenceProvider coldStartProvider;

    @InjectMocks
    private RecommendationService service;

    private Learner learner;
    private Concept concept;

    @BeforeEach
    void setUp() {
        learner = new Learner();
        learner.setId(1L);
        learner.setStyleActiveReflective(5.0);
        learner.setStyleSensingIntuitive(3.0);
        learner.setStyleVisualVerbal(-2.0);
        learner.setStyleSequentialGlobal(7.0);

        concept = new Concept();
        concept.setId(10L);

        when(learnerRepo.findById(1L)).thenReturn(Optional.of(learner));
        when(conceptRepo.findById(10L)).thenReturn(Optional.of(concept));
    }

    // ========== SUFFIX MATCHING TESTS ==========

    @Test
    @DisplayName("Suffix match: Exact match for 3-length suffix")
    void suffixMatch_exactMatch3Length() {
        // Given: Recent types [T, E, A] and pattern T>E>A → F exists
        LearningSession session = createSession(List.of(1L, 2L, 3L)); // T,E,A
        when(sessionRepo.findByLearnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(session));
        when(loRepo.findById(1L)).thenReturn(Optional.of(createLO(1L, "T")));
        when(loRepo.findById(2L)).thenReturn(Optional.of(createLO(2L, "E")));
        when(loRepo.findById(3L)).thenReturn(Optional.of(createLO(3L, "A")));

        MinedSupport pattern = createPattern("ACTIVE_SENSING_VERBAL_SEQUENTIAL", "T>E>A", "F", 0.8, 10);
        when(minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                eq("ACTIVE_SENSING_VERBAL_SEQUENTIAL"), eq(10L), eq("T>E>A")))
                .thenReturn(List.of(pattern));

        LearningObject fLO = createLO(10L, "F");
        when(loRepo.findByConcept(concept)).thenReturn(new ArrayList<>(List.of(fLO)));
        when(loRepo.findByConceptAndType(concept, "F")).thenReturn(new ArrayList<>(List.of(fLO)));

        // When
        Optional<LearningObject> result = service.recommendNext(1L, 10L);

        // Then: Should recommend F
        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo("F");
    }

    @Test
    @DisplayName("Backoff: Falls back from 3 → 2 → 1 when longer suffix not found")
    void backoff_fallsBackToShorterSuffix() {
        // Given: Recent types [T, E, A] but no pattern for T>E>A
        LearningSession session = createSession(List.of(1L, 2L, 3L));
        when(sessionRepo.findByLearnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(session));
        when(loRepo.findById(1L)).thenReturn(Optional.of(createLO(1L, "T")));
        when(loRepo.findById(2L)).thenReturn(Optional.of(createLO(2L, "E")));
        when(loRepo.findById(3L)).thenReturn(Optional.of(createLO(3L, "A")));

        // No pattern for T>E>A (length 3)
        when(minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                anyString(), eq(10L), eq("T>E>A")))
                .thenReturn(Collections.emptyList());

        // No pattern for E>A (length 2)
        when(minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                anyString(), eq(10L), eq("E>A")))
                .thenReturn(Collections.emptyList());

        // Pattern exists for A (length 1)
        MinedSupport pattern = createPattern("GLOBAL", "A", "F", 0.7, 8);
        when(minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                eq("GLOBAL"), eq(10L), eq("A")))
                .thenReturn(List.of(pattern));

        LearningObject fLO = createLO(10L, "F");
        when(loRepo.findByConcept(concept)).thenReturn(new ArrayList<>(List.of(fLO)));
        when(loRepo.findByConceptAndType(concept, "F")).thenReturn(new ArrayList<>(List.of(fLO)));

        // When
        Optional<LearningObject> result = service.recommendNext(1L, 10L);

        // Then: Should fall back to length-1 suffix and recommend F
        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo("F");
    }

    // ========== CLUSTER FALLBACK TESTS ==========

    @Test
    @DisplayName("Cluster fallback: Uses GLOBAL when cluster-specific not found")
    void clusterFallback_usesGlobal() {
        // Given: Pattern not in cluster, but exists in GLOBAL
        LearningSession session = createSession(List.of(1L)); // Just T
        when(sessionRepo.findByLearnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(session));
        when(loRepo.findById(1L)).thenReturn(Optional.of(createLO(1L, "T")));

        // No cluster-specific pattern
        when(minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                eq("ACTIVE_SENSING_VERBAL_SEQUENTIAL"), eq(10L), eq("T")))
                .thenReturn(Collections.emptyList());

        // GLOBAL pattern exists
        MinedSupport global = createPattern("GLOBAL", "T", "E", 0.6, 15);
        when(minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                eq("GLOBAL"), eq(10L), eq("T")))
                .thenReturn(List.of(global));

        LearningObject eLO = createLO(10L, "E");
        when(loRepo.findByConcept(concept)).thenReturn(new ArrayList<>(List.of(eLO)));
        when(loRepo.findByConceptAndType(concept, "E")).thenReturn(new ArrayList<>(List.of(eLO)));

        // When
        Optional<LearningObject> result = service.recommendNext(1L, 10L);

        // Then: Should use GLOBAL pattern
        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo("E");
    }

    // ========== COLD-START TESTS ==========

    @Test
    @DisplayName("Cold-start: Falls back to FSLSM when no patterns exist")
    void coldStart_usesFslsmWhenNoPatternsExist() {
        // Given: No mined patterns at all
        when(sessionRepo.findByLearnerIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());
        when(minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                anyString(), eq(10L), anyString()))
                .thenReturn(Collections.emptyList());

        LearningObject tLO = createLO(20L, "T");
        when(loRepo.findByConcept(concept)).thenReturn(new ArrayList<>(List.of(tLO)));

        // Cold-start returns LO ID 20
        when(coldStartProvider.getDefaultRecommendation(
                eq(1L), eq(10L), anyList(), anySet()))
                .thenReturn(20L);
        when(loRepo.findById(20L)).thenReturn(Optional.of(tLO));

        // When
        Optional<LearningObject> result = service.recommendNext(1L, 10L);

        // Then: Should use cold-start
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(20L);
        assertThat(result.get().getType()).isEqualTo("T");
    }

    @Test
    @DisplayName("Cold-start: Start state (empty suffix) uses empty string suffix")
    void coldStart_startStateUsesEmptySuffix() {
        // Given: No previous session (start state)
        when(sessionRepo.findByLearnerIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());

        // Pattern with suffix="" (start state)
        MinedSupport startPattern = createPattern("GLOBAL", "", "T", 0.9, 20);
        when(minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                anyString(), eq(10L), eq("")))
                .thenReturn(List.of(startPattern));

        LearningObject tLO = createLO(10L, "T");
        when(loRepo.findByConcept(concept)).thenReturn(new ArrayList<>(List.of(tLO)));
        when(loRepo.findByConceptAndType(concept, "T")).thenReturn(new ArrayList<>(List.of(tLO)));

        // When
        Optional<LearningObject> result = service.recommendNext(1L, 10L);

        // Then: Should use start-state pattern
        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo("T");
    }

    // ========== TIE-BREAKING TESTS ==========

    @Test
    @DisplayName("Tie-break: Uses first available pattern (ordered by n_support DESC)")
    void tieBreak_usesFirstAvailable() {
        // Given: Multiple patterns, but first is not available
        LearningSession session = createSession(List.of(1L)); // T
        when(sessionRepo.findByLearnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(session));
        when(loRepo.findById(1L)).thenReturn(Optional.of(createLO(1L, "T")));

        // Patterns ordered by n_support DESC
        MinedSupport pattern1 = createPattern("GLOBAL", "T", "A", 0.9, 100); // Highest support, but not available
        MinedSupport pattern2 = createPattern("GLOBAL", "T", "E", 0.8, 80);  // Second highest, available
        MinedSupport pattern3 = createPattern("GLOBAL", "T", "F", 0.7, 60);  // Third

        when(minedRepo.findByClusterKeyAndConceptIdAndSuffixOrderByNSupportDesc(
                anyString(), eq(10L), eq("T")))
                .thenReturn(List.of(pattern1, pattern2, pattern3));

        // Only E and F are available (A already visited)
        LearningObject eLO = createLO(10L, "E");
        LearningObject fLO = createLO(11L, "F");
        when(loRepo.findByConcept(concept)).thenReturn(new ArrayList<>(List.of(eLO, fLO)));
        when(loRepo.findByConceptAndType(concept, "E")).thenReturn(new ArrayList<>(List.of(eLO)));

        // When
        Optional<LearningObject> result = service.recommendNext(1L, 10L);

        // Then: Should skip A and use E (first available)
        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo("E");
    }

    // ========== HELPER METHODS ==========

    private LearningSession createSession(List<Long> completionSequence) {
        LearningSession session = new LearningSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setLearner(learner);
        session.setConcept(concept);
        session.setCompleted(false);
        session.setCompletionSequence(new ArrayList<>(completionSequence));
        return session;
    }

    private LearningObject createLO(Long id, String type) {
        LearningObject lo = new LearningObject();
        lo.setId(id);
        lo.setType(type);
        lo.setConcept(concept);
        lo.setVersion(1);
        return lo;
    }

    private MinedSupport createPattern(String clusterKey, String suffix, String nextType,
                                       double support, int nSupport) {
        return new MinedSupport(clusterKey, 10L, suffix, nextType, support, nSupport, 10);
    }
}
