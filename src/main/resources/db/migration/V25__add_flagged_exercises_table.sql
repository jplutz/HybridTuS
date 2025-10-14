-- Add table for flagged exercises that need review

CREATE TABLE app.flagged_exercises (
    id BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT NOT NULL REFERENCES app.exercises(id) ON DELETE CASCADE,
    learner_id BIGINT NOT NULL REFERENCES app.learners(id) ON DELETE CASCADE,
    user_answer TEXT NOT NULL,
    grading_score DOUBLE PRECISION,
    grading_feedback TEXT,
    flag_reason TEXT,
    flagged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_at TIMESTAMP,
    reviewer_notes TEXT
);

CREATE INDEX idx_flagged_exercises_exercise ON app.flagged_exercises(exercise_id);
CREATE INDEX idx_flagged_exercises_learner ON app.flagged_exercises(learner_id);
CREATE INDEX idx_flagged_exercises_reviewed ON app.flagged_exercises(reviewed);
