-- Comprehensive schema fix for all entity/database mismatches
-- All operations are conditional - only run if tables/columns exist

-- 1. Fix courses.title column length (256 → 120) - only if table exists
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='courses') THEN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='courses' AND column_name='title') THEN
      ALTER TABLE app.courses ALTER COLUMN title TYPE VARCHAR(120);
    END IF;
  END IF;
END $$;

-- 2. Fix mined_supports.support type - only if table exists
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='mined_supports') THEN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='mined_supports' AND column_name='support') THEN
      ALTER TABLE app.mined_supports ALTER COLUMN support TYPE DOUBLE PRECISION;
    END IF;
  END IF;
END $$;

-- 3. Add missing columns to sequence_stats - only if table exists
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='sequence_stats') THEN
    ALTER TABLE app.sequence_stats
      ADD COLUMN IF NOT EXISTS event_count INT DEFAULT 0,
      ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ DEFAULT now();
  END IF;
END $$;

-- 4. Create sequence_stats_ngram table - only if parent exists
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='sequence_stats') THEN
    CREATE TABLE IF NOT EXISTS app.sequence_stats_ngram (
      stats_id BIGINT NOT NULL REFERENCES app.sequence_stats(id) ON DELETE CASCADE,
      item VARCHAR(255) NOT NULL,
      position INT NOT NULL,
      PRIMARY KEY (stats_id, position)
    );
  END IF;
END $$;

-- 5. Create sequence_stats_learners table - only if parent exists
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='sequence_stats') THEN
    CREATE TABLE IF NOT EXISTS app.sequence_stats_learners (
      stats_id BIGINT NOT NULL REFERENCES app.sequence_stats(id) ON DELETE CASCADE,
      learner_id BIGINT NOT NULL
    );
  END IF;
END $$;

-- 6. Add ranker_config.created_at - only if table exists
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='ranker_config') THEN
    ALTER TABLE app.ranker_config
      ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT now();
  END IF;
END $$;

-- 7. Add UNIQUE constraint on ranker_config.version - only if table exists
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='ranker_config') THEN
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.table_constraints
      WHERE table_schema='app' AND table_name='ranker_config'
        AND constraint_name='uk_ranker_config_version'
    ) THEN
      ALTER TABLE app.ranker_config
        ADD CONSTRAINT uk_ranker_config_version UNIQUE (version);
    END IF;
  END IF;
END $$;

-- 8. Fix generated_los.type if still ENUM - only if table exists
-- Must drop CHECK constraint first, then convert type, then re-add CHECK
DO $$
DECLARE
  constraint_name_var TEXT;
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='generated_los') THEN
    IF EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='app' AND table_name='generated_los'
        AND column_name='type' AND udt_name='lo_type'
    ) THEN
      -- Find and drop any CHECK constraints on the type column
      FOR constraint_name_var IN
        SELECT tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
        WHERE tc.table_schema = 'app'
          AND tc.table_name = 'generated_los'
          AND tc.constraint_type = 'CHECK'
          AND ccu.column_name = 'type'
      LOOP
        EXECUTE 'ALTER TABLE app.generated_los DROP CONSTRAINT ' || constraint_name_var;
      END LOOP;

      -- Convert column type from enum to VARCHAR
      ALTER TABLE app.generated_los
        ALTER COLUMN type TYPE VARCHAR(32) USING type::text;

      -- Re-add CHECK constraint for VARCHAR
      ALTER TABLE app.generated_los
        ADD CONSTRAINT chk_generated_los_type CHECK (type IN ('A', 'Test'));
    END IF;
  END IF;
END $$;

-- 9. Fix mined_supports.next_type if still ENUM - convert to VARCHAR
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='mined_supports') THEN
    IF EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='app' AND table_name='mined_supports'
        AND column_name='next_type' AND udt_name='lo_type'
    ) THEN
      -- Convert next_type from enum to VARCHAR(32)
      ALTER TABLE app.mined_supports
        ALTER COLUMN next_type TYPE VARCHAR(32) USING next_type::text;
    END IF;
  END IF;
END $$;
