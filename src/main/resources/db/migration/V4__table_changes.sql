CREATE SCHEMA IF NOT EXISTS app;

-- 1) courses
ALTER TABLE app.courses
  ADD COLUMN IF NOT EXISTS description TEXT;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='courses' AND column_name='code') THEN
    ALTER TABLE app.courses DROP COLUMN code;
  END IF;
END $$;

-- 2) concepts
ALTER TABLE app.concepts
  ADD COLUMN IF NOT EXISTS name          VARCHAR(120),
  ADD COLUMN IF NOT EXISTS description   TEXT,
  ADD COLUMN IF NOT EXISTS order_index   INT,
  ADD COLUMN IF NOT EXISTS course_id     BIGINT;

UPDATE app.concepts SET name = COALESCE(name, title);

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='concepts' AND column_name='title') THEN
    ALTER TABLE app.concepts DROP COLUMN title;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='concepts' AND column_name='code') THEN
    ALTER TABLE app.concepts DROP COLUMN code;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='concepts' AND column_name='phase_id') THEN
    ALTER TABLE app.concepts DROP COLUMN phase_id;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE table_schema='app' AND table_name='concepts' AND constraint_name='fk_concept_course'
  ) THEN
    ALTER TABLE app.concepts
      ADD CONSTRAINT fk_concept_course FOREIGN KEY (course_id) REFERENCES app.courses(id) ON DELETE CASCADE;
  END IF;
END $$;

UPDATE app.concepts SET name = CONCAT('Concept-', id) WHERE name IS NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE table_schema='app' AND table_name='concepts' AND constraint_name='uk_concept_name'
  ) THEN
    ALTER TABLE app.concepts ADD CONSTRAINT uk_concept_name UNIQUE (name);
  END IF;
END $$;

-- 3) learning_objects (migrate legacy los)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='los')
     AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='learning_objects') THEN
    ALTER TABLE app.los RENAME TO learning_objects;
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS app.learning_objects (
  id            BIGSERIAL PRIMARY KEY,
  concept_id    BIGINT REFERENCES app.concepts(id) ON DELETE CASCADE,
  type          VARCHAR(255) NOT NULL DEFAULT 'T',
  source_uri    VARCHAR(255),
  est_time_min  INT,
  rubric_link   TEXT
);

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='learning_objects' AND column_name='estimated_time') THEN
    ALTER TABLE app.learning_objects RENAME COLUMN estimated_time TO est_time_min;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='learning_objects' AND column_name='rubric_id') THEN
    ALTER TABLE app.learning_objects RENAME COLUMN rubric_id TO rubric_link;
  END IF;
END $$;

-- 4) concept_prerequisites (ManyToMany on Concept)
CREATE TABLE IF NOT EXISTS app.concept_prerequisites (
  concept_id       BIGINT NOT NULL REFERENCES app.concepts(id) ON DELETE CASCADE,
  prerequisite_id  BIGINT NOT NULL REFERENCES app.concepts(id) ON DELETE CASCADE,
  CONSTRAINT concept_prerequisites_pk PRIMARY KEY (concept_id, prerequisite_id),
  CONSTRAINT chk_concept_prereq_not_self CHECK (concept_id <> prerequisite_id)
);

-- 5) sequence_stats
CREATE TABLE IF NOT EXISTS app.sequence_stats (
  id           BIGSERIAL PRIMARY KEY,
  cluster_key  VARCHAR(32) NOT NULL,
  concept_id   BIGINT REFERENCES app.concepts(id) ON DELETE CASCADE
);

-- 6) ranker_config
CREATE TABLE IF NOT EXISTS app.ranker_config (
  id            BIGSERIAL PRIMARY KEY,
  version       VARCHAR(32)  NOT NULL,
  alpha_support DOUBLE PRECISION NOT NULL,
  beta_user     DOUBLE PRECISION NOT NULL,
  notes         TEXT
);

-- 7) interactions (lightweight event log)
CREATE TABLE IF NOT EXISTS app.interactions (
  id         BIGSERIAL PRIMARY KEY,
  learner_id BIGINT REFERENCES app.learners(id) ON DELETE CASCADE,
  concept_id BIGINT REFERENCES app.concepts(id) ON DELETE SET NULL,
  lo_id      BIGINT REFERENCES app.learning_objects(id) ON DELETE SET NULL,
  action     VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);