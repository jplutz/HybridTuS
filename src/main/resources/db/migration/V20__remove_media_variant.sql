-- Remove media_variant column from learning_objects
-- The LO type system (T, E, F, A, Test) already encodes the visual/verbal dimension:
-- - F (Figure) = Visual content
-- - T (Theory) = Verbal/textual content
-- - E, A = Mixed modality
-- The media_variant field was redundant and never used in recommendation logic.

-- First make it nullable with default to allow V19 inserts to work if not specified
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'app'
      AND table_name = 'learning_objects'
      AND column_name = 'media_variant'
  ) THEN
    -- Make nullable
    ALTER TABLE app.learning_objects ALTER COLUMN media_variant DROP NOT NULL;

    -- Set default for any existing NULLs
    ALTER TABLE app.learning_objects ALTER COLUMN media_variant SET DEFAULT 'VISUAL';
  END IF;
END $$;

-- Drop CHECK constraint (if exists)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE table_schema = 'app'
      AND table_name = 'learning_objects'
      AND constraint_name = 'chk_media_variant'
  ) THEN
    ALTER TABLE app.learning_objects DROP CONSTRAINT chk_media_variant;
  END IF;
END $$;

-- Drop the column
ALTER TABLE app.learning_objects DROP COLUMN IF EXISTS media_variant;
