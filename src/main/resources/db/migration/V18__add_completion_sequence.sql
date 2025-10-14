-- Add completion sequence table to track done + revisited in chronological order
-- This table allows duplicates (for revisits) and preserves order via seq_order column
-- Used by sequence mining to analyze actual completion patterns, not just navigation

CREATE TABLE IF NOT EXISTS app.session_completion_sequence (
    session_id VARCHAR(36) NOT NULL,
    seq_order INT NOT NULL,
    lo_id BIGINT NOT NULL,
    CONSTRAINT fk_completion_sequence_session
        FOREIGN KEY (session_id)
        REFERENCES app.learning_sessions(session_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_completion_sequence_lo
        FOREIGN KEY (lo_id)
        REFERENCES app.learning_objects(id)
        ON DELETE CASCADE,
    PRIMARY KEY (session_id, seq_order)
);

CREATE INDEX idx_completion_sequence_session ON app.session_completion_sequence(session_id);

-- Note: No data migration needed since existing sessions have no completion tracking
-- Synthetic data generation and new user sessions will populate this table going forward
