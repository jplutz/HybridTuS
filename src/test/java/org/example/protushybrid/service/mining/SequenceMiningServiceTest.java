package org.example.protushybrid.service.mining;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.Learner;
import org.example.protushybrid.domain.core.LearningObject;
import org.example.protushybrid.domain.core.LearningSession;
import org.example.protushybrid.domain.mining.MinedSupport;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.core.LearnerRepository;
import org.example.protushybrid.repository.core.LearningObjectRepository;
import org.example.protushybrid.repository.core.LearningSessionRepository;
import org.example.protushybrid.repository.mining.MinedSupportRepository;
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

import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SequenceMiningService.
 * Tests: support calculation, MIN_* guards, sequence extraction, edge cases.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SequenceMiningServiceTest {

    @Mock
    private LearningSessionRepository sessionRepo;

    @Mock
    private LearnerRepository learnerRepo;

    @Mock
    private ConceptRepository conceptRepo;

    @Mock
    private LearningObjectRepository loRepo;

    @Mock
    private MinedSupportRepository minedRepo;

    @InjectMocks
    private SequenceMiningService service;

    private Concept concept;
    private Map<Long, LearningObject> loMap;

    @BeforeEach
    void setUp() {
        concept = new Concept();
        concept.setId(1L);
        concept.setName("Test Concept");

        // Setup LO map for testing
        loMap = new HashMap<>();
        loMap.put(1L, createLO(1L, "T", "Theory 1"));
        loMap.put(2L, createLO(2L, "T", "Theory 2"));
        loMap.put(3L, createLO(3L, "E", "Example 1"));
        loMap.put(4L, createLO(4L, "A", "Activity 1"));
        loMap.put(5L, createLO(5L, "PRACTICE", "Practice 1")); // Should be excluded
        loMap.put(6L, createLO(6L, "NAVIGATION", "Nav 1")); // Should be excluded

        when(conceptRepo.findById(1L)).thenReturn(Optional.of(concept));
        when(loRepo.findById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(loMap.get(id));
        });
    }

    // ========== SUPPORT CALCULATION TESTS ==========

    @Test
    @DisplayName("Support: Calculated as distinctLearners / totalLearners")
    void support_calculatedCorrectly() throws Exception {
        // Given: 10 learners with 4 events each = 40 total events (meets MIN_EVENTS=30)
        // Pattern appears for 5 learners → support = 0.5
        List<Learner> learners = createLearners(10);
        List<LearningSession> sessions = new ArrayList<>();

        // 5 learners have pattern T→E→A→T (4 events each)
        for (int i = 0; i < 5; i++) {
            sessions.add(createSession(learners.get(i), List.of(1L, 3L, 4L, 1L))); // T→E→A→T
        }

        // 5 learners have different pattern T→A→E→A (4 events each)
        for (int i = 5; i < 10; i++) {
            sessions.add(createSession(learners.get(i), List.of(1L, 4L, 3L, 4L))); // T→A→E→A
        }

        when(sessionRepo.findAll()).thenReturn(sessions);
        when(learnerRepo.findAll()).thenReturn(learners);
        when(minedRepo.findAll()).thenReturn(Collections.emptyList());
        when(minedRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Call private method via reflection
        Method method = SequenceMiningService.class.getDeclaredMethod(
                "mineConceptPartition", String.class, Long.class, List.class);
        method.setAccessible(true);
        method.invoke(service, "CLUSTER1", 1L, sessions);

        ArgumentCaptor<List<MinedSupport>> captor = ArgumentCaptor.forClass(List.class);
        verify(minedRepo).saveAll(captor.capture());

        List<MinedSupport> saved = captor.getValue();
        assertThat(saved).isNotEmpty();

        // Both T→E and T→A patterns should have support = 5/10 = 0.5
        MinedSupport tePattern = saved.stream()
                .filter(ms -> ms.getSuffix().equals("T") && ms.getNextType().equals("E"))
                .findFirst().orElse(null);
        MinedSupport taPattern = saved.stream()
                .filter(ms -> ms.getSuffix().equals("T") && ms.getNextType().equals("A"))
                .findFirst().orElse(null);

        assertThat(tePattern).isNotNull();
        assertThat(tePattern.getSupport()).isCloseTo(0.5, within(0.01));
        assertThat(tePattern.getNLearners()).isEqualTo(5);

        assertThat(taPattern).isNotNull();
        assertThat(taPattern.getSupport()).isCloseTo(0.5, within(0.01));
        assertThat(taPattern.getNLearners()).isEqualTo(5);
    }

    @Test
    @DisplayName("Support: Requires MIN_LEARNERS=5 distinct learners")
    void support_requiresMinimumLearners() throws Exception {
        // Given: Pattern appears for only 4 learners (below threshold)
        // Need 30+ events, so 10 learners × 4 events = 40 events
        List<Learner> learners = createLearners(10);
        List<LearningSession> sessions = new ArrayList<>();

        // Only 4 learners have pattern T→E (below MIN_LEARNERS=5)
        for (int i = 0; i < 4; i++) {
            sessions.add(createSession(learners.get(i), List.of(1L, 3L, 4L, 1L))); // T→E→A→T
        }

        // Fill to meet MIN_EVENTS threshold (6 learners with T→A pattern)
        for (int i = 4; i < 10; i++) {
            sessions.add(createSession(learners.get(i), List.of(1L, 4L, 3L, 1L))); // T→A→E→T
        }

        when(sessionRepo.findAll()).thenReturn(sessions);
        when(learnerRepo.findAll()).thenReturn(learners);
        when(minedRepo.findAll()).thenReturn(Collections.emptyList());
        when(minedRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        Method method = SequenceMiningService.class.getDeclaredMethod(
                "mineConceptPartition", String.class, Long.class, List.class);
        method.setAccessible(true);
        method.invoke(service, "CLUSTER1", 1L, sessions);

        ArgumentCaptor<List<MinedSupport>> captor = ArgumentCaptor.forClass(List.class);
        verify(minedRepo).saveAll(captor.capture());

        List<MinedSupport> saved = captor.getValue();

        // T→E pattern should NOT be saved (only 4 learners)
        boolean hasTE = saved.stream()
                .anyMatch(ms -> ms.getSuffix().equals("T") && ms.getNextType().equals("E"));
        assertThat(hasTE).isFalse();

        // T→A pattern should be saved (6 learners ≥ MIN_LEARNERS)
        boolean hasTA = saved.stream()
                .anyMatch(ms -> ms.getSuffix().equals("T") && ms.getNextType().equals("A"));
        assertThat(hasTA).isTrue();
    }

    // ========== MIN_* GUARDS TESTS ==========

    @Test
    @DisplayName("MIN_EVENTS: Skips partition with <30 events")
    void minEvents_skipsSmallPartitions() throws Exception {
        // Given: Only 20 total events (below MIN_EVENTS=30)
        List<Learner> learners = createLearners(10);
        List<LearningSession> sessions = new ArrayList<>();

        // 10 sessions with 2 events each = 20 total events
        for (int i = 0; i < 10; i++) {
            sessions.add(createSession(learners.get(i), List.of(1L, 3L)));
        }

        when(minedRepo.findAll()).thenReturn(Collections.emptyList());

        Method method = SequenceMiningService.class.getDeclaredMethod(
                "mineConceptPartition", String.class, Long.class, List.class);
        method.setAccessible(true);
        int result = (int) method.invoke(service, "CLUSTER1", 1L, sessions);

        // Should return 0 patterns (skipped)
        assertThat(result).isEqualTo(0);
        verify(minedRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("MIN_EVENTS: Processes partition with ≥30 events")
    void minEvents_processesLargePartitions() throws Exception {
        // Given: 30 total events (meets MIN_EVENTS)
        List<Learner> learners = createLearners(10);
        List<LearningSession> sessions = new ArrayList<>();

        // 10 sessions with 3 events each = 30 total events
        for (int i = 0; i < 10; i++) {
            sessions.add(createSession(learners.get(i), List.of(1L, 3L, 4L)));
        }

        when(sessionRepo.findAll()).thenReturn(sessions);
        when(learnerRepo.findAll()).thenReturn(learners);
        when(minedRepo.findAll()).thenReturn(Collections.emptyList());
        when(minedRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        Method method = SequenceMiningService.class.getDeclaredMethod(
                "mineConceptPartition", String.class, Long.class, List.class);
        method.setAccessible(true);
        method.invoke(service, "CLUSTER1", 1L, sessions);

        verify(minedRepo).saveAll(any());
    }

    @Test
    @DisplayName("MIN_SUPPORT: Filters patterns below 25% support")
    void minSupport_filtersLowSupport() throws Exception {
        // Given: 20 learners, pattern appears for only 4 → support = 0.2 (below 0.25)
        List<Learner> learners = createLearners(20);
        List<LearningSession> sessions = new ArrayList<>();

        // 4 learners have pattern T→E (support = 4/20 = 0.2)
        for (int i = 0; i < 4; i++) {
            sessions.add(createSession(learners.get(i), List.of(1L, 3L)));
        }

        // 16 learners have pattern T→A (support = 16/20 = 0.8)
        for (int i = 4; i < 20; i++) {
            sessions.add(createSession(learners.get(i), List.of(1L, 4L)));
        }

        when(sessionRepo.findAll()).thenReturn(sessions);
        when(learnerRepo.findAll()).thenReturn(learners);
        when(minedRepo.findAll()).thenReturn(Collections.emptyList());
        when(minedRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        Method method = SequenceMiningService.class.getDeclaredMethod(
                "mineConceptPartition", String.class, Long.class, List.class);
        method.setAccessible(true);
        method.invoke(service, "CLUSTER1", 1L, sessions);

        ArgumentCaptor<List<MinedSupport>> captor = ArgumentCaptor.forClass(List.class);
        verify(minedRepo).saveAll(captor.capture());

        List<MinedSupport> saved = captor.getValue();

        // T→E should NOT be saved (support 0.2 < MIN_SUPPORT)
        // BUT it has only 4 learners < MIN_LEARNERS=5, so filtered anyway
        boolean hasTE = saved.stream()
                .anyMatch(ms -> ms.getSuffix().equals("T") && ms.getNextType().equals("E"));
        assertThat(hasTE).isFalse();

        // T→A should be saved (support 0.8 ≥ MIN_SUPPORT)
        boolean hasTA = saved.stream()
                .anyMatch(ms -> ms.getSuffix().equals("T") && ms.getNextType().equals("A"));
        assertThat(hasTA).isTrue();
    }

    // ========== SHORT SEQUENCE EDGE CASES ==========

    @Test
    @DisplayName("Edge case: Empty sequence produces no patterns")
    void edgeCase_emptySequence() throws Exception {
        List<Learner> learners = createLearners(5);
        List<LearningSession> sessions = List.of(
                createSession(learners.get(0), Collections.emptyList())
        );

        Method method = SequenceMiningService.class.getDeclaredMethod(
                "extractAndCollapseSequence", LearningSession.class);
        method.setAccessible(true);

        List<String> result = (List<String>) method.invoke(service, sessions.get(0));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Edge case: Single-element sequence produces start pattern only")
    void edgeCase_singleElement() throws Exception {
        // Need 30+ events: 10 learners × 4 same events = 40 events
        List<Learner> learners = createLearners(10);
        List<LearningSession> sessions = new ArrayList<>();

        // All learners see T→T→T→T (repeated same LO, will collapse to single T)
        for (Learner l : learners) {
            sessions.add(createSession(l, List.of(1L, 1L, 1L, 1L))); // T→T→T→T (collapses to T)
        }

        when(sessionRepo.findAll()).thenReturn(sessions);
        when(learnerRepo.findAll()).thenReturn(learners);
        when(minedRepo.findAll()).thenReturn(Collections.emptyList());
        when(minedRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        Method method = SequenceMiningService.class.getDeclaredMethod(
                "mineConceptPartition", String.class, Long.class, List.class);
        method.setAccessible(true);
        method.invoke(service, "CLUSTER1", 1L, sessions);

        ArgumentCaptor<List<MinedSupport>> captor = ArgumentCaptor.forClass(List.class);
        verify(minedRepo).saveAll(captor.capture());

        List<MinedSupport> saved = captor.getValue();

        // Should have start pattern: "" → T
        MinedSupport startPattern = saved.stream()
                .filter(ms -> ms.getSuffix().equals("") && ms.getNextType().equals("T"))
                .findFirst().orElse(null);

        assertThat(startPattern).isNotNull();
    }

    @Test
    @DisplayName("Edge case: Duplicate collapse - consecutive same LO ID")
    void edgeCase_duplicateCollapse() throws Exception {
        // Given: T1→T1→T1→E1 should become T→E (T1 collapsed)
        Method method = SequenceMiningService.class.getDeclaredMethod(
                "extractAndCollapseSequence", LearningSession.class);
        method.setAccessible(true);

        Learner learner = createLearners(1).get(0);
        LearningSession session = createSession(learner, List.of(1L, 1L, 1L, 3L)); // T1,T1,T1,E1

        List<String> result = (List<String>) method.invoke(service, session);

        // T1 collapsed, E1 kept
        assertThat(result).containsExactly("T", "E");
    }

    @Test
    @DisplayName("Edge case: Different LOs same type - preserved as separate")
    void edgeCase_differentLOsSameType() throws Exception {
        // Given: T1→T2→E should become T→T→E (different LO IDs preserved)
        Method method = SequenceMiningService.class.getDeclaredMethod(
                "extractAndCollapseSequence", LearningSession.class);
        method.setAccessible(true);

        Learner learner = createLearners(1).get(0);
        LearningSession session = createSession(learner, List.of(1L, 2L, 3L)); // T1,T2,E1

        List<String> result = (List<String>) method.invoke(service, session);

        assertThat(result).containsExactly("T", "T", "E");
    }

    @Test
    @DisplayName("Edge case: Non-contiguous revisit preserved")
    void edgeCase_nonContiguousRevisit() throws Exception {
        // Given: T1→E1→T1 should stay as T→E→T (non-contiguous)
        Method method = SequenceMiningService.class.getDeclaredMethod(
                "extractAndCollapseSequence", LearningSession.class);
        method.setAccessible(true);

        Learner learner = createLearners(1).get(0);
        LearningSession session = createSession(learner, List.of(1L, 3L, 1L)); // T1,E1,T1

        List<String> result = (List<String>) method.invoke(service, session);

        assertThat(result).containsExactly("T", "E", "T");
    }

    @Test
    @DisplayName("Edge case: PRACTICE and NAVIGATION excluded from sequence")
    void edgeCase_excludedTypes() throws Exception {
        // Given: T→PRACTICE→E→NAVIGATION→A should become T→E→A
        Method method = SequenceMiningService.class.getDeclaredMethod(
                "extractAndCollapseSequence", LearningSession.class);
        method.setAccessible(true);

        Learner learner = createLearners(1).get(0);
        LearningSession session = createSession(learner, List.of(1L, 5L, 3L, 6L, 4L));

        List<String> result = (List<String>) method.invoke(service, session);

        // PRACTICE (5L) and NAVIGATION (6L) should be excluded
        assertThat(result).containsExactly("T", "E", "A");
    }

    // ========== HELPER METHODS ==========

    private List<Learner> createLearners(int count) {
        List<Learner> learners = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Learner l = new Learner();
            l.setId((long) (i + 1));
            l.setDisplayName("Learner " + (i + 1));
            learners.add(l);
        }
        return learners;
    }

    private LearningSession createSession(Learner learner, List<Long> loIds) {
        LearningSession session = new LearningSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setLearner(learner);
        session.setConcept(concept);
        session.setCompleted(true);
        session.setCompletionSequence(new ArrayList<>(loIds));
        return session;
    }

    private LearningObject createLO(Long id, String type, String title) {
        LearningObject lo = new LearningObject();
        lo.setId(id);
        lo.setType(type);
        lo.setTitle(title);
        lo.setConcept(concept);
        return lo;
    }
}
