-- Add frequent_sequences and mining_violations tables for sequence mining

-- Frequent sequences discovered through mining
CREATE TABLE app.frequent_sequences (
    id BIGSERIAL PRIMARY KEY,
    cluster_key VARCHAR(64) NOT NULL,
    concept_id BIGINT REFERENCES app.concepts(id),
    support DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    n_learners INTEGER DEFAULT 0,
    last_mined_at TIMESTAMP,
    mining_snapshot_id VARCHAR(64)
);

-- Sequence items (ordered list of LO types)
CREATE TABLE app.sequence_items (
    sequence_id BIGINT NOT NULL REFERENCES app.frequent_sequences(id) ON DELETE CASCADE,
    lo_type VARCHAR(32) NOT NULL,
    item_order INTEGER NOT NULL,
    PRIMARY KEY (sequence_id, item_order)
);

-- Mining violations for SME review
CREATE TABLE app.mining_violations (
    id BIGSERIAL PRIMARY KEY,
    cluster_key VARCHAR(64) NOT NULL,
    concept_id BIGINT REFERENCES app.concepts(id),
    violation_reason VARCHAR(255),
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed BOOLEAN NOT NULL DEFAULT FALSE
);

-- Violation sequence items
CREATE TABLE app.violation_sequence (
    violation_id BIGINT NOT NULL REFERENCES app.mining_violations(id) ON DELETE CASCADE,
    lo_type VARCHAR(32) NOT NULL,
    item_order INTEGER NOT NULL,
    PRIMARY KEY (violation_id, item_order)
);

-- Indexes
CREATE INDEX idx_frequent_sequences_cluster_concept ON app.frequent_sequences(cluster_key, concept_id, support DESC);
CREATE INDEX idx_mining_violations_reviewed ON app.mining_violations(reviewed, detected_at DESC);
