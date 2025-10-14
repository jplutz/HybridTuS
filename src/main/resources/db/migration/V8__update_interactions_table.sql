-- Add missing columns to interactions table

ALTER TABLE app.interactions
ADD COLUMN IF NOT EXISTS result VARCHAR(32),
ADD COLUMN IF NOT EXISTS score DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS score_raw SMALLINT,
ADD COLUMN IF NOT EXISTS hint_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS lo_type VARCHAR(32),
ADD COLUMN IF NOT EXISTS duration_s INTEGER;

-- Add index for better query performance
CREATE INDEX IF NOT EXISTS idx_interactions_learner_ts ON app.interactions(learner_id, created_at);
CREATE INDEX IF NOT EXISTS idx_interactions_concept_ts ON app.interactions(concept_id, created_at);
