-- Add source_uri column that was missing from V4 CREATE TABLE IF NOT EXISTS
-- This is needed for figures and other media that reference external URIs

ALTER TABLE app.learning_objects
  ADD COLUMN IF NOT EXISTS source_uri VARCHAR(255);
