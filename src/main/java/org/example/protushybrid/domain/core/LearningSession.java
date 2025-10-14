package org.example.protushybrid.domain.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Learning session for a learner working through a concept.
 * Tracks which modules (LOs) the learner has visited in sequence.
 * Each LO appears only once in the sequence.
 */
@Entity
@Table(name = "learning_sessions", schema = "app")
@Getter
@Setter
public class LearningSession {

    @Id
    @Column(length = 36)
    private String sessionId = UUID.randomUUID().toString();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_id")
    private Learner learner;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id")
    private Concept concept;

    /**
     * Ordered sequence of LO IDs visited by learner.
     * Each LO appears only once - records the order user navigated modules.
     * Used for data mining to recommend next steps.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "session_sequence",
            schema = "app",
            joinColumns = @JoinColumn(name = "session_id")
    )
    @OrderColumn(name = "seq_order")
    @Column(name = "lo_id")
    private List<Long> visitedLoIds = new ArrayList<>();

    /**
     * Set of LO IDs that have been marked as done (completed).
     * Subset of visitedLoIds - tracks explicit completion.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "session_done_modules",
            schema = "app",
            joinColumns = @JoinColumn(name = "session_id")
    )
    @Column(name = "lo_id")
    private List<Long> doneLoIds = new ArrayList<>();

    /**
     * List of LO IDs that have been revisited after being marked as done.
     * Tracks repeat completions for recommendation updates.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "session_revisited_modules",
            schema = "app",
            joinColumns = @JoinColumn(name = "session_id")
    )
    @Column(name = "lo_id")
    private List<Long> revisitedLoIds = new ArrayList<>();

    /**
     * Ordered sequence of completion events (done + revisited).
     * Allows duplicates to track revisits in chronological order.
     * Used for sequence mining to analyze actual learning patterns.
     *
     * Example: If user completes T1, E1, revisits T1, then completes A1,
     * this list will be [T1, E1, T1, A1] preserving the actual learning path.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "session_completion_sequence",
            schema = "app",
            joinColumns = @JoinColumn(name = "session_id")
    )
    @OrderColumn(name = "seq_order")
    @Column(name = "lo_id")
    private List<Long> completionSequence = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt = Instant.now();

    @Column(name = "completed")
    private boolean completed = false;

    /**
     * Flag for train/test split in evaluation scenarios.
     * - NULL: Real user sessions (default) - included in mining
     * - false: Synthetic training sessions - included in mining
     * - true: Synthetic test sessions - excluded from mining
     */
    @Column(name = "is_test_set")
    private Boolean isTestSet;

    /**
     * Add a LO to the visited sequence if not already present.
     * @return true if added, false if already in sequence
     */
    public boolean addToSequence(Long loId) {
        if (visitedLoIds.contains(loId)) {
            return false;
        }
        visitedLoIds.add(loId);
        lastAccessedAt = Instant.now();
        return true;
    }

    /**
     * Mark a LO as done (completed).
     * Adds to visited, done, and completion sequence lists.
     * @return true if newly marked as done, false if already done
     */
    public boolean markAsDone(Long loId) {
        lastAccessedAt = Instant.now();

        // Add to visited sequence if not present
        addToSequence(loId);

        // Check if already marked as done
        if (doneLoIds.contains(loId)) {
            return false;
        }

        doneLoIds.add(loId);
        completionSequence.add(loId);  // Track in completion sequence for mining
        return true;
    }

    /**
     * Mark a LO as revisited (completed again after being marked done).
     * Only works if LO was previously marked as done.
     * @return true if marked as revisited, false if not yet done
     */
    public boolean markAsRevisited(Long loId) {
        lastAccessedAt = Instant.now();

        // Can only revisit if already marked as done
        if (!doneLoIds.contains(loId)) {
            return false;
        }

        // Add to visited sequence if somehow not present
        addToSequence(loId);

        // Track the revisit
        revisitedLoIds.add(loId);
        completionSequence.add(loId);  // Track in completion sequence for mining (allows duplicate)
        return true;
    }

    /**
     * Get ordered sequence of completed LO IDs (done + revisited).
     * Used for sequence mining to analyze actual learning patterns.
     *
     * This preserves chronological order and includes revisits as duplicates.
     * Example: [T1, E1, T1, A1] means T1 was completed, then E1, then T1 again, then A1.
     *
     * @return Ordered list of completed LO IDs with duplicates for revisits
     */
    public List<Long> getCompletionSequence() {
        return completionSequence;
    }
}
