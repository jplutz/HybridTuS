package org.example.protushybrid.repository.core;

import org.example.protushybrid.domain.core.Concept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConceptRepository extends JpaRepository<Concept, Long> {
    Concept findByName(String name);
    List<Concept> findByCourseIdOrderByOrderIndex(Long courseId);
}
