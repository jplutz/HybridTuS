-- Add exercises, mastery, and related tables

-- Exercises table
CREATE TABLE app.exercises (
    id BIGSERIAL PRIMARY KEY,
    lo_id BIGINT NOT NULL REFERENCES app.learning_objects(id) ON DELETE CASCADE,
    exercise_type VARCHAR(16) NOT NULL,
    question_json JSONB NOT NULL,
    correct_answer_json JSONB NOT NULL,
    max_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    grading_mode VARCHAR(16) NOT NULL,
    feedback_prompt TEXT,
    is_checkpoint BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_exercise_type CHECK (exercise_type IN ('MC', 'GAP_FILL', 'FREE_TEXT', 'CODE_WRITE', 'CODE_READ')),
    CONSTRAINT chk_grading_mode CHECK (grading_mode IN ('DETERMINISTIC', 'LLM', 'HYBRID'))
);

CREATE INDEX idx_exercises_lo_id ON app.exercises(lo_id);

-- LLM grading audit table
CREATE TABLE app.llm_grading_audit (
    id BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT NOT NULL REFERENCES app.exercises(id) ON DELETE CASCADE,
    learner_id BIGINT NOT NULL REFERENCES app.learners(id) ON DELETE CASCADE,
    user_answer_hash VARCHAR(64) NOT NULL,
    llm_score DOUBLE PRECISION,
    llm_feedback TEXT,
    duration_ms INTEGER,
    ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_llm_grading_audit_exercise ON app.llm_grading_audit(exercise_id);
CREATE INDEX idx_llm_grading_audit_learner ON app.llm_grading_audit(learner_id);

-- Learner cluster weights table
CREATE TABLE app.learner_cluster_weights (
    id BIGSERIAL PRIMARY KEY,
    learner_id BIGINT NOT NULL REFERENCES app.learners(id) ON DELETE CASCADE,
    cluster_code VARCHAR(32) NOT NULL,
    weight DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cluster_weight CHECK (weight >= 0 AND weight <= 1)
);

CREATE INDEX idx_learner_cluster_weights_learner ON app.learner_cluster_weights(learner_id);

-- Mastery table (composite key: learner_id + concept_id)
CREATE TABLE app.mastery (
    learner_id BIGINT NOT NULL REFERENCES app.learners(id) ON DELETE CASCADE,
    concept_id BIGINT NOT NULL REFERENCES app.concepts(id) ON DELETE CASCADE,
    mastery DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    event_count INTEGER NOT NULL DEFAULT 0,
    last_updated TIMESTAMP,
    PRIMARY KEY (learner_id, concept_id),
    CONSTRAINT chk_mastery_value CHECK (mastery >= 0 AND mastery <= 1),
    CONSTRAINT chk_event_count CHECK (event_count >= 0)
);

CREATE INDEX idx_mastery_learner ON app.mastery(learner_id);
CREATE INDEX idx_mastery_concept ON app.mastery(concept_id);
