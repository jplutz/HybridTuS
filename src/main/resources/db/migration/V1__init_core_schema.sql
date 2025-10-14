-- schema
CREATE SCHEMA IF NOT EXISTS app;

-- learners
CREATE TABLE app.learners (
                              id              BIGSERIAL PRIMARY KEY,
                              external_id     VARCHAR(64) UNIQUE,
                              display_name    VARCHAR(128) NOT NULL,
                              created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- fslsm profiles (one row per learner)
CREATE TABLE app.fslsm_profiles (
                                    learner_id      BIGINT PRIMARY KEY REFERENCES app.learners(id) ON DELETE CASCADE,
                                    active_reflective     VARCHAR(10) NOT NULL CHECK (active_reflective IN ('ACTIVE','REFLECTIVE')),
                                    sensing_intuitive     VARCHAR(10) NOT NULL CHECK (sensing_intuitive IN ('SENSING','INTUITIVE')),
                                    visual_verbal         VARCHAR(6)  NOT NULL CHECK (visual_verbal IN ('VISUAL','VERBAL')),
                                    sequential_global     VARCHAR(11) NOT NULL CHECK (sequential_global IN ('SEQUENTIAL','GLOBAL')),
                                    cluster_key     VARCHAR(64) GENERATED ALWAYS AS
                                        (active_reflective||'_'||sensing_intuitive||'_'||visual_verbal||'_'||sequential_global) STORED
);

-- course/phase/concept
CREATE TABLE app.courses (
                             id              BIGSERIAL PRIMARY KEY,
                             code            VARCHAR(64) UNIQUE NOT NULL,
                             title           VARCHAR(256) NOT NULL
);

CREATE TABLE app.phases (
                            id              BIGSERIAL PRIMARY KEY,
                            course_id       BIGINT NOT NULL REFERENCES app.courses(id) ON DELETE CASCADE,
                            phase_order     INT NOT NULL,
                            title           VARCHAR(256) NOT NULL,
                            UNIQUE(course_id, phase_order)
);

CREATE TABLE app.concepts (
                              id              BIGSERIAL PRIMARY KEY,
                              course_id       BIGINT NOT NULL REFERENCES app.courses(id) ON DELETE CASCADE,
                              phase_id        BIGINT NOT NULL REFERENCES app.phases(id) ON DELETE CASCADE,
                              code            VARCHAR(64) NOT NULL,
                              title           VARCHAR(256) NOT NULL,
                              UNIQUE(course_id, code)
);

-- learning objects (no pressure/difficulty/mastery-gain)
-- Using canonical single-letter codes: T, E, A, F, Test
CREATE TYPE app.lo_type AS ENUM ('T','E','A','F','Test');

CREATE TABLE app.los (
                         id              BIGSERIAL PRIMARY KEY,
                         concept_id      BIGINT NOT NULL REFERENCES app.concepts(id) ON DELETE CASCADE,
                         type            app.lo_type NOT NULL,
                         media_variant   VARCHAR(6) NOT NULL CHECK (media_variant IN ('VISUAL','VERBAL')),
                         estimated_time  INT NOT NULL CHECK (estimated_time > 0),
                         version         INT NOT NULL DEFAULT 1,
                         rubric_id       VARCHAR(64),
                         is_generated    BOOLEAN NOT NULL DEFAULT FALSE
);

-- prerequisite DAG (acyclic enforced in app code)
CREATE TABLE app.prerequisites (
                                   id              BIGSERIAL PRIMARY KEY,
                                   concept_id      BIGINT NOT NULL REFERENCES app.concepts(id) ON DELETE CASCADE,
                                   lo_src_id       BIGINT NOT NULL REFERENCES app.los(id) ON DELETE CASCADE,
                                   lo_dst_id       BIGINT NOT NULL REFERENCES app.los(id) ON DELETE CASCADE,
                                   UNIQUE (lo_src_id, lo_dst_id),
                                   CHECK (lo_src_id <> lo_dst_id)
);

-- events (append-only)
CREATE TABLE app.events (
                            id              BIGSERIAL PRIMARY KEY,
                            learner_id      BIGINT NOT NULL REFERENCES app.learners(id) ON DELETE CASCADE,
                            concept_id      BIGINT NOT NULL REFERENCES app.concepts(id) ON DELETE CASCADE,
                            lo_id           BIGINT NOT NULL REFERENCES app.los(id) ON DELETE CASCADE,
                            lo_type         app.lo_type NOT NULL,
                            ts              TIMESTAMPTZ NOT NULL DEFAULT now(),
                            score_raw       SMALLINT,                  -- 1..5 or NULL for ungraded
                            time_spent_ms   INT NOT NULL CHECK (time_spent_ms >= 0),
                            hint_count      SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX ON app.events (concept_id, ts);
CREATE INDEX ON app.events (learner_id, concept_id, ts);

-- mined supports per partition (cluster×concept, n-gram suffix)
CREATE TABLE app.mined_supports (
                                    id              BIGSERIAL PRIMARY KEY,
                                    cluster_key     VARCHAR(64) NOT NULL,
                                    concept_id      BIGINT NOT NULL REFERENCES app.concepts(id) ON DELETE CASCADE,
                                    suffix          TEXT NOT NULL,            -- e.g. 'THEORY>EXAMPLE'
                                    next_type       app.lo_type NOT NULL,
                                    support         NUMERIC(6,5) NOT NULL CHECK (support >= 0 AND support <= 1),
                                    confidence      NUMERIC(6,5),
                                    n_support       INT NOT NULL CHECK (n_support >= 0),
                                    last_mined_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON app.mined_supports (cluster_key, concept_id, suffix);

-- per-user n-grams (Laplace-ready counts)
CREATE TABLE app.user_ngrams (
                                 id              BIGSERIAL PRIMARY KEY,
                                 learner_id      BIGINT NOT NULL REFERENCES app.learners(id) ON DELETE CASCADE,
                                 concept_id      BIGINT NOT NULL REFERENCES app.concepts(id) ON DELETE CASCADE,
                                 suffix          TEXT NOT NULL,
                                 next_type       app.lo_type NOT NULL,
                                 trials          INT NOT NULL DEFAULT 0,
                                 succ            INT NOT NULL DEFAULT 0,
                                 UNIQUE (learner_id, concept_id, suffix, next_type)
);

-- generated exercises store
CREATE TABLE app.generated_los (
                                   id              BIGSERIAL PRIMARY KEY,
                                   base_lo_id      BIGINT NOT NULL REFERENCES app.los(id) ON DELETE CASCADE,
                                   concept_id      BIGINT NOT NULL REFERENCES app.concepts(id) ON DELETE CASCADE,
                                   type            app.lo_type NOT NULL CHECK (type IN ('A','Test')),
                                   version         INT NOT NULL DEFAULT 1,
                                   prompt_hash     VARCHAR(64),
                                   content_json    JSONB NOT NULL,
                                   created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                   human_reviewed  BOOLEAN NOT NULL DEFAULT FALSE
);
