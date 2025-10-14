-- V29: Add second demo learner with different learning style
-- and set up first learner with mastered LOOPS concept
--
-- This migration:
-- 1. Creates a second demo learner with REFLECTIVE_INTUITIVE_VERBAL_GLOBAL style
-- 2. Creates a completed learning session for first demo learner on LOOPS concept
-- 3. Records mastery/completion for demonstration purposes

-- =============================================================================
-- 1. Add second demo learner with different FSLSM style
-- =============================================================================
--
-- First learner (from V22): Balanced (0.0, 0.0, 0.0, 0.0) → ACTIVE_SENSING_VISUAL_SEQUENTIAL
-- Second learner: Opposite style (-5.0, -5.0, -5.0, -5.0) → REFLECTIVE_INTUITIVE_VERBAL_GLOBAL
--
-- FSLSM Scoring:
--   Negative values = first pole: Reflective, Intuitive, Verbal, Global
--   Positive values = second pole: Active, Sensing, Visual, Sequential
--   Range: -11 to +11

INSERT INTO app.learners (external_ref, display_name, style_active_reflective, style_sensing_intuitive, style_visual_verbal, style_sequential_global)
VALUES ('demo_learner_reflective', 'Demo Learner 2', -5.0, -5.0, -5.0, -5.0)
ON CONFLICT (external_ref) DO NOTHING;

-- Create corresponding FSLSM profile entry
INSERT INTO app.fslsm_profiles (learner_id, active_reflective, sensing_intuitive, visual_verbal, sequential_global)
SELECT
    l.id,
    'REFLECTIVE',
    'INTUITIVE',
    'VERBAL',
    'GLOBAL'
FROM app.learners l
WHERE l.external_ref = 'demo_learner_reflective'
  AND NOT EXISTS (
    SELECT 1 FROM app.fslsm_profiles fp WHERE fp.learner_id = l.id
  );

-- =============================================================================
-- 2. Set up first demo learner with completed LOOPS concept
-- =============================================================================
--
-- This creates a completed learning session where the learner has:
-- - Visited all 3 LOOPS learning objects (T, E, F)
-- - Completed them in order: Theory → Example → Figure
-- - Session is marked as completed
--
-- This demonstrates:
-- - UI showing a mastered/completed concept
-- - Recommendation system working on subsequent concepts
-- - Progress tracking and session history

-- Create a completed learning session for LOOPS concept
INSERT INTO app.learning_sessions (session_id, learner_id, concept_id, created_at, last_accessed_at, completed)
SELECT
    gen_random_uuid()::text,
    l.id,
    c.id,
    NOW() - INTERVAL '2 days',  -- Created 2 days ago
    NOW() - INTERVAL '2 days',  -- Last accessed 2 days ago
    true                         -- Marked as completed
FROM app.learners l
CROSS JOIN app.concepts c
WHERE l.external_ref = 'demo_learner_default'
  AND c.code = 'LOOPS'
  AND NOT EXISTS (
    SELECT 1 FROM app.learning_sessions s
    WHERE s.learner_id = l.id AND s.concept_id = c.id
  );

-- Get the session_id for the next inserts
DO $$
DECLARE
    v_session_id text;
    v_learner_id bigint;
    v_concept_id bigint;
    v_lo_theory_id bigint;
    v_lo_example_id bigint;
    v_lo_figure_id bigint;
BEGIN
    -- Get IDs
    SELECT id INTO v_learner_id FROM app.learners WHERE external_ref = 'demo_learner_default';
    SELECT id INTO v_concept_id FROM app.concepts WHERE code = 'LOOPS';
    SELECT session_id INTO v_session_id
    FROM app.learning_sessions
    WHERE learner_id = v_learner_id AND concept_id = v_concept_id;

    -- Get LO IDs for LOOPS
    SELECT id INTO v_lo_theory_id FROM app.learning_objects WHERE concept_id = v_concept_id AND type = 'T';
    SELECT id INTO v_lo_example_id FROM app.learning_objects WHERE concept_id = v_concept_id AND type = 'E';
    SELECT id INTO v_lo_figure_id FROM app.learning_objects WHERE concept_id = v_concept_id AND type = 'F';

    -- Add visited sequence (order user navigated): T → E → F
    INSERT INTO app.session_sequence (session_id, seq_order, lo_id)
    VALUES
        (v_session_id, 0, v_lo_theory_id),
        (v_session_id, 1, v_lo_example_id),
        (v_session_id, 2, v_lo_figure_id);

    -- Add done modules (all three completed)
    INSERT INTO app.session_done_modules (session_id, lo_id)
    VALUES
        (v_session_id, v_lo_theory_id),
        (v_session_id, v_lo_example_id),
        (v_session_id, v_lo_figure_id);

    -- Add completion sequence (chronological order of completions): T → E → F
    INSERT INTO app.session_completion_sequence (session_id, seq_order, lo_id)
    VALUES
        (v_session_id, 0, v_lo_theory_id),
        (v_session_id, 1, v_lo_example_id),
        (v_session_id, 2, v_lo_figure_id);
END $$;

-- =============================================================================
-- 3. Optional: Add mastery record if mastery tracking is enabled
-- =============================================================================
-- This section can be expanded if there's a mastery tracking table

-- Note: The completed session itself demonstrates mastery.
-- Future migrations can add explicit mastery scores if needed.
