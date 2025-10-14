/**
 * Migration V16: Add optional title column to learning_objects
 *
 * Adds a nullable title field to allow custom names for learning objects.
 * When NULL, the application will auto-generate titles based on type and concept.
 */

-- Add title column to learning_objects table
ALTER TABLE app.learning_objects
ADD COLUMN IF NOT EXISTS title VARCHAR(255);

-- Add comment explaining the column
COMMENT ON COLUMN app.learning_objects.title IS 'Optional custom title for the learning object. If NULL, title will be auto-generated from type and concept name.';
