-- minimal demo seed

-- insert one course
INSERT INTO app.courses (code, title)
VALUES ('CS101', 'Intro CS');

-- insert one phase linked to that course
INSERT INTO app.phases (course_id, phase_order, title)
VALUES (
           (SELECT id FROM app.courses WHERE code = 'CS101'),
           1,
           'Foundations'
       );

-- insert one concept linked to that course + phase
INSERT INTO app.concepts (course_id, phase_id, code, title)
VALUES (
           (SELECT id FROM app.courses WHERE code = 'CS101'),
           (SELECT id FROM app.phases WHERE title = 'Foundations'),
           'RECURSION',
           'Recursion'
       );

-- insert learning objects linked to that concept (using canonical types: T, E, A, F, Test)
INSERT INTO app.los (concept_id, type, media_variant, estimated_time, version, rubric_id)
VALUES
    ((SELECT id FROM app.concepts WHERE code = 'RECURSION'), 'T',    'VERBAL', 10, 1, NULL),
    ((SELECT id FROM app.concepts WHERE code = 'RECURSION'), 'E',    'VISUAL', 8,  1, NULL),
    ((SELECT id FROM app.concepts WHERE code = 'RECURSION'), 'A',    'VISUAL', 12, 1, 'rubric-basic'),
    ((SELECT id FROM app.concepts WHERE code = 'RECURSION'), 'Test', 'VERBAL', 6,  1, 'rubric-quiz');
