package org.example.protushybrid.repository.core;

import org.example.protushybrid.domain.core.FslsmProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FslsmProfileRepository extends JpaRepository<FslsmProfile, Long> {

    /**
     * Find FSLSM profile by learner ID.
     */
    Optional<FslsmProfile> findByLearnerId(Long learnerId);
}
