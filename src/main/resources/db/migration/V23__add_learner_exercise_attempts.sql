-- Track which exercises each learner has attempted to prevent duplicates

CREATE TABLE IF NOT EXISTS app.learner_exercise_attempts (
    id BIGSERIAL PRIMARY KEY,
    learner_id BIGINT NOT NULL REFERENCES app.learners(id) ON DELETE CASCADE,
    exercise_id BIGINT NOT NULL REFERENCES app.exercises(id) ON DELETE CASCADE,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_learner_exercise UNIQUE (learner_id, exercise_id)
);

CREATE INDEX idx_learner_exercise_attempts_learner ON app.learner_exercise_attempts(learner_id);
CREATE INDEX idx_learner_exercise_attempts_exercise ON app.learner_exercise_attempts(exercise_id);
