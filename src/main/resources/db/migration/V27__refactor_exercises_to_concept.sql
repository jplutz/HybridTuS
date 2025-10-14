-- Refactor exercises to link directly to concepts instead of learning objects
-- Removes the artificial "PRACTICE" LO container pattern

-- Add concept_id column
ALTER TABLE app.exercises ADD COLUMN concept_id BIGINT;

-- Populate concept_id from learning_object -> concept relationship
UPDATE app.exercises e
SET concept_id = lo.concept_id
FROM app.learning_objects lo
WHERE e.lo_id = lo.id;

-- Make concept_id NOT NULL now that it's populated
ALTER TABLE app.exercises ALTER COLUMN concept_id SET NOT NULL;

-- Add foreign key constraint
ALTER TABLE app.exercises ADD CONSTRAINT fk_exercises_concept
    FOREIGN KEY (concept_id) REFERENCES app.concepts(id) ON DELETE CASCADE;

-- Drop old index and foreign key
DROP INDEX IF EXISTS app.idx_exercises_lo_id;
ALTER TABLE app.exercises DROP CONSTRAINT IF EXISTS exercises_lo_id_fkey;

-- Drop lo_id column
ALTER TABLE app.exercises DROP COLUMN lo_id;

-- Create new index on concept_id
CREATE INDEX idx_exercises_concept_id ON app.exercises(concept_id);

-- Optional: Clean up any orphaned PRACTICE learning objects (if they exist)
-- These were only used as containers for exercises
DELETE FROM app.learning_objects WHERE type = 'PRACTICE';
