-- Add tables to track done and revisited modules separately from visited
-- This allows distinguishing between:
-- - visited: just viewing a module
-- - done: marking a module as completed
-- - revisited: marking a module as completed again after being done

-- Table for modules marked as done (completed)
CREATE TABLE IF NOT EXISTS app.session_done_modules (
    session_id VARCHAR(36) NOT NULL,
    lo_id BIGINT NOT NULL,
    CONSTRAINT fk_session_done_modules_session
        FOREIGN KEY (session_id)
        REFERENCES app.learning_sessions(session_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_session_done_modules_lo
        FOREIGN KEY (lo_id)
        REFERENCES app.learning_objects(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_session_done_modules_session ON app.session_done_modules(session_id);

-- Table for modules that have been revisited (completed again after being done)
CREATE TABLE IF NOT EXISTS app.session_revisited_modules (
    session_id VARCHAR(36) NOT NULL,
    lo_id BIGINT NOT NULL,
    CONSTRAINT fk_session_revisited_modules_session
        FOREIGN KEY (session_id)
        REFERENCES app.learning_sessions(session_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_session_revisited_modules_lo
        FOREIGN KEY (lo_id)
        REFERENCES app.learning_objects(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_session_revisited_modules_session ON app.session_revisited_modules(session_id);

-- Migrate existing data: all visited modules are considered done
-- (for backward compatibility with existing sessions)
INSERT INTO app.session_done_modules (session_id, lo_id)
SELECT DISTINCT session_id, lo_id
FROM app.session_sequence
WHERE NOT EXISTS (
    SELECT 1 FROM app.session_done_modules sdm
    WHERE sdm.session_id = session_sequence.session_id
    AND sdm.lo_id = session_sequence.lo_id
);
