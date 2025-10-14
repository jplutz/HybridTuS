-- Add more concepts to CS101 course with prerequisite relationships
-- Concepts: Loops, Functions, Recursion (exists), Dynamic Programming
-- Prerequisite tree:
--   Loops (no prereqs)
--   Functions (no prereqs)
--   Recursion (requires Loops AND Functions)
--   Dynamic Programming (requires Recursion)
--
-- Note: V4 dropped the 'code' column from courses table, so we reference by title

-- Insert 3 new concepts
-- Note: V4 also dropped phase_id column from concepts table
INSERT INTO app.concepts (course_id, code, name)
VALUES
    -- Loops - foundational
    (
        (SELECT id FROM app.courses WHERE title = 'Intro CS'),
        'LOOPS',
        'Loops & Iteration'
    ),
    -- Functions - foundational
    (
        (SELECT id FROM app.courses WHERE title = 'Intro CS'),
        'FUNCTIONS',
        'Functions & Abstraction'
    ),
    -- Dynamic Programming - advanced
    (
        (SELECT id FROM app.courses WHERE title = 'Intro CS'),
        'DYNAMIC_PROGRAMMING',
        'Dynamic Programming'
    );

-- Add learning objects for LOOPS
INSERT INTO app.learning_objects (concept_id, type, title, source_uri, est_time_min)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'LOOPS'),
        'T',
        'Loops - Theory',
        'classpath:content/LOOPS_T_1.md',
        10
    ),
    (
        (SELECT id FROM app.concepts WHERE code = 'LOOPS'),
        'E',
        'Loops Example - For Loop',
        'classpath:content/LOOPS_E_1.md',
        8
    ),
    (
        (SELECT id FROM app.concepts WHERE code = 'LOOPS'),
        'F',
        'Loops Figure - Loop Flowchart',
        'classpath:content/LOOPS_F_1.md',
        5
    );

-- Add learning objects for FUNCTIONS
INSERT INTO app.learning_objects (concept_id, type, title, source_uri, est_time_min)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'FUNCTIONS'),
        'T',
        'Functions - Theory',
        'classpath:content/FUNCTIONS_T_1.md',
        10
    ),
    (
        (SELECT id FROM app.concepts WHERE code = 'FUNCTIONS'),
        'E',
        'Functions Example - Calculator',
        'classpath:content/FUNCTIONS_E_1.md',
        8
    ),
    (
        (SELECT id FROM app.concepts WHERE code = 'FUNCTIONS'),
        'F',
        'Functions Figure - Call Stack',
        'classpath:content/FUNCTIONS_F_1.md',
        5
    );

-- Add learning objects for DYNAMIC_PROGRAMMING
INSERT INTO app.learning_objects (concept_id, type, title, source_uri, est_time_min)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'DYNAMIC_PROGRAMMING'),
        'T',
        'Dynamic Programming - Theory',
        'classpath:content/DP_T_1.md',
        15
    ),
    (
        (SELECT id FROM app.concepts WHERE code = 'DYNAMIC_PROGRAMMING'),
        'E',
        'DP Example - Fibonacci Optimization',
        'classpath:content/DP_E_1.md',
        12
    ),
    (
        (SELECT id FROM app.concepts WHERE code = 'DYNAMIC_PROGRAMMING'),
        'F',
        'DP Figure - Memoization Table',
        'classpath:content/DP_F_1.md',
        5
    );

-- Add prerequisite relationships
-- Note: V4__table_changes.sql already created concept_prerequisites table
-- with columns: concept_id, prerequisite_id (composite PK, no surrogate id)

-- Add prerequisite relationships
-- Recursion requires Loops AND Functions
INSERT INTO app.concept_prerequisites (concept_id, prerequisite_id)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'RECURSION'),
        (SELECT id FROM app.concepts WHERE code = 'LOOPS')
    ),
    (
        (SELECT id FROM app.concepts WHERE code = 'RECURSION'),
        (SELECT id FROM app.concepts WHERE code = 'FUNCTIONS')
    );

-- Dynamic Programming requires Recursion
INSERT INTO app.concept_prerequisites (concept_id, prerequisite_id)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'DYNAMIC_PROGRAMMING'),
        (SELECT id FROM app.concepts WHERE code = 'RECURSION')
    );
