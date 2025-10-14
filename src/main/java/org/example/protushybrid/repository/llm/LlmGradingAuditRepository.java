package org.example.protushybrid.repository.llm;

import org.example.protushybrid.domain.llm.LlmGradingAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LlmGradingAuditRepository extends JpaRepository<LlmGradingAudit, Long> {

    List<LlmGradingAudit> findByExerciseId(Long exerciseId);

    List<LlmGradingAudit> findByLearnerId(Long learnerId);
}
