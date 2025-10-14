-- Add n_learners column to track distinct learners per pattern
ALTER TABLE app.mined_supports
    ADD COLUMN IF NOT EXISTS n_learners INT NOT NULL DEFAULT 0 CHECK (n_learners >= 0);

-- Add unique composite index on (cluster_key, concept_id, suffix, next_type)
-- Drop old index first if it exists
DROP INDEX IF EXISTS app.mined_supports_cluster_key_concept_id_suffix_idx;

-- Create unique composite index as specified in mining policy
CREATE UNIQUE INDEX IF NOT EXISTS idx_mined_supports_unique_pattern
    ON app.mined_supports(cluster_key, concept_id, suffix, next_type);

-- Comment: The user_ngrams table is deprecated as of this migration.
-- Individual user mining has been removed in favor of pure group-based mining.
-- The table is retained for backward compatibility but is no longer populated or queried.
-- It can be safely dropped in a future migration if needed.
COMMENT ON TABLE app.user_ngrams IS 'DEPRECATED: Individual user mining removed. Use group-based mined_supports only.';
