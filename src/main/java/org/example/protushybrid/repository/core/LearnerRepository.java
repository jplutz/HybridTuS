package org.example.protushybrid.repository.core;

import org.example.protushybrid.domain.core.Learner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LearnerRepository extends JpaRepository<Learner, Long> {
    Learner findByExternalRef(String externalRef);
}
