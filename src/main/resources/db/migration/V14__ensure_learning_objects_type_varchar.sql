-- Ensure learning_objects.type column is VARCHAR, not enum
-- This migration ensures compatibility with Hibernate's default String mapping

DO $$
BEGIN
  -- Check if the column is of type app.lo_type (enum)
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='app'
      AND table_name='learning_objects'
      AND column_name='type'
      AND udt_name='lo_type'
  ) THEN
    -- Convert from enum back to VARCHAR
    ALTER TABLE app.learning_objects
      ALTER COLUMN type TYPE VARCHAR(255) USING type::text;
  END IF;

  -- Ensure column exists as VARCHAR if it somehow doesn't exist
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='app'
      AND table_name='learning_objects'
      AND column_name='type'
  ) THEN
    ALTER TABLE app.learning_objects
      ADD COLUMN type VARCHAR(255) NOT NULL DEFAULT 'T';
  END IF;
END $$;
