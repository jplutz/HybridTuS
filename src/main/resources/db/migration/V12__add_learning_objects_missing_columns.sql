-- Add missing columns to learning_objects table
-- These were in the original los table but missing from the V4 CREATE TABLE IF NOT EXISTS statement

ALTER TABLE app.learning_objects
  ADD COLUMN IF NOT EXISTS media_variant VARCHAR(6) NOT NULL DEFAULT 'VISUAL',
  ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;

-- Add constraint to ensure media_variant is either VISUAL or VERBAL
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.constraint_column_usage
    WHERE table_schema='app' AND table_name='learning_objects' AND constraint_name='chk_media_variant'
  ) THEN
    ALTER TABLE app.learning_objects
      ADD CONSTRAINT chk_media_variant CHECK (media_variant IN ('VISUAL','VERBAL'));
  END IF;
END $$;
