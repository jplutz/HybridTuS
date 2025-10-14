-- Add test exercises and active learning activities to all CS concepts
-- Each concept gets:
-- 1. One TEST learning object with an exercise (MC, GAP_FILL, or FREE_TEXT)
-- 2. One ACTIVITY (A) learning object for active-reflective dimension engagement

-- ========================================
-- LOOPS - Test & Activity
-- ========================================

-- Insert TEST learning object for LOOPS
INSERT INTO app.learning_objects (concept_id, type, title, est_time_min)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'LOOPS'),
        'TEST',
        'Loops - Comprehension Test',
        15
    );

-- Insert MC exercise for LOOPS test
INSERT INTO app.exercises (lo_id, exercise_type, question_json, correct_answer_json, max_score, grading_mode, is_checkpoint)
VALUES
    (
        (SELECT id FROM app.learning_objects WHERE concept_id = (SELECT id FROM app.concepts WHERE code = 'LOOPS') AND type = 'TEST'),
        'MC',
        '{
            "questions": [
                {
                    "id": 1,
                    "text": "What is the purpose of a loop in programming?",
                    "options": ["To execute code once", "To repeat code execution multiple times", "To define a function", "To declare variables"],
                    "correctAnswer": "To repeat code execution multiple times"
                },
                {
                    "id": 2,
                    "text": "Which loop type checks the condition AFTER executing the loop body at least once?",
                    "options": ["for loop", "while loop", "do-while loop", "foreach loop"],
                    "correctAnswer": "do-while loop"
                }
            ]
        }'::jsonb,
        '{
            "answers": [
                {"questionId": 1, "correctAnswer": "To repeat code execution multiple times"},
                {"questionId": 2, "correctAnswer": "do-while loop"}
            ]
        }'::jsonb,
        1.0,
        'DETERMINISTIC',
        false
    );

-- Insert ACTIVITY learning object for LOOPS (Active Learning)
INSERT INTO app.learning_objects (concept_id, type, title, source_uri, est_time_min)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'LOOPS'),
        'A',
        'Loops - Trace Execution Activity',
        'classpath:content/LOOPS_A_1.md',
        12
    );

-- ========================================
-- FUNCTIONS - Test & Activity
-- ========================================

-- Insert TEST learning object for FUNCTIONS
INSERT INTO app.learning_objects (concept_id, type, title, est_time_min)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'FUNCTIONS'),
        'TEST',
        'Functions - Comprehension Test',
        15
    );

-- Insert GAP_FILL exercise for FUNCTIONS test
INSERT INTO app.exercises (lo_id, exercise_type, question_json, correct_answer_json, max_score, grading_mode, is_checkpoint)
VALUES
    (
        (SELECT id FROM app.learning_objects WHERE concept_id = (SELECT id FROM app.concepts WHERE code = 'FUNCTIONS') AND type = 'TEST'),
        'GAP_FILL',
        '{
            "template": "A function is a reusable block of code that performs a specific {{1}}. Functions can accept input values called {{2}} and return an output value using the {{3}} keyword.",
            "gaps": [
                {"id": 1, "hint": "purpose or operation"},
                {"id": 2, "hint": "input values"},
                {"id": 3, "hint": "keyword to send back result"}
            ]
        }'::jsonb,
        '{
            "gaps": [
                {"id": 1, "validAnswers": ["task", "operation", "function", "job", "purpose"]},
                {"id": 2, "validAnswers": ["parameters", "arguments", "args", "inputs"]},
                {"id": 3, "validAnswers": ["return", "returns"]}
            ]
        }'::jsonb,
        1.0,
        'DETERMINISTIC',
        false
    );

-- Insert ACTIVITY learning object for FUNCTIONS (Active Learning)
INSERT INTO app.learning_objects (concept_id, type, title, source_uri, est_time_min)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'FUNCTIONS'),
        'A',
        'Functions - Design Your Own Function',
        'classpath:content/FUNCTIONS_A_1.md',
        15
    );

-- ========================================
-- RECURSION - Test & Activity
-- ========================================

-- Insert TEST learning object for RECURSION
INSERT INTO app.learning_objects (concept_id, type, title, est_time_min)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'RECURSION'),
        'TEST',
        'Recursion - Comprehension Test',
        15
    );

-- Insert FREE_TEXT exercise for RECURSION test
INSERT INTO app.exercises (lo_id, exercise_type, question_json, correct_answer_json, max_score, grading_mode, is_checkpoint)
VALUES
    (
        (SELECT id FROM app.learning_objects WHERE concept_id = (SELECT id FROM app.concepts WHERE code = 'RECURSION') AND type = 'TEST'),
        'FREE_TEXT',
        '{
            "question": "Explain the two essential components that every recursive function must have to avoid infinite recursion. Provide a brief explanation of each component."
        }'::jsonb,
        '{
            "sampleAnswer": "Every recursive function must have: 1) Base case - a condition that stops the recursion when reached, providing a direct answer without further recursive calls. 2) Recursive case - the part where the function calls itself with a modified input that moves closer to the base case.",
            "keyPoints": [
                "base case",
                "stopping condition",
                "recursive case",
                "function calls itself",
                "modified input",
                "moves toward base case"
            ]
        }'::jsonb,
        1.0,
        'LLM',
        false
    );

-- Insert ACTIVITY learning object for RECURSION (Active Learning)
INSERT INTO app.learning_objects (concept_id, type, title, source_uri, est_time_min)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'RECURSION'),
        'A',
        'Recursion - Build the Call Stack',
        'classpath:content/RECURSION_A_1.md',
        20
    );

-- ========================================
-- DYNAMIC_PROGRAMMING - Test & Activity
-- ========================================

-- Insert TEST learning object for DYNAMIC_PROGRAMMING
INSERT INTO app.learning_objects (concept_id, type, title, est_time_min)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'DYNAMIC_PROGRAMMING'),
        'TEST',
        'Dynamic Programming - Comprehension Test',
        15
    );

-- Insert MC exercise for DYNAMIC_PROGRAMMING test
INSERT INTO app.exercises (lo_id, exercise_type, question_json, correct_answer_json, max_score, grading_mode, is_checkpoint)
VALUES
    (
        (SELECT id FROM app.learning_objects WHERE concept_id = (SELECT id FROM app.concepts WHERE code = 'DYNAMIC_PROGRAMMING') AND type = 'TEST'),
        'MC',
        '{
            "questions": [
                {
                    "id": 1,
                    "text": "What is the main advantage of dynamic programming over naive recursion?",
                    "options": ["Uses less memory", "Avoids recomputing overlapping subproblems", "Easier to implement", "Works only for specific problems"],
                    "correctAnswer": "Avoids recomputing overlapping subproblems"
                },
                {
                    "id": 2,
                    "text": "Which technique stores results of subproblems in a table or cache?",
                    "options": ["Greedy approach", "Divide and conquer", "Memoization", "Brute force"],
                    "correctAnswer": "Memoization"
                }
            ]
        }'::jsonb,
        '{
            "answers": [
                {"questionId": 1, "correctAnswer": "Avoids recomputing overlapping subproblems"},
                {"questionId": 2, "correctAnswer": "Memoization"}
            ]
        }'::jsonb,
        1.0,
        'DETERMINISTIC',
        false
    );

-- Insert ACTIVITY learning object for DYNAMIC_PROGRAMMING (Active Learning)
INSERT INTO app.learning_objects (concept_id, type, title, source_uri, est_time_min)
VALUES
    (
        (SELECT id FROM app.concepts WHERE code = 'DYNAMIC_PROGRAMMING'),
        'A',
        'Dynamic Programming - Optimize a Problem',
        'classpath:content/DP_A_1.md',
        20
    );
