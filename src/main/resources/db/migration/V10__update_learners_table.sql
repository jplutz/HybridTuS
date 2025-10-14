-- Update learners table to match Learner entity

-- Rename external_id to external_ref
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema='app' AND table_name='learners' AND column_name='external_id') THEN
    ALTER TABLE app.learners RENAME COLUMN external_id TO external_ref;
  END IF;
END $$;

-- Add FSLSM style columns
ALTER TABLE app.learners
ADD COLUMN IF NOT EXISTS style_active_reflective DOUBLE PRECISION NOT NULL DEFAULT 0.0,
ADD COLUMN IF NOT EXISTS style_sensing_intuitive DOUBLE PRECISION NOT NULL DEFAULT 0.0,
ADD COLUMN IF NOT EXISTS style_visual_verbal DOUBLE PRECISION NOT NULL DEFAULT 0.0,
ADD COLUMN IF NOT EXISTS style_sequential_global DOUBLE PRECISION NOT NULL DEFAULT 0.0;
