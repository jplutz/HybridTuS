-- Add is_test_set flag to learning_sessions for train/test split tracking
-- This flag is used to exclude synthetic test sessions from mining while including all real user sessions
--
-- Values:
--   NULL  - Real user sessions (default) - INCLUDED in mining
--   false - Synthetic training sessions - INCLUDED in mining
--   true  - Synthetic test sessions - EXCLUDED from mining

ALTER TABLE app.learning_sessions
ADD COLUMN is_test_set BOOLEAN DEFAULT NULL;

-- Add index for efficient test session queries (used in evaluation)
CREATE INDEX idx_learning_sessions_is_test_set
ON app.learning_sessions(is_test_set)
WHERE is_test_set = true;

COMMENT ON COLUMN app.learning_sessions.is_test_set IS 'Flag for train/test split: NULL (real users), false (synthetic training), true (synthetic test)';
