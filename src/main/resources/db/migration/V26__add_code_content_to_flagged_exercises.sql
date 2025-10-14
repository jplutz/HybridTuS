-- Add code_content column to flagged_exercises table
-- This field stores code separately for CODE_WRITE and CODE_READ exercises
-- to enable better logging, display, and LLM grading

ALTER TABLE app.flagged_exercises
ADD COLUMN code_content TEXT;

COMMENT ON COLUMN app.flagged_exercises.code_content IS 'Stores code content separately for CODE_WRITE and CODE_READ exercises to enable better logging, display, and LLM grading';
