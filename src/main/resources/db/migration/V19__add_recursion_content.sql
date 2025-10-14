-- Add concept code column if not exists and set sourceUri for content files
-- This migration updates the Recursion concept to use markdown content files

-- Add code column to concepts table if not exists
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'app'
      AND table_name = 'concepts'
      AND column_name = 'code'
  ) THEN
    ALTER TABLE app.concepts ADD COLUMN code VARCHAR(50);
  END IF;
END $$;

-- Set code for existing Recursion concept
UPDATE app.concepts
SET code = 'RECURSION'
WHERE name = 'Recursion'
  AND code IS NULL;

-- Update or insert learning objects to point to markdown files
-- Theory (T) - Pure theory content
DO $$
DECLARE
  recursion_concept_id BIGINT;
BEGIN
  SELECT id INTO recursion_concept_id FROM app.concepts WHERE code = 'RECURSION';

  IF recursion_concept_id IS NOT NULL THEN
    -- Update or insert Theory learning object
    IF EXISTS (SELECT 1 FROM app.learning_objects WHERE concept_id = recursion_concept_id AND type = 'T') THEN
      UPDATE app.learning_objects
      SET source_uri = 'classpath:content/RECURSION_T_1.md',
          title = 'Recursion - Theory'
      WHERE concept_id = recursion_concept_id
        AND type = 'T'
        AND id = (SELECT MIN(id) FROM app.learning_objects WHERE concept_id = recursion_concept_id AND type = 'T');
    ELSE
      INSERT INTO app.learning_objects (concept_id, type, source_uri, title, est_time_min, media_variant)
      VALUES (recursion_concept_id, 'T', 'classpath:content/RECURSION_T_1.md', 'Recursion - Theory', 10, 'VERBAL');
    END IF;

    -- Update or insert Example 1: Fibonacci
    IF EXISTS (SELECT 1 FROM app.learning_objects WHERE concept_id = recursion_concept_id AND type = 'E') THEN
      UPDATE app.learning_objects
      SET source_uri = 'classpath:content/RECURSION_E_1.md',
          title = 'Recursion Example - Fibonacci'
      WHERE concept_id = recursion_concept_id
        AND type = 'E'
        AND id = (SELECT MIN(id) FROM app.learning_objects WHERE concept_id = recursion_concept_id AND type = 'E');
    ELSE
      INSERT INTO app.learning_objects (concept_id, type, source_uri, title, est_time_min, media_variant)
      VALUES (recursion_concept_id, 'E', 'classpath:content/RECURSION_E_1.md', 'Recursion Example - Fibonacci', 8, 'VISUAL');
    END IF;

    -- Insert Example 2: Factorial (new)
    IF NOT EXISTS (
      SELECT 1 FROM app.learning_objects
      WHERE concept_id = recursion_concept_id
        AND source_uri = 'classpath:content/RECURSION_E_2.md'
    ) THEN
      INSERT INTO app.learning_objects (concept_id, type, source_uri, title, est_time_min, media_variant)
      VALUES (recursion_concept_id, 'E', 'classpath:content/RECURSION_E_2.md', 'Recursion Example - Factorial', 8, 'VISUAL');
    END IF;

    -- Insert Figure 1: Call Stack Visualization
    IF NOT EXISTS (
      SELECT 1 FROM app.learning_objects
      WHERE concept_id = recursion_concept_id
        AND source_uri = 'classpath:content/RECURSION_F_1.md'
    ) THEN
      INSERT INTO app.learning_objects (concept_id, type, source_uri, title, est_time_min, media_variant)
      VALUES (recursion_concept_id, 'F', 'classpath:content/RECURSION_F_1.md', 'Recursion Figure - Call Stack', 5, 'VISUAL');
    END IF;

    -- Insert Figure 2: Fibonacci Recursion Tree
    IF NOT EXISTS (
      SELECT 1 FROM app.learning_objects
      WHERE concept_id = recursion_concept_id
        AND source_uri = 'classpath:content/RECURSION_F_2.md'
    ) THEN
      INSERT INTO app.learning_objects (concept_id, type, source_uri, title, est_time_min, media_variant)
      VALUES (recursion_concept_id, 'F', 'classpath:content/RECURSION_F_2.md', 'Recursion Figure - Fibonacci Tree', 5, 'VISUAL');
    END IF;
  END IF;
END $$;

-- Add unique constraint on concept code
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'concepts_code_key'
      AND conrelid = 'app.concepts'::regclass
  ) THEN
    ALTER TABLE app.concepts
      ADD CONSTRAINT concepts_code_key UNIQUE (code);
  END IF;
END $$;
