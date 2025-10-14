-- Migration: Add Test modules to Intro CS course concepts
-- Description: Adds self-assessment test modules to all 4 concepts (Recursion, Loops, Functions, Dynamic Programming)

-- Test module for "Recursion" (concept_id = 1)
INSERT INTO learning_objects (concept_id, type, title, source_uri, est_time_min, version)
VALUES (
    1,
    'Test',
    'Self-Assessment: Recursion',
    'classpath:content/RECURSION_TEST_1.md',
    15,
    1
);

-- Test module for "Loops & Iteration" (concept_id = 2)
INSERT INTO learning_objects (concept_id, type, title, source_uri, est_time_min, version)
VALUES (
    2,
    'Test',
    'Self-Assessment: Loops & Iteration',
    'classpath:content/LOOPS_TEST_1.md',
    15,
    1
);

-- Test module for "Functions & Abstraction" (concept_id = 3)
INSERT INTO learning_objects (concept_id, type, title, source_uri, est_time_min, version)
VALUES (
    3,
    'Test',
    'Self-Assessment: Functions & Abstractions',
    'classpath:content/FUNCTIONS_TEST_1.md',
    15,
    1
);

-- Test module for "Dynamic Programming" (concept_id = 4)
INSERT INTO learning_objects (concept_id, type, title, source_uri, est_time_min, version)
VALUES (
    4,
    'Test',
    'Self-Assessment: Dynamic Programming',
    'classpath:content/DP_TEST_1.md',
    15,
    1
);
