package org.example.protushybrid.service.admin;

import org.example.protushybrid.domain.core.*;
import org.example.protushybrid.repository.core.*;
import org.example.protushybrid.service.core.ClusterKeyUtil;
import org.example.protushybrid.service.recommendation.generation.MatrixBasedSequenceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Generates synthetic learning session data using FSLSM transition matrices.
 *
 * Features:
 * - 640 learners with even distribution across all 16 FSLSM clusters (40 learners per cluster)
 * - 3 concepts with 2-3 LOs per type
 * - ONE session per learner per concept (1,920 total sessions)
 * - Gaussian-distributed session lengths (mean=7, min=5, max=15 LOs)
 * - Within-session revisits supported (learner can repeat LOs during single session)
 * - Session timestamps spread over 30 days
 * - 80/20 train/test split for hold-out evaluation
 *
 * LO Types Used:
 * - T (Theory): Foundational explanations
 * - E (Example): Concrete demonstrations
 * - F (Figure): Visual diagrams (for Visual learners)
 * - A (Activity): Hands-on practice
 * - Test: Knowledge assessment
 *
 * These 5 types provide complete pedagogical coverage for demonstrating the FSLSM-based
 * recommendation approach. Extended types (O, X, R, S) are supported by the database schema
 * but require additional transition matrix design (see V14 migration for details).
 *
 * Target: 640 learners × 3 concepts × 1 session × 7 avg LOs ≈ 13,440 total interactions
 * Mining: 40 learners per cluster >> MIN_LEARNERS=5 threshold (8× safety margin)
 */
@Service
public class SyntheticDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(SyntheticDataGenerator.class);

    private final LearnerRepository learnerRepo;
    private final ConceptRepository conceptRepo;
    private final CourseRepository courseRepo;
    private final LearningObjectRepository loRepo;
    private final LearningSessionRepository sessionRepo;
    private final FslsmProfileRepository profileRepo;

    private final Random random = new Random(42); // Fixed seed for reproducibility
    private final MatrixBasedSequenceGenerator sequenceGenerator;

    // LO type pools (canonical single-letter codes)
    // Currently using 5 core types that align with FSLSM transition matrices
    // Future work: Expand matrices to support O, X, R, S types
    private static final List<String> EXTENDED_TYPES = List.of("T", "E", "F", "A", "Test");

    // Sequence segment boundaries (cumulative proportions)
    private static final double PHASE_T0_END = 0.30; // First 30%
    private static final double PHASE_T1_END = 0.80; // Next 50% (30-80%)
    // Remaining 20% is final segment

    // FSLSM matrix adherence coefficients (probability of following cluster matrix)
    private static final double ADHERENCE_T0 = 0.95; // Strong adherence (95%)
    private static final double ADHERENCE_T1 = 0.70; // Moderate adherence (70%)
    private static final double ADHERENCE_T2 = 0.20; // Weak adherence (20%)

    // Session parameters
    private static final int MIN_SESSION_LENGTH = 5;
    private static final int MAX_SESSION_LENGTH = 15;
    private static final int MEAN_SESSION_LENGTH = 7;

    public SyntheticDataGenerator(LearnerRepository learnerRepo,
                                  ConceptRepository conceptRepo,
                                  CourseRepository courseRepo,
                                  LearningObjectRepository loRepo,
                                  LearningSessionRepository sessionRepo,
                                  FslsmProfileRepository profileRepo) {
        this.learnerRepo = learnerRepo;
        this.conceptRepo = conceptRepo;
        this.courseRepo = courseRepo;
        this.loRepo = loRepo;
        this.sessionRepo = sessionRepo;
        this.profileRepo = profileRepo;
        this.sequenceGenerator = new MatrixBasedSequenceGenerator(random);
    }

    /**
     * Generate all synthetic data using FSLSM transition matrices.
     * Can be called via admin endpoint: POST /api/admin/data/generate
     *
     * NOTE: This method does NOT clean existing data. The caller (AdminController)
     * should check if data exists and prevent regeneration to avoid duplicate key errors.
     *
     * @return GenerationReport with statistics including train/test split info
     */
    @Transactional
    public GenerationReport generateAll() {
        log.info("Starting FSLSM matrix-based synthetic data generation...");

        // Generate base data
        Course course = createCourse();
        List<Concept> concepts = createConcepts(course, 3);
        Map<Concept, List<LearningObject>> losByConcept = createLearningObjects(concepts);

        // Generate learners with diverse FSLSM profiles
        // 640 learners = 40 per cluster across all 16 FSLSM clusters
        List<Learner> learners = createLearners(640);

        // Track sessions for train/test split
        List<LearningSession> allSessions = new ArrayList<>();
        int totalInteractions = 0;

        // Generate ONE session per learner-concept pair
        for (Learner learner : learners) {
            for (Concept concept : concepts) {
                List<LearningObject> availableLOs = losByConcept.get(concept);

                // Generate session with timestamps spread over 30 days
                Instant sessionStart = Instant.now().minus(30, ChronoUnit.DAYS)
                        .plus(random.nextInt(25), ChronoUnit.DAYS);

                LearningSession session = generateSession(
                    learner, concept, availableLOs, sessionStart
                );

                allSessions.add(session);
                totalInteractions += session.getCompletionSequence().size();
            }
        }

        // 80/20 train/test split
        Collections.shuffle(allSessions, random);
        int trainSize = (int) (allSessions.size() * 0.8);
        List<LearningSession> trainSessions = allSessions.subList(0, trainSize);
        List<LearningSession> testSessions = allSessions.subList(trainSize, allSessions.size());

        // Mark sessions for train/test split isolation
        for (LearningSession session : trainSessions) {
            session.setIsTestSet(false);  // Synthetic training sessions
        }
        for (LearningSession session : testSessions) {
            session.setIsTestSet(true);   // Synthetic test sessions (excluded from mining)
        }

        // Persist train/test flags
        sessionRepo.saveAll(trainSessions);
        sessionRepo.saveAll(testSessions);

        log.info("Synthetic data generation complete:");
        log.info("  - {} learners across {} FSLSM clusters ({} per cluster)",
                 learners.size(),
                 learners.stream().map(this::computeClusterKey).distinct().count(),
                 learners.size() / 16);
        log.info("  - {} concepts with {} LO types", concepts.size(), EXTENDED_TYPES.size());
        log.info("  - {} total sessions ({} train, {} test)", allSessions.size(),
                 trainSessions.size(), testSessions.size());
        log.info("  - {} total interactions", totalInteractions);
        log.info("  - Avg interactions/session: {}", totalInteractions / allSessions.size());

        return new GenerationReport(
            learners.size(),
            concepts.size(),
            allSessions.size(),
            trainSessions.size(),
            testSessions.size(),
            totalInteractions
        );
    }


    /**
     * Generation report with statistics.
     */
    public record GenerationReport(
        int numLearners,
        int numConcepts,
        int totalSessions,
        int trainSessions,
        int testSessions,
        int totalInteractions
    ) {}

    /**
     * Create synthetic test course for data generation.
     */
    private Course createCourse() {
        Course course = new Course();
        course.setTitle("[SYNTHETIC DATA] Programming Fundamentals");
        course.setDescription("Synthetic test course for FSLSM mining evaluation - Generated data only");
        return courseRepo.save(course);
    }

    /**
     * Create multiple concepts.
     */
    private List<Concept> createConcepts(Course course, int count) {
        List<Concept> concepts = new ArrayList<>();
        String[] names = {"Variables", "Control Flow", "Functions", "Data Structures", "Algorithms"};

        for (int i = 0; i < Math.min(count, names.length); i++) {
            Concept concept = new Concept();
            concept.setCourse(course);
            concept.setName(names[i]);
            concept.setDescription("Synthetic concept: " + names[i]);
            concept.setOrderIndex(i);
            concepts.add(conceptRepo.save(concept));
        }

        return concepts;
    }

    /**
     * Create learning objects for each concept.
     * Some LOs will have custom titles, others will use auto-generated titles.
     */
    private Map<Concept, List<LearningObject>> createLearningObjects(List<Concept> concepts) {
        Map<Concept, List<LearningObject>> result = new HashMap<>();

        for (Concept concept : concepts) {
            List<LearningObject> los = new ArrayList<>();

            // Create 2-3 of each type
            for (String type : EXTENDED_TYPES) {
                int count = 2 + random.nextInt(2);
                for (int i = 0; i < count; i++) {
                    LearningObject lo = new LearningObject();
                    lo.setConcept(concept);
                    lo.setType(type);
                    lo.setSourceUri("synthetic://" + concept.getName() + "/" + type + "/" + i);
                    lo.setEstTimeMin(5 + random.nextInt(15));
                    lo.setVersion(1);

                    // Add custom title to ~50% of LOs (demonstrates optional title feature)
                    if (random.nextBoolean()) {
                        lo.setTitle(generateSampleTitle(concept.getName(), type, i));
                    }
                    // Note: When title is null, SessionController will auto-generate it

                    los.add(loRepo.save(lo));
                }
            }

            result.put(concept, los);
        }

        return result;
    }

    /**
     * Generate sample titles for learning objects.
     * These demonstrate the custom title feature.
     */
    private String generateSampleTitle(String conceptName, String type, int index) {
        return switch (type) {
            case "T" -> conceptName + " Fundamentals " + (index + 1);
            case "E" -> "Example: " + conceptName + " in Action " + (index + 1);
            case "F" -> conceptName + " Visual Guide " + (index + 1);
            case "A" -> "Practice: " + conceptName + " Exercise " + (index + 1);
            case "Test" -> conceptName + " Assessment " + (index + 1);
            default -> conceptName + " - " + type + " " + (index + 1);
        };
    }

    /**
     * Create learners with diverse FSLSM profiles.
     * Systematically covers all 16 FSLSM clusters with even distribution.
     *
     * With 640 learners, each cluster gets exactly 40 learners.
     */
    private List<Learner> createLearners(int count) {
        List<Learner> learners = new ArrayList<>();

        // All 16 FSLSM cluster combinations (2^4 = 16 clusters)
        // Generated systematically: AR × SI × VV × SG
        String[][] allClusters = {
                {"ACTIVE", "SENSING", "VISUAL", "SEQUENTIAL"},
                {"ACTIVE", "SENSING", "VISUAL", "GLOBAL"},
                {"ACTIVE", "SENSING", "VERBAL", "SEQUENTIAL"},
                {"ACTIVE", "SENSING", "VERBAL", "GLOBAL"},
                {"ACTIVE", "INTUITIVE", "VISUAL", "SEQUENTIAL"},
                {"ACTIVE", "INTUITIVE", "VISUAL", "GLOBAL"},
                {"ACTIVE", "INTUITIVE", "VERBAL", "SEQUENTIAL"},
                {"ACTIVE", "INTUITIVE", "VERBAL", "GLOBAL"},
                {"REFLECTIVE", "SENSING", "VISUAL", "SEQUENTIAL"},
                {"REFLECTIVE", "SENSING", "VISUAL", "GLOBAL"},
                {"REFLECTIVE", "SENSING", "VERBAL", "SEQUENTIAL"},
                {"REFLECTIVE", "SENSING", "VERBAL", "GLOBAL"},
                {"REFLECTIVE", "INTUITIVE", "VISUAL", "SEQUENTIAL"},
                {"REFLECTIVE", "INTUITIVE", "VISUAL", "GLOBAL"},
                {"REFLECTIVE", "INTUITIVE", "VERBAL", "SEQUENTIAL"},
                {"REFLECTIVE", "INTUITIVE", "VERBAL", "GLOBAL"}
        };

        for (int i = 0; i < count; i++) {
            Learner learner = new Learner();
            learner.setExternalRef("synth_learner_" + i);
            learner.setDisplayName("Synthetic Learner " + i);

            // Assign FSLSM profile by cycling through all 16 clusters
            String[] profile = allClusters[i % allClusters.length];
            learner.setStyleActiveReflective(profile[0].equals("ACTIVE") ? 5.0 : -5.0);
            learner.setStyleSensingIntuitive(profile[1].equals("SENSING") ? 5.0 : -5.0);
            learner.setStyleVisualVerbal(profile[2].equals("VISUAL") ? 5.0 : -5.0);
            learner.setStyleSequentialGlobal(profile[3].equals("SEQUENTIAL") ? 5.0 : -5.0);

            learner = learnerRepo.save(learner);

            // Create FSLSM profile entry
            FslsmProfile fslsm = new FslsmProfile();
            fslsm.setLearnerId(learner.getId());
            fslsm.setActiveReflective(profile[0]);
            fslsm.setSensingIntuitive(profile[1]);
            fslsm.setVisualVerbal(profile[2]);
            fslsm.setSequentialGlobal(profile[3]);
            profileRepo.save(fslsm);

            learners.add(learner);
        }

        return learners;
    }

    /**
     * Generate a single completed session using FSLSM transition matrices.
     *
     * @param learner Learner with FSLSM profile
     * @param concept Concept being studied
     * @param availableLOs Available learning objects for this concept
     * @param sessionStart Timestamp when session started
     * @return Completed LearningSession entity (saved to database)
     */
    private LearningSession generateSession(Learner learner, Concept concept,
                                            List<LearningObject> availableLOs,
                                            Instant sessionStart) {
        LearningSession session = new LearningSession();
        session.setLearner(learner);
        session.setConcept(concept);
        session.setCreatedAt(sessionStart);

        // Session duration: 10-60 minutes
        int durationMin = 10 + random.nextInt(51);
        session.setLastAccessedAt(sessionStart.plus(durationMin, ChronoUnit.MINUTES));
        session.setCompleted(true);

        // Determine session length
        int targetLength = sampleSessionLength();

        // Generate sequence using FSLSM matrix for learner's cluster
        List<Long> sequence = generatePhaseEvolvedSequence(learner, availableLOs, targetLength);

        // Add sequence to session with proper done/revisited tracking
        Long lastLoId = null;
        Set<Long> alreadyDone = new HashSet<>();  // Track completions within this session

        for (Long loId : sequence) {
            // Skip consecutive duplicates (prevents T1→T1→T1 pathological sequences)
            if (loId.equals(lastLoId)) {
                continue;
            }

            // Determine if this is first-time completion or revisit within this session
            if (!alreadyDone.contains(loId)) {
                // First time seeing this LO in this session - mark as done
                session.markAsDone(loId);
                alreadyDone.add(loId);
            } else {
                // Seen before in this session - mark as revisited
                session.markAsRevisited(loId);
            }

            lastLoId = loId;
        }

        sessionRepo.save(session);
        return session;
    }

    /**
     * Generate LO sequence using FSLSM transition matrix with varying adherence levels.
     * Adherence decreases throughout the session: 95% → 70% → 20%.
     *
     * @param learner Learner with FSLSM profile
     * @param availableLOs Available learning objects
     * @param targetLength Target sequence length
     * @return Sequence of LO IDs
     */
    private List<Long> generatePhaseEvolvedSequence(Learner learner,
                                                     List<LearningObject> availableLOs,
                                                     int targetLength) {
        List<Long> fullSequence = new ArrayList<>();

        // Calculate segment boundaries
        int t0End = (int) (targetLength * PHASE_T0_END);
        int t1End = (int) (targetLength * PHASE_T1_END);

        // First segment (first 30%): Strong adherence (95%)
        int t0Length = t0End;
        if (t0Length > 0) {
            List<Long> t0Sequence = sequenceGenerator.generateSequence(
                    learner, availableLOs, ADHERENCE_T0, t0Length);
            fullSequence.addAll(t0Sequence);
        }

        // Middle segment (next 50%): Moderate adherence (70%)
        int t1Length = t1End - t0End;
        if (t1Length > 0) {
            List<Long> t1Sequence = sequenceGenerator.generateSequence(
                    learner, availableLOs, ADHERENCE_T1, t1Length);
            fullSequence.addAll(t1Sequence);
        }

        // Final segment (last 20%): Weak adherence (20%)
        int t2Length = targetLength - t1End;
        if (t2Length > 0) {
            List<Long> t2Sequence = sequenceGenerator.generateSequence(
                    learner, availableLOs, ADHERENCE_T2, t2Length);
            fullSequence.addAll(t2Sequence);
        }

        return fullSequence;
    }

    /**
     * Sample session length from Gaussian distribution.
     * Mean = MEAN_SESSION_LENGTH, min = MIN_SESSION_LENGTH, max = MAX_SESSION_LENGTH.
     */
    private int sampleSessionLength() {
        double mean = MEAN_SESSION_LENGTH;
        double stdDev = mean * 0.3; // 30% variation

        double length = mean + random.nextGaussian() * stdDev;
        length = Math.max(MIN_SESSION_LENGTH, Math.min(MAX_SESSION_LENGTH, length));

        return (int) Math.round(length);
    }

    /**
     * Compute FSLSM cluster key from learner's style scores.
     * Delegates to ClusterKeyUtil for consistency.
     */
    private String computeClusterKey(Learner learner) {
        return ClusterKeyUtil.forLearner(learner);
    }

    /**
     * Clean existing synthetic data (optional).
     */
    private void cleanExistingData() {
        log.info("Cleaning existing synthetic data...");

        // Delete sessions created by synthetic learners
        List<Learner> synthLearners = learnerRepo.findAll().stream()
                .filter(l -> l.getExternalRef() != null && l.getExternalRef().startsWith("synth_"))
                .toList();

        for (Learner learner : synthLearners) {
            sessionRepo.findByLearnerIdOrderByCreatedAtDesc(learner.getId())
                    .forEach(sessionRepo::delete);

            // Delete FSLSM profiles associated with this learner
            profileRepo.findByLearnerId(learner.getId())
                    .ifPresent(profileRepo::delete);

            learnerRepo.delete(learner);
        }

        // Delete synthetic courses and their associated data
        List<Course> synthCourses = courseRepo.findAll().stream()
                .filter(c -> c.getDescription() != null && c.getDescription().contains("Synthetic"))
                .toList();

        for (Course course : synthCourses) {
            // Get all concepts for this course
            List<Concept> concepts = conceptRepo.findAll().stream()
                    .filter(c -> c.getCourse() != null && c.getCourse().getId().equals(course.getId()))
                    .toList();

            // Delete learning objects for each concept
            for (Concept concept : concepts) {
                List<LearningObject> los = loRepo.findAll().stream()
                        .filter(lo -> lo.getConcept() != null && lo.getConcept().getId().equals(concept.getId()))
                        .toList();
                los.forEach(loRepo::delete);
            }

            // Delete concepts
            concepts.forEach(conceptRepo::delete);

            // Finally delete the course
            courseRepo.delete(course);
        }

        log.info("Cleanup complete");
    }
}
