package org.example.protushybrid.controller.core;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.Course;
import org.example.protushybrid.domain.tracking.Mastery;
import org.example.protushybrid.repository.core.ConceptRepository;
import org.example.protushybrid.repository.core.CourseRepository;
import org.example.protushybrid.repository.tracking.MasteryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for course and concept management
 */
@RestController
@RequestMapping("/api")
public class CourseController {

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);

    private final CourseRepository courseRepo;
    private final ConceptRepository conceptRepo;
    private final MasteryRepository masteryRepo;

    public CourseController(CourseRepository courseRepo,
                           ConceptRepository conceptRepo,
                           MasteryRepository masteryRepo) {
        this.courseRepo = courseRepo;
        this.conceptRepo = conceptRepo;
        this.masteryRepo = masteryRepo;
    }

    /**
     * Get all courses
     * GET /api/courses
     */
    @GetMapping("/courses")
    public ResponseEntity<List<CourseDto>> getAllCourses() {
        List<Course> courses = courseRepo.findAll();

        List<CourseDto> courseDtos = courses.stream()
                .map(this::buildCourseDto)
                .toList();

        log.info("Retrieved {} courses", courseDtos.size());
        return ResponseEntity.ok(courseDtos);
    }

    /**
     * Get single course
     * GET /api/courses/{id}
     */
    @GetMapping("/courses/{id}")
    public ResponseEntity<CourseDto> getCourse(@PathVariable Long id) {
        return courseRepo.findById(id)
                .map(this::buildCourseDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all concepts for a course
     * GET /api/courses/{id}/concepts?learnerId=X (optional)
     * If learnerId is provided, includes mastery data for that learner
     */
    @GetMapping("/courses/{courseId}/concepts")
    public ResponseEntity<List<ConceptDto>> getConcepts(
            @PathVariable Long courseId,
            @RequestParam(required = false) Long learnerId) {

        Course course = courseRepo.findById(courseId).orElse(null);

        if (course == null) {
            return ResponseEntity.notFound().build();
        }

        // Get mastery data for this learner if learnerId provided
        Map<Long, Double> masteryMap = Map.of();
        if (learnerId != null) {
            masteryMap = masteryRepo.findAll().stream()
                .filter(m -> m.getLearner().getId().equals(learnerId))
                .collect(Collectors.toMap(
                    m -> m.getConcept().getId(),
                    Mastery::getMastery
                ));
        }

        final Map<Long, Double> finalMasteryMap = masteryMap;
        List<ConceptDto> conceptDtos = course.getConcepts().stream()
                .map(concept -> buildConceptDto(concept, finalMasteryMap.get(concept.getId())))
                .toList();

        log.info("Retrieved {} concepts for course {} (learnerId={})", conceptDtos.size(), courseId, learnerId);
        return ResponseEntity.ok(conceptDtos);
    }

    /**
     * Get all concepts (for backward compatibility)
     * GET /api/concepts
     */
    @GetMapping("/concepts")
    public ResponseEntity<List<ConceptDto>> getAllConcepts() {
        List<Concept> concepts = conceptRepo.findAll();

        List<ConceptDto> conceptDtos = concepts.stream()
                .map(this::buildConceptDto)
                .toList();

        return ResponseEntity.ok(conceptDtos);
    }

    /**
     * Get single concept
     * GET /api/concepts/{id}
     */
    @GetMapping("/concepts/{id}")
    public ResponseEntity<ConceptDto> getConcept(@PathVariable Long id) {
        return conceptRepo.findById(id)
                .map(this::buildConceptDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ========== DTO Builders ==========

    private CourseDto buildCourseDto(Course course) {
        return new CourseDto(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getConcepts() != null ? course.getConcepts().size() : 0
        );
    }

    private ConceptDto buildConceptDto(Concept concept) {
        return buildConceptDto(concept, null);
    }

    private ConceptDto buildConceptDto(Concept concept, Double mastery) {
        List<ConceptPrereqDto> prereqs = concept.getPrerequisites() != null
                ? concept.getPrerequisites().stream()
                        .map(p -> new ConceptPrereqDto(p.getId(), p.getName()))
                        .toList()
                : List.of();

        return new ConceptDto(
                concept.getId(),
                concept.getName(),
                concept.getDescription(),
                mastery,  // null if no mastery data available
                prereqs,
                concept.getCourse() != null ? concept.getCourse().getId() : null,
                concept.getOrderIndex()
        );
    }

    // ========== DTOs ==========

    public record CourseDto(
            Long id,
            String title,
            String description,
            Integer conceptCount
    ) {}

    public record ConceptDto(
            Long id,
            String title,
            String description,
            Double mastery,  // null if not started, 0.0-1.0 if attempted
            List<ConceptPrereqDto> prerequisites,
            Long courseId,
            Integer orderIndex
    ) {}

    public record ConceptPrereqDto(
            Long conceptId,
            String conceptName
    ) {}
}
