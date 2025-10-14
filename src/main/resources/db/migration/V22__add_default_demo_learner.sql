-- Add a default demo learner that will always exist on startup
-- This learner has a balanced FSLSM profile (all dimensions at 0.0)
-- and can be used immediately without creating a new learner

INSERT INTO app.learners (external_ref, display_name, style_active_reflective, style_sensing_intuitive, style_visual_verbal, style_sequential_global)
VALUES ('demo_learner_default', 'Demo Learner', 0.0, 0.0, 0.0, 0.0)
ON CONFLICT (external_ref) DO NOTHING;

-- Also create corresponding FSLSM profile entry
-- For balanced learner (0.0 scores), fromLearner logic assigns first pole (>= 0 check)
-- This results in cluster key: ACTIVE_SENSING_VISUAL_SEQUENTIAL
INSERT INTO app.fslsm_profiles (learner_id, active_reflective, sensing_intuitive, visual_verbal, sequential_global)
SELECT
    l.id,
    'ACTIVE',
    'SENSING',
    'VISUAL',
    'SEQUENTIAL'
FROM app.learners l
WHERE l.external_ref = 'demo_learner_default'
  AND NOT EXISTS (
    SELECT 1 FROM app.fslsm_profiles fp WHERE fp.learner_id = l.id
  );
