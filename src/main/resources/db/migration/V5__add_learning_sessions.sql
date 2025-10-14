-- Add learning_sessions table for tracking user navigation through concept modules
-- Each session records the order in which a learner visits learning objects

CREATE TABLE app.learning_sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    learner_id BIGINT NOT NULL REFERENCES app.learners(id),
    concept_id BIGINT NOT NULL REFERENCES app.concepts(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP,
    completed BOOLEAN NOT NULL DEFAULT FALSE
);

-- Ordered sequence of visited LO IDs (each appears once)
CREATE TABLE app.session_sequence (
    session_id VARCHAR(36) NOT NULL REFERENCES app.learning_sessions(session_id) ON DELETE CASCADE,
    lo_id BIGINT NOT NULL,
    seq_order INTEGER NOT NULL,
    PRIMARY KEY (session_id, seq_order)
);

CREATE INDEX idx_sessions_learner_concept ON app.learning_sessions(learner_id, concept_id, completed, created_at);
CREATE INDEX idx_session_sequence_session ON app.session_sequence(session_id);
