package org.example.protushybrid.repository.tracking;

import org.example.protushybrid.domain.tracking.Mastery;
import org.example.protushybrid.domain.tracking.Mastery.Pk;
import org.example.protushybrid.domain.core.Learner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MasteryRepository extends JpaRepository<Mastery, Pk> {
    List<Mastery> findByLearner(Learner learner);

    @Query("""
      select m from Mastery m
      where m.learner.id = :learnerId
      order by m.mastery asc
    """)
    List<Mastery> findOrderedByLowest(Long learnerId);
}
