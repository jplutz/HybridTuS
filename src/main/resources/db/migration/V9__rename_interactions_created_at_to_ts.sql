-- Rename created_at to ts for consistency with Interaction entity

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema='app' AND table_name='interactions' AND column_name='created_at') THEN
    ALTER TABLE app.interactions RENAME COLUMN created_at TO ts;
  END IF;
END $$;
