package org.example.protushybrid.repository.core;

import org.example.protushybrid.domain.core.Concept;
import org.example.protushybrid.domain.core.LearningObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningObjectRepository extends JpaRepository<LearningObject, Long> {

    List<LearningObject> findByConcept(Concept concept);

    List<LearningObject> findByConceptAndType(Concept concept, String type);

    /**
     * Find learning objects by concept ID and type.
     * Used by cold-start recommendation provider.
     */
    List<LearningObject> findByConceptIdAndType(Long conceptId, String type);

    /**
     * Find learning objects by concept ID.
     * Used for session initialization and available LO queries.
     */
    List<LearningObject> findByConceptId(Long conceptId);
}
