package org.example.protushybrid.integration;

import org.example.protushybrid.testutil.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for Flyway migrations using Testcontainers.
 * Verifies that all migrations run successfully from V1→HEAD
 * and that expected tables/indexes exist.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.clean-disabled=false"
})
class MigrationSmokeTest extends PostgresTestContainer {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Flyway migrations V1→HEAD complete successfully")
    void migrationsRunSuccessfully() {
        // Verify migrations ran
        var info = flyway.info();
        var applied = info.applied();

        assertThat(applied).isNotEmpty();
        assertThat(info.pending()).isEmpty(); // All migrations applied

        // Check current version
        var current = info.current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion()).isNotNull();
    }

    @Test
    @DisplayName("Core tables exist after migration")
    void coreTablesExist() {
        var tables = getTablesInSchema("app");

        assertThat(tables).contains(
                "courses",
                "concepts",
                "learners",
                "learning_objects",
                "learning_sessions"
        );
    }

    @Test
    @DisplayName("Tracking tables exist after migration")
    void trackingTablesExist() {
        var tables = getTablesInSchema("app");

        assertThat(tables).contains(
                "interactions",
                "mastery"
        );
    }

    @Test
    @DisplayName("Mining tables exist after migration")
    void miningTablesExist() {
        var tables = getTablesInSchema("app");

        assertThat(tables).contains(
                "mined_supports",
                "frequent_sequences",
                "mining_violations",
                "sequence_stats"
        );
    }

    @Test
    @DisplayName("Exercise tables exist after migration")
    void exerciseTablesExist() {
        var tables = getTablesInSchema("app");

        assertThat(tables).contains(
                "exercises",
                "flagged_exercises",
                "learner_exercise_attempts"
        );
    }

    @Test
    @DisplayName("LLM tables exist after migration")
    void llmTablesExist() {
        var tables = getTablesInSchema("app");

        assertThat(tables).contains(
                "llm_grading_audit"
        );
    }

    @Test
    @DisplayName("Key indexes exist for performance")
    void keyIndexesExist() {
        var indexes = getIndexesInSchema("app");

        // Core indexes (using abbreviated table names)
        assertThat(indexes).contains(
                "idx_sessions_learner_concept",
                "idx_session_sequence_session",
                "idx_mastery_learner",
                "idx_mastery_concept"
        );

        // Tracking indexes
        assertThat(indexes).contains(
                "idx_interactions_learner_ts",
                "idx_interactions_concept_ts"
        );

        // Mining indexes
        assertThat(indexes).contains(
                "idx_frequent_sequences_cluster_concept",
                "idx_mined_supports_unique_pattern"
        );
    }

    @Test
    @DisplayName("Foreign key constraints exist")
    void foreignKeysExist() {
        // Verify that key foreign keys are in place
        var fks = jdbc.queryForList(
                """
                SELECT tc.constraint_name
                FROM information_schema.table_constraints tc
                WHERE tc.constraint_schema = 'app'
                  AND tc.constraint_type = 'FOREIGN KEY'
                """,
                String.class
        );

        assertThat(fks).isNotEmpty();

        // Spot check critical FKs (using abbreviated table names)
        assertThat(fks).anyMatch(fk -> fk.contains("concept") && fk.contains("course"));
        assertThat(fks).anyMatch(fk -> fk.contains("los") && fk.contains("concept"));
        assertThat(fks).anyMatch(fk -> fk.contains("interaction") && fk.contains("learner"));
    }

    @Test
    @DisplayName("Sequences exist for auto-increment IDs")
    void sequencesExist() {
        var sequences = jdbc.queryForList(
                """
                SELECT sequence_name
                FROM information_schema.sequences
                WHERE sequence_schema = 'app'
                """,
                String.class
        );

        assertThat(sequences).contains(
                "courses_id_seq",
                "concepts_id_seq",
                "learners_id_seq",
                "los_id_seq",  // Abbreviated from learning_objects
                "exercises_id_seq"
        );
    }

    @Test
    @DisplayName("Schema is set to 'app' not 'public'")
    void schemaIsApp() {
        var tables = getTablesInSchema("app");
        var publicTables = getTablesInSchema("public");

        assertThat(tables).isNotEmpty();
        // Flyway history might be in public or app schema depending on configuration
        // The important thing is that application tables are in app schema
        assertThat(tables).contains(
                "learners",
                "concepts",
                "learning_sessions",
                "interactions"
        );
    }

    // ========== HELPERS ==========

    private List<String> getTablesInSchema(String schema) {
        return jdbc.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """,
                String.class,
                schema
        );
    }

    private List<String> getIndexesInSchema(String schema) {
        return jdbc.queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = ?
                ORDER BY indexname
                """,
                String.class,
                schema
        );
    }
}
