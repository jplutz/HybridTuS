/**
 * Practice Mode Page
 *
 * One-exercise-at-a-time practice flow with:
 * - LLM-generated exercises
 * - Immediate grading feedback
 * - Exercise flagging capability
 * - Progress tracking
 * - Results summary
 */

import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { api } from '@/adapters';
import { useLearner } from '@/contexts/LearnerContext';
import ChatBar from '@/components/chat/ChatBar';

interface PracticeSession {
  exercises: any[];
  currentIndex: number;
  answers: Map<number, any>;
  scores: Map<number, number>;
  startTime: Date;
  conceptName: string;
}

/**
 * Extract question text from exercises to avoid generating similar questions
 */
function extractPreviousQuestions(exercises: any[]): string[] {
  return exercises.map(exercise => {
    const questionJson = exercise.questionJson;
    const exerciseType = exercise.type || 'MC';

    // Extract question text based on exercise type
    if (exerciseType === 'MC' && questionJson.questions) {
      // New MC format with multiple questions
      return questionJson.questions.map((q: any) => q.text).join(' | ');
    } else if (exerciseType === 'MC' && questionJson.question) {
      // Legacy MC format
      return questionJson.question;
    } else if (exerciseType === 'GAP_FILL' && questionJson.template) {
      // Gap fill format
      return questionJson.template;
    } else if (exerciseType === 'FREE_TEXT' && questionJson.question) {
      // Free text format
      return questionJson.question;
    } else if (exerciseType === 'CODE_READ' && questionJson.question) {
      // Code comprehension format
      return questionJson.question;
    } else if (exerciseType === 'CODE_WRITE' && questionJson.description) {
      // Code writing format
      return questionJson.description;
    }

    return 'Unknown question format';
  }).filter(q => q !== 'Unknown question format');
}

export default function PracticeMode() {
  const { courseId, conceptId } = useParams<'courseId' | 'conceptId'>();
  const { learner, isGuest } = useLearner();
  const navigate = useNavigate();

  const [session, setSession] = useState<PracticeSession | null>(null);
  const [currentAnswer, setCurrentAnswer] = useState<any>(null);
  const [feedback, setFeedback] = useState<any | null>(null);
  const [grading, setGrading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showResults, setShowResults] = useState(false);
  const [currentMastery, setCurrentMastery] = useState<number | null>(null);

  // Initialize practice session
  useEffect(() => {
    if (!conceptId) return;

    // Require learner to be selected
    if (isGuest || !learner) {
      setLoading(false);
      return;
    }

    // Use learner.id as dependency to avoid re-runs when learner object reference changes
    const learnerId = learner.id;

    (async () => {
      try {
        setLoading(true);
        setError(null);

        // Fetch concept name first
        const concept = await api.getConcept(Number(conceptId));

        // Fetch existing exercises (excluding ones already attempted by this learner)
        let exercises = await api.getExercises(Number(conceptId), learnerId);

        // If no unattempted exercises exist, generate one
        if (exercises.length === 0) {
          console.log('[PracticeMode] No unattempted exercises found, generating new one...');

          try {
            // Generate exercise with random type
            const exerciseTypes = ['MC', 'GAP_FILL', 'FREE_TEXT', 'CODE_READ', 'CODE_WRITE'];
            const randomType = exerciseTypes[Math.floor(Math.random() * exerciseTypes.length)];
            console.log(`[PracticeMode] Generating ${randomType} exercise...`);
            const exercise = await api.generateExercise(Number(conceptId), randomType, 'medium', []);
            exercises = [exercise];
            console.log('[PracticeMode] Successfully generated exercise');
          } catch (genErr) {
            console.error('[PracticeMode] Exercise generation failed:', genErr);
            setError(
              'Failed to generate exercise for this concept. The LLM service may be unavailable. ' +
              (genErr instanceof Error ? genErr.message : '')
            );
            setLoading(false);
            return;
          }
        } else {
          console.log(`[PracticeMode] Found ${exercises.length} unattempted exercise(s)`);
        }

        // Start session
        setSession({
          exercises,
          currentIndex: 0,
          answers: new Map(),
          scores: new Map(),
          startTime: new Date(),
          conceptName: concept.title,
        });
      } catch (err) {
        console.error('[PracticeMode] Failed to initialize session:', err);
        setError(err instanceof Error ? err.message : 'Failed to load practice session');
      } finally {
        setLoading(false);
      }
    })();
  }, [conceptId, learner?.id, isGuest]); // Use learner.id instead of learner object

  const currentExercise = session?.exercises[session.currentIndex];

  // Log current exercise for debugging
  React.useEffect(() => {
    if (currentExercise) {
      console.log(`[PracticeMode] Current exercise: ID=${currentExercise.id}, Index=${session?.currentIndex}, Type=${currentExercise.type}`);
    }
  }, [currentExercise, session?.currentIndex]);

  const handleSubmit = async () => {
    if (!currentExercise || !currentAnswer || !learner || !session) return;

    setGrading(true);
    try {
      const result = await api.gradeExercise({
        exerciseId: currentExercise.id,
        learnerId: learner.id,
        userAnswer: JSON.stringify(currentAnswer),
      });

      // Calculate scoreRaw for historical/audit purposes (not used for EWMA)
      const scoreRaw = convertScoreToRaw(result.score);

      // Store score directly (0-1 scale) for transparent mastery tracking
      const newScores = new Map(session.scores);
      newScores.set(session.currentIndex, result.score);
      setSession({ ...session, scores: newScores });

      // Record interaction to update mastery (EWMA uses direct score: α=0.4, thresholds: <0.65 UNSAFE, 0.65-0.69 WORKING, ≥0.70 MASTERED)
      try {
        const interactionResult = await api.logInteraction({
          learnerId: learner.id,
          conceptId: currentExercise.conceptId,  // Use conceptId for exercise interactions
          score: result.score,
          scoreRaw: scoreRaw,  // Kept for historical/audit purposes
          hintCount: 0,
          durationSec: 60, // Approximate duration, can be improved with timer
        });

        if (interactionResult.recorded) {
          console.log(`[PracticeMode] ✓ Interaction recorded, mastery updated with direct score: ${result.score.toFixed(2)}`);
        } else {
          console.warn(`[PracticeMode] ⚠️  Interaction filtered by anti-gaming rules: ${interactionResult.message}`);
          console.warn('[PracticeMode] Mastery was NOT updated. You may be practicing too quickly or have reached the rate limit.');
        }
      } catch (interactionErr) {
        console.error('[PracticeMode] Failed to record interaction:', interactionErr);
        // Don't fail the entire flow if interaction recording fails
      }

      // Check stuck status for warning (doesn't change recommendations)
      try {
        const recommendation = await api.getNextRecommendation(learner.id, Number(conceptId));
        setFeedback({
          ...result,
          isStuck: recommendation.isStuckIntervention || false
        });
        console.log(`[PracticeMode] Stuck status checked: ${recommendation.isStuckIntervention ? 'STUCK' : 'OK'}`);
      } catch (recommendationErr) {
        console.error('[PracticeMode] Failed to check stuck status:', recommendationErr);
        // Continue without stuck detection if it fails
        setFeedback({ ...result, isStuck: false });
      }
    } catch (err) {
      console.error('[PracticeMode] Grading failed:', err);
      setError(err instanceof Error ? err.message : 'Grading failed');
    } finally {
      setGrading(false);
    }
  };

  // Convert score (0-1) to scoreRaw (1-5 scale) for historical/audit purposes only
  // Note: EWMA mastery tracking now uses direct score (0-1) for transparency
  const convertScoreToRaw = (score: number): number => {
    if (score >= 0.9) return 5;  // Excellent
    if (score >= 0.7) return 4;  // Very Good
    if (score >= 0.5) return 3;  // Good
    if (score >= 0.3) return 2;  // Fair
    return 1;                     // Needs Work
  };

  const handleNext = async () => {
    if (!session || !conceptId || !learner) return;

    // Clear current state
    setCurrentAnswer(null);
    setFeedback(null);
    setGrading(true);

    try {
      // First, try to get unattempted exercises from the database
      console.log('[PracticeMode] Fetching unattempted exercises...');
      const availableExercises = await api.getExercises(Number(conceptId), learner.id);

      let newExercise;

      // Filter out exercises already in current session
      const sessionExerciseIds = new Set(session.exercises.map(ex => ex.id));
      const unseenExercises = availableExercises.filter(ex => !sessionExerciseIds.has(ex.id));

      if (unseenExercises.length > 0) {
        // Use an existing unattempted exercise
        newExercise = unseenExercises[0];
        console.log(`[PracticeMode] Using existing exercise from pool (${unseenExercises.length} available)`);
      } else {
        // No unattempted exercises available, generate a new one with random type
        const exerciseTypes = ['MC', 'GAP_FILL', 'FREE_TEXT', 'CODE_READ', 'CODE_WRITE'];
        const randomType = exerciseTypes[Math.floor(Math.random() * exerciseTypes.length)];

        // Extract previous questions from current session to avoid similar exercises
        const previousQuestions = extractPreviousQuestions(session.exercises);

        console.log(`[PracticeMode] No pool exercises available, generating new ${randomType} exercise (avoiding ${previousQuestions.length} previous questions)...`);
        newExercise = await api.generateExercise(Number(conceptId), randomType, 'medium', previousQuestions);
        console.log('[PracticeMode] Successfully generated new exercise');
      }

      // Add new exercise to session and move to it
      setSession({
        ...session,
        exercises: [...session.exercises, newExercise],
        currentIndex: session.exercises.length, // Move to the newly added exercise
      });

      console.log(`[PracticeMode] Moved to exercise ${session.exercises.length + 1} (ID: ${newExercise.id})`);
    } catch (err) {
      console.error('[PracticeMode] Failed to load next exercise:', err);
      setError('Failed to load next exercise. ' + (err instanceof Error ? err.message : ''));
    } finally {
      setGrading(false);
    }
  };

  const handleFinishPractice = async () => {
    if (!session || !learner || !conceptId) return;

    // Fetch current mastery from database
    try {
      const masteryData = await api.getLearnerMastery(learner.id);
      const conceptMastery = masteryData[Number(conceptId)] ?? null;
      setCurrentMastery(conceptMastery);
    } catch (err) {
      console.error('[PracticeMode] Failed to fetch mastery:', err);
      // Continue showing results even if mastery fetch fails
    }

    setShowResults(true);
  };

  const handlePracticeAgain = async () => {
    if (!conceptId || !learner) return;

    // Reset states
    setShowResults(false);
    setLoading(true);
    setCurrentAnswer(null);
    setFeedback(null);

    try {
      // Fetch fresh exercises (excluding ones already attempted by this learner)
      let exercises = await api.getExercises(Number(conceptId), learner.id);

      // If no unattempted exercises exist, generate a new one
      if (exercises.length === 0) {
        console.log('[PracticeMode] No unattempted exercises, generating new one...');
        const exerciseTypes = ['MC', 'GAP_FILL', 'FREE_TEXT', 'CODE_READ', 'CODE_WRITE'];
        const randomType = exerciseTypes[Math.floor(Math.random() * exerciseTypes.length)];
        const exercise = await api.generateExercise(Number(conceptId), randomType, 'medium', []);
        exercises = [exercise];
      }

      // Start fresh session with new exercises
      setSession({
        ...session!,
        exercises,
        currentIndex: 0,
        answers: new Map(),
        scores: new Map(),
        startTime: new Date(),
      });
    } catch (err) {
      console.error('[PracticeMode] Failed to reload exercises:', err);
      setError('Failed to load new exercises. ' + (err instanceof Error ? err.message : ''));
    } finally {
      setLoading(false);
    }
  };

  const handleReturnToConcept = () => {
    navigate(`/courses/${courseId}/concepts/${conceptId}`);
  };

  const handleFlagExercise = async (reason: string) => {
    if (!currentExercise || !learner) return;

    try {
      // Pass the user's answer and grading results when flagging
      // Use the score (0-1 scale) directly from feedback
      await api.flagExercise(
        currentExercise.id,
        learner.id,
        reason,
        currentAnswer ? JSON.stringify(currentAnswer) : undefined,
        feedback?.score !== undefined ? feedback.score : undefined,
        feedback?.feedback || undefined
      );
      console.log('[PracticeMode] Exercise flagged:', currentExercise.id);
    } catch (err) {
      console.error('[PracticeMode] Failed to flag exercise:', err);
    }
  };

  // Build exercise context for chat
  const exerciseContext = currentExercise ? {
    exerciseId: currentExercise.id,
    type: currentExercise.type || 'MC',
    question: JSON.stringify(currentExercise.questionJson), // Serialize to string
    userAnswer: currentAnswer ? JSON.stringify(currentAnswer) : null,
    gradingScore: feedback?.score ?? null,
    gradingFeedback: feedback?.feedback || null,
  } : null;

  // Guest mode check
  if (isGuest || !learner) {
    return (
      <div className="max-w-3xl mx-auto p-6">
        <div className="border rounded-lg p-8 text-center bg-yellow-50 border-yellow-200">
          <h2 className="text-xl font-semibold mb-2">Learner Selection Required</h2>
          <p className="text-gray-600 mb-4">
            Please select a learner from the dropdown in the header to start practice mode.
          </p>
          <button
            onClick={() => navigate(`/courses/${courseId}/concepts/${conceptId}`)}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            Back to Concept
          </button>
        </div>
      </div>
    );
  }

  // Loading state
  if (loading) {
    return (
      <div className="max-w-3xl mx-auto p-6">
        <div className="text-center">
          <div className="text-xl font-semibold mb-2">Loading practice session...</div>
          <div className="text-gray-600">Preparing exercises for you</div>
        </div>
      </div>
    );
  }

  // Error state
  if (error || !session) {
    return (
      <div className="max-w-3xl mx-auto p-6">
        <div className="border rounded-lg p-6 bg-red-50 border-red-200">
          <div className="text-xl font-semibold text-red-700 mb-2">
            Failed to Load Practice
          </div>
          <div className="text-red-600 mb-4">{error || 'Session not found'}</div>
          <Link
            to={`/courses/${courseId}/concepts/${conceptId}`}
            className="inline-block px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            Back to Concept
          </Link>
        </div>
      </div>
    );
  }

  // Show results
  if (showResults && session) {
    return (
      <PracticeResults
        exercises={session.exercises}
        scores={session.scores}
        startTime={session.startTime}
        conceptName={session.conceptName}
        conceptId={Number(conceptId)}
        currentMastery={currentMastery}
        onPracticeAgain={handlePracticeAgain}
        onReturn={handleReturnToConcept}
      />
    );
  }

  return (
    <>
      {/* Main Content with padding for fixed chat */}
      <div className="max-w-3xl mx-auto p-6 pb-24">
        {/* Header */}
        <div className="mb-6">
          <div className="flex items-center justify-between">
            <h1 className="text-2xl font-bold">Practice: {session.conceptName}</h1>
            <Link
              to={`/courses/${courseId}/concepts/${conceptId}`}
              className="text-sm text-gray-600 hover:text-gray-900"
            >
              Exit Practice
            </Link>
          </div>
        </div>

        {/* Exercise Content */}
        {currentExercise && !feedback && (
          <div className="border rounded-lg p-6 bg-white shadow-sm">
            <ExerciseWidget
              exercise={currentExercise}
              onAnswer={setCurrentAnswer}
              disabled={grading}
            />

            <button
              onClick={handleSubmit}
              disabled={!currentAnswer || grading}
              className="mt-6 w-full bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
            >
              {grading ? 'Grading...' : 'Submit Answer'}
            </button>
          </div>
        )}

        {/* Feedback */}
        {feedback && currentExercise && (
          <GradingFeedback
            feedback={feedback}
            exercise={currentExercise}
            userAnswer={currentAnswer}
            exerciseId={currentExercise.id}
            learnerId={learner!.id}
            courseId={courseId!}
            conceptId={conceptId!}
            onNext={handleNext}
            onFinish={handleFinishPractice}
            onFlag={handleFlagExercise}
            isGenerating={grading}
          />
        )}
      </div>

      {/* Fixed Chat Bar */}
      {conceptId && (
        <div className="fixed bottom-0 left-0 right-0 z-50">
          <ChatBar
            conceptId={conceptId}
            contextPhase="Practice Exercise"
            exerciseContext={exerciseContext}
          />
        </div>
      )}
    </>
  );
}

/* ========== EXERCISE WIDGET ========== */

interface ExerciseWidgetProps {
  exercise: any;
  onAnswer: (answer: any) => void;
  disabled: boolean;
}

function ExerciseWidget({ exercise, onAnswer, disabled }: ExerciseWidgetProps) {
  // Question JSON is now an object (not a string) thanks to backend fix
  const question = exercise.questionJson;

  // Validate question data
  if (!question || typeof question !== 'object') {
    console.error('[ExerciseWidget] Invalid question data:', question);
    return (
      <div className="border border-red-200 rounded-lg p-6 bg-red-50">
        <div className="text-red-700 font-semibold mb-2">Error Loading Exercise</div>
        <div className="text-red-600 text-sm mb-4">
          This exercise has invalid data and cannot be displayed.
        </div>
      </div>
    );
  }

  const exerciseType = exercise.type || 'MC';

  return (
    <div>
      {/* Exercise Type Badge */}
      <div className="flex items-center gap-2 mb-4">
        <span className="px-2 py-1 bg-blue-100 text-blue-800 text-xs font-medium rounded">
          {exerciseType}
        </span>
      </div>

      {/* Route to appropriate widget */}
      {exerciseType === 'MC' && question.questions ? (
        <MCTwoQuestionWidget question={question} onAnswer={onAnswer} disabled={disabled} />
      ) : exerciseType === 'MC' ? (
        <MCLegacyWidget question={question} onAnswer={onAnswer} disabled={disabled} />
      ) : exerciseType === 'GAP_FILL' ? (
        <GapFillWidget question={question} onAnswer={onAnswer} disabled={disabled} />
      ) : exerciseType === 'CODE_READ' ? (
        <CodeReadWidget question={question} onAnswer={onAnswer} disabled={disabled} />
      ) : exerciseType === 'CODE_WRITE' ? (
        <CodeWriteWidget question={question} onAnswer={onAnswer} disabled={disabled} />
      ) : (
        <FreeTextWidget question={question} onAnswer={onAnswer} disabled={disabled} />
      )}
    </div>
  );
}

/* ========== MC TWO QUESTIONS (New Format) ========== */

interface MCTwoQuestionWidgetProps {
  question: any;
  onAnswer: (answer: any) => void;
  disabled: boolean;
}

function MCTwoQuestionWidget({ question, onAnswer, disabled }: MCTwoQuestionWidgetProps) {
  const [answers, setAnswers] = useState<Map<number, string>>(new Map());

  const handleSelect = (questionId: number, selectedAnswer: string) => {
    const newAnswers = new Map(answers);
    newAnswers.set(questionId, selectedAnswer);
    setAnswers(newAnswers);

    // Convert to format expected by backend
    onAnswer({
      answers: Array.from(newAnswers.entries()).map(([id, ans]) => ({
        questionId: id,
        selectedAnswer: ans
      }))
    });
  };

  // Null safety check
  if (!question || !question.questions || !Array.isArray(question.questions)) {
    return (
      <div className="text-red-600">
        Error: Question data is missing or malformed
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {question.questions.map((q: any, qIndex: number) => (
        <div key={q.id} className="border-b pb-6 last:border-b-0">
          <h4 className="font-semibold mb-3 text-gray-900">
            Question {qIndex + 1} of {question.questions.length}
          </h4>
          <p className="mb-4 text-lg">{q.text}</p>

          <div className="space-y-2">
            {q.options.map((option: string, optIndex: number) => {
              const isSelected = answers.get(q.id) === option;
              return (
                <button
                  key={optIndex}
                  onClick={() => handleSelect(q.id, option)}
                  disabled={disabled}
                  className={[
                    'w-full text-left px-4 py-3 border-2 rounded-lg transition-all',
                    isSelected
                      ? 'border-blue-500 bg-blue-50'
                      : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50',
                    disabled && 'opacity-50 cursor-not-allowed',
                  ].join(' ')}
                >
                  <div className="flex items-start gap-3">
                    <span className="font-medium text-gray-600 min-w-[24px]">
                      {String.fromCharCode(65 + optIndex)}.
                    </span>
                    <span>{option}</span>
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}

/* ========== MC LEGACY (Single Question) ========== */

interface MCLegacyWidgetProps {
  question: any;
  onAnswer: (answer: any) => void;
  disabled: boolean;
}

function MCLegacyWidget({ question, onAnswer, disabled }: MCLegacyWidgetProps) {
  const [selected, setSelected] = useState<number | null>(null);

  const handleSelect = (index: number) => {
    setSelected(index);
    onAnswer({ selectedIndex: index });
  };

  // Null safety check
  if (!question || !question.question) {
    return (
      <div className="text-red-600">
        Error: Question data is missing or malformed
      </div>
    );
  }

  return (
    <div>
      <h3 className="text-xl font-semibold mb-6">{question.question}</h3>

      <div className="space-y-3">
        {question.options && Array.isArray(question.options) && question.options.map((option: string, index: number) => (
          <button
            key={index}
            onClick={() => handleSelect(index)}
            disabled={disabled}
            className={[
              'w-full text-left px-4 py-3 border-2 rounded-lg transition-all',
              selected === index
                ? 'border-blue-500 bg-blue-50'
                : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50',
              disabled && 'opacity-50 cursor-not-allowed',
            ].join(' ')}
          >
            <div className="flex items-start gap-3">
              <span className="font-medium text-gray-600 min-w-[24px]">
                {String.fromCharCode(65 + index)}.
              </span>
              <span>{option}</span>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

/* ========== GAP FILL ========== */

interface GapFillWidgetProps {
  question: any;
  onAnswer: (answer: any) => void;
  disabled: boolean;
}

function GapFillWidget({ question, onAnswer, disabled }: GapFillWidgetProps) {
  const gaps = question.gaps || [];
  const template = question.template || question.question || '';
  const [gapAnswers, setGapAnswers] = useState<string[]>(new Array(gaps.length).fill(''));

  const handleGapChange = (index: number, value: string) => {
    const newAnswers = [...gapAnswers];
    newAnswers[index] = value;
    setGapAnswers(newAnswers);
    onAnswer(newAnswers); // Send as JSON array
  };

  // Parse template and render with inline input fields
  const renderTemplateWithGaps = () => {
    const parts: React.ReactNode[] = [];
    let remainingText = template;
    let lastIndex = 0;

    // Find all gap markers {{1}}, {{2}}, etc.
    const gapRegex = /\{\{(\d+)\}\}/g;
    let match;
    let keyCounter = 0;

    while ((match = gapRegex.exec(template)) !== null) {
      const gapNumber = parseInt(match[1]);
      const gapIndex = gapNumber - 1; // Convert to 0-based index

      // Add text before the gap
      if (match.index > lastIndex) {
        parts.push(
          <span key={`text-${keyCounter++}`}>
            {template.substring(lastIndex, match.index)}
          </span>
        );
      }

      // Add the input field for this gap
      const gap = gaps[gapIndex];
      parts.push(
        <span key={`gap-${gapNumber}`} className="inline-flex items-center">
          <input
            type="text"
            value={gapAnswers[gapIndex] || ''}
            onChange={(e) => handleGapChange(gapIndex, e.target.value)}
            disabled={disabled}
            placeholder={`Gap ${gapNumber}`}
            title={gap?.hint || `Fill in gap ${gapNumber}`}
            className="inline-block mx-1 px-3 py-1 border-2 border-blue-400 rounded bg-blue-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white disabled:opacity-50 min-w-[120px] max-w-[200px]"
          />
        </span>
      );

      lastIndex = match.index + match[0].length;
    }

    // Add any remaining text after the last gap
    if (lastIndex < template.length) {
      parts.push(
        <span key={`text-${keyCounter++}`}>
          {template.substring(lastIndex)}
        </span>
      );
    }

    return parts;
  };

  return (
    <div>
      <div className="text-lg mb-6 leading-relaxed">
        {renderTemplateWithGaps()}
      </div>

      {/* Show hints below if any gaps have them */}
      {gaps.some((gap: any) => gap.hint) && (
        <div className="mt-6 pt-4 border-t border-gray-200">
          <div className="text-sm font-medium text-gray-700 mb-2">Hints:</div>
          <div className="space-y-1">
            {gaps.map((gap: any, index: number) => (
              gap.hint && (
                <div key={gap.id || index} className="text-sm text-gray-600">
                  <span className="font-medium">Gap {index + 1}:</span> {gap.hint}
                </div>
              )
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/* ========== FREE TEXT ========== */

interface FreeTextWidgetProps {
  question: any;
  onAnswer: (answer: any) => void;
  disabled: boolean;
}

function FreeTextWidget({ question, onAnswer, disabled }: FreeTextWidgetProps) {
  const [answer, setAnswer] = useState('');

  const handleChange = (value: string) => {
    setAnswer(value);
    onAnswer(value);
  };

  return (
    <div>
      <h3 className="text-xl font-semibold mb-4">{question.question}</h3>

      <textarea
        value={answer}
        onChange={(e) => handleChange(e.target.value)}
        disabled={disabled}
        className="w-full border border-gray-300 rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 text-sm"
        rows={8}
        placeholder="Type your answer here..."
      />
    </div>
  );
}

/* ========== CODE READ ========== */

interface CodeReadWidgetProps {
  question: any;
  onAnswer: (answer: any) => void;
  disabled: boolean;
}

function CodeReadWidget({ question, onAnswer, disabled }: CodeReadWidgetProps) {
  const [answer, setAnswer] = useState('');

  const handleChange = (value: string) => {
    setAnswer(value);
    onAnswer(value);
  };

  return (
    <div>
      {/* Code Snippet */}
      <div className="mb-6">
        <div className="flex items-center justify-between mb-2">
          <h4 className="text-sm font-semibold text-gray-700">Code Snippet</h4>
          <span className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded">
            {question.language || 'Code'}
          </span>
        </div>
        <pre className="bg-gray-900 text-gray-100 rounded-lg p-4 overflow-x-auto">
          <code className="text-sm">{question.codeSnippet}</code>
        </pre>
      </div>

      {/* Question about the code */}
      <div className="mb-4">
        <h3 className="text-lg font-semibold mb-3">{question.question}</h3>
      </div>

      {/* Answer textarea */}
      <textarea
        value={answer}
        onChange={(e) => handleChange(e.target.value)}
        disabled={disabled}
        className="w-full border border-gray-300 rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 text-sm"
        rows={6}
        placeholder="Explain what the code does..."
      />
    </div>
  );
}

/* ========== CODE WRITE ========== */

interface CodeWriteWidgetProps {
  question: any;
  onAnswer: (answer: any) => void;
  disabled: boolean;
}

function CodeWriteWidget({ question, onAnswer, disabled }: CodeWriteWidgetProps) {
  const [code, setCode] = useState(question.starterCode || '');

  const handleChange = (value: string) => {
    setCode(value);
    onAnswer(value);
  };

  return (
    <div>
      {/* Problem description */}
      <div className="mb-6">
        <h3 className="text-lg font-semibold mb-3">Problem</h3>
        <div className="text-gray-700 whitespace-pre-wrap">{question.description}</div>
      </div>

      {/* Test cases (if provided) */}
      {question.testCases && question.testCases.length > 0 && (
        <div className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
          <h4 className="text-sm font-semibold text-blue-900 mb-2">Test Cases</h4>
          <div className="space-y-1 text-sm text-blue-800">
            {question.testCases.map((testCase: string, index: number) => (
              <div key={index} className="font-mono text-xs">• {testCase}</div>
            ))}
          </div>
        </div>
      )}

      {/* Code editor */}
      <div className="mb-2">
        <div className="flex items-center justify-between mb-2">
          <h4 className="text-sm font-semibold text-gray-700">Your Solution</h4>
          <span className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded">
            {question.language || 'Code'}
          </span>
        </div>
        <textarea
          value={code}
          onChange={(e) => handleChange(e.target.value)}
          disabled={disabled}
          className="w-full border border-gray-300 rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 font-mono text-sm bg-gray-900 text-gray-100"
          rows={15}
          placeholder="Write your code here..."
        />
      </div>
    </div>
  );
}

/* ========== GRADING FEEDBACK ========== */

interface GradingFeedbackProps {
  feedback: any;
  exercise: any;
  userAnswer: any;
  exerciseId: number;
  learnerId: number;
  courseId: string;
  conceptId: string;
  onNext: () => void;
  onFinish: () => void;
  onFlag: (reason: string) => void;
  isGenerating: boolean;
}

function GradingFeedback({ feedback, exercise, userAnswer, exerciseId, learnerId, courseId, conceptId, onNext, onFinish, onFlag, isGenerating }: GradingFeedbackProps) {
  const [showFlagDialog, setShowFlagDialog] = useState(false);
  const [flagReason, setFlagReason] = useState('');
  const [flagging, setFlagging] = useState(false);

  const handleFlag = async () => {
    setFlagging(true);
    try {
      await onFlag(flagReason);
      setShowFlagDialog(false);
      alert('Question flagged for review. Thank you!');
    } finally {
      setFlagging(false);
    }
  };

  // Get quality assessment based on score
  const getQualityAssessment = (score: number) => {
    if (score >= 0.9) {
      return {
        label: 'Excellent',
        color: 'text-green-700',
        bg: 'bg-green-50',
        border: 'border-green-500',
        description: 'Outstanding understanding!',
      };
    } else if (score >= 0.7) {
      return {
        label: 'Very Good',
        color: 'text-blue-700',
        bg: 'bg-blue-50',
        border: 'border-blue-500',
        description: 'Strong grasp of the concept',
      };
    } else if (score >= 0.5) {
      return {
        label: 'Good',
        color: 'text-yellow-700',
        bg: 'bg-yellow-50',
        border: 'border-yellow-500',
        description: 'Solid understanding with room to grow',
      };
    } else if (score >= 0.3) {
      return {
        label: 'Fair',
        color: 'text-orange-700',
        bg: 'bg-orange-50',
        border: 'border-orange-500',
        description: 'Partial understanding - keep practicing',
      };
    } else {
      return {
        label: 'Needs Work',
        color: 'text-red-700',
        bg: 'bg-red-50',
        border: 'border-red-500',
        description: 'Review the material and try again',
      };
    }
  };

  // Get mastery impact based on direct score (0-1 scale)
  const getMasteryImpact = (score: number) => {
    if (score >= 0.7) {
      return {
        icon: '↑↑',
        label: 'Strong Positive',
        color: 'text-green-700',
        description: 'This will significantly boost your mastery'
      };
    } else if (score >= 0.5) {
      return {
        icon: '↑',
        label: 'Moderate Positive',
        color: 'text-yellow-700',
        description: 'This will moderately increase your mastery'
      };
    } else if (score >= 0.3) {
      return {
        icon: '↓',
        label: 'Slight Negative',
        color: 'text-orange-700',
        description: 'This may slightly lower your mastery'
      };
    } else {
      return {
        icon: '↓↓',
        label: 'Negative',
        color: 'text-red-700',
        description: 'This will lower your mastery - review and practice more'
      };
    }
  };

  const quality = getQualityAssessment(feedback.score);
  const masteryImpact = getMasteryImpact(feedback.score);

  // Render formatted feedback for MC questions
  const renderFormattedFeedback = () => {
    const questionJson = exercise.questionJson;
    const exerciseType = exercise.type || 'MC';

    // For MC with multiple questions (new format)
    if (exerciseType === 'MC' && questionJson.questions && Array.isArray(questionJson.questions)) {
      return (
        <div className="space-y-4">
          {questionJson.questions.map((q: any, index: number) => {
            // Find user's answer for this question
            const userAnswerForQuestion = userAnswer?.answers?.find(
              (ans: any) => ans.questionId === q.id
            )?.selectedAnswer || 'No answer';

            const correctAnswer = q.correctAnswer || 'Unknown';
            const isCorrect = userAnswerForQuestion.toLowerCase() === correctAnswer.toLowerCase();

            return (
              <div key={q.id} className={`p-4 rounded-lg border-2 ${isCorrect ? 'border-green-200 bg-green-50' : 'border-red-200 bg-red-50'}`}>
                <div className="font-semibold mb-2">Question {index + 1}:</div>
                <p className="text-gray-800 mb-3">{q.text}</p>

                <div className="space-y-1 text-sm">
                  <div className="flex gap-2">
                    <span className="font-medium">Your answer:</span>
                    <span className={isCorrect ? 'text-green-700 font-medium' : 'text-red-700'}>{userAnswerForQuestion}</span>
                  </div>
                  {!isCorrect && (
                    <div className="flex gap-2">
                      <span className="font-medium">Correct answer:</span>
                      <span className="text-green-700 font-medium">{correctAnswer}</span>
                    </div>
                  )}
                  <div className="mt-2 flex items-start gap-2">
                    <span className={isCorrect ? 'text-green-600' : 'text-red-600'}>
                      {isCorrect ? '✓ Correct' : '✗ Incorrect'}
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      );
    }

    // For legacy MC (single question)
    if (exerciseType === 'MC' && questionJson.question) {
      const selectedIndex = userAnswer?.selectedIndex;
      const userAnswerText = selectedIndex !== undefined && questionJson.options?.[selectedIndex]
        ? questionJson.options[selectedIndex]
        : 'No answer';
      const correctAnswer = questionJson.correctAnswer || 'Unknown';

      return (
        <div className={`p-4 rounded-lg border-2 ${feedback.correct ? 'border-green-200 bg-green-50' : 'border-red-200 bg-red-50'}`}>
          <div className="font-semibold mb-2">Question:</div>
          <p className="text-gray-800 mb-3">{questionJson.question}</p>

          <div className="space-y-1 text-sm">
            <div className="flex gap-2">
              <span className="font-medium">Your answer:</span>
              <span className={feedback.correct ? 'text-green-700 font-medium' : 'text-red-700'}>{userAnswerText}</span>
            </div>
            {!feedback.correct && (
              <div className="flex gap-2">
                <span className="font-medium">Correct answer:</span>
                <span className="text-green-700 font-medium">{correctAnswer}</span>
              </div>
            )}
          </div>
        </div>
      );
    }

    // For GAP_FILL, parse and format the feedback
    if (exerciseType === 'GAP_FILL') {
      const feedbackText = feedback.feedback || '';

      // Try to extract score information from feedback
      const scoreMatch = feedbackText.match(/Score:\s*(\d+)\/(\d+)/i);
      const gapMatches = feedbackText.match(/Gap\s+\d+:.*?(?=Gap\s+\d+:|$)/gs);

      return (
        <div className="space-y-4">
          {scoreMatch && (
            <div className="p-3 rounded-lg bg-blue-50 border border-blue-200">
              <div className="font-semibold text-blue-900">
                Score: {scoreMatch[1]}/{scoreMatch[2]} gaps correct
              </div>
            </div>
          )}

          {gapMatches && gapMatches.length > 0 ? (
            <div className="space-y-2">
              {gapMatches.map((gapFeedback: string, index: number) => {
                const isCorrect = gapFeedback.includes('✓');
                const isClose = gapFeedback.includes('Close');

                return (
                  <div
                    key={index}
                    className={`p-3 rounded-lg border ${
                      isCorrect
                        ? 'bg-green-50 border-green-200'
                        : isClose
                        ? 'bg-yellow-50 border-yellow-200'
                        : 'bg-red-50 border-red-200'
                    }`}
                  >
                    <div className={`text-sm ${
                      isCorrect
                        ? 'text-green-800'
                        : isClose
                        ? 'text-yellow-800'
                        : 'text-red-800'
                    }`}>
                      {gapFeedback.trim()}
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
              <p className="text-gray-800 whitespace-pre-wrap">{feedbackText}</p>
            </div>
          )}
        </div>
      );
    }

    // For FREE_TEXT and CODE exercises, don't render here since we have a dedicated "AI Grader Feedback" section below
    if (exerciseType === 'FREE_TEXT' || exerciseType === 'CODE_WRITE' || exerciseType === 'CODE_READ') {
      return null;
    }

    // For other types, show raw feedback
    return (
      <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
        <p className="text-gray-800 whitespace-pre-wrap">{feedback.feedback}</p>
      </div>
    );
  };

  return (
    <div className="border rounded-lg p-6 bg-white shadow-sm">
      {/* Score Card Header */}
      <div className={`border-2 ${quality.border} ${quality.bg} rounded-lg p-6 mb-6`}>
        <div className="flex items-center justify-between mb-4">
          {/* Score */}
          <div>
            <div className="text-sm text-gray-600 mb-1">Your Score</div>
            <div className={`text-5xl font-bold ${quality.color}`}>
              {Math.round(feedback.score * 100)}%
            </div>
            <div className="text-sm text-gray-600 mt-1">
              ({feedback.score.toFixed(2)} / 1.00)
            </div>
          </div>

          {/* Quality Badge */}
          <div className="text-right">
            <div className={`text-2xl font-bold ${quality.color} mb-1`}>
              {quality.label}
            </div>
            <div className="text-sm text-gray-600">
              {quality.description}
            </div>
          </div>
        </div>

        {/* Mastery Impact Indicator */}
        <div className="border-t pt-4 mt-4">
          <div>
            <div className="text-sm font-semibold text-gray-700 mb-1">
              Mastery Impact
            </div>
            <div className={`flex items-center gap-2 ${masteryImpact.color} font-medium`}>
              <span className="text-2xl">{masteryImpact.icon}</span>
              <span>{masteryImpact.label}</span>
            </div>
          </div>
          <div className="text-xs text-gray-600 mt-2">
            {masteryImpact.description}
          </div>
        </div>
      </div>

      {/* Stuck Detection Warning Banner */}
      {feedback.isStuck && (
        <div className="mb-6 border-2 border-amber-400 bg-amber-50 rounded-lg p-5 shadow-sm">
          <div className="flex items-start gap-3">
            <div className="text-2xl mt-0.5">⚠️</div>
            <div className="flex-1">
              <h4 className="font-bold text-amber-900 text-lg mb-2">
                Having Trouble with This Concept?
              </h4>
              <p className="text-amber-800 mb-3">
                We've noticed you may be struggling with this concept. Consider reviewing the learning materials
                (theory, examples, videos) before continuing with more exercises. Understanding the fundamentals
                will help you succeed!
              </p>
              <Link
                to={`/courses/${courseId}/concepts/${conceptId}`}
                className="inline-flex items-center gap-2 px-4 py-2 bg-amber-600 text-white rounded-lg hover:bg-amber-700 font-medium transition-colors"
              >
                <span>Review Learning Materials</span>
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" />
                </svg>
              </Link>
            </div>
          </div>
        </div>
      )}

      {/* Formatted Feedback */}
      {renderFormattedFeedback() && (
        <div className="mb-6">
          {renderFormattedFeedback()}
        </div>
      )}

      {/* LLM Feedback for FREE_TEXT exercises */}
      {feedback.feedback && feedback.feedback.trim() && (exercise.type === 'FREE_TEXT' || exercise.type === 'CODE_WRITE' || exercise.type === 'CODE_READ') && (
        <div className="mb-6 p-5 bg-gradient-to-br from-purple-50 to-blue-50 rounded-lg border-2 border-purple-200 shadow-sm">
          <div className="flex items-center gap-2 mb-3">
            <svg className="w-5 h-5 text-purple-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
            </svg>
            <div className="font-semibold text-purple-900">AI Grader Feedback</div>
            {!feedback.deterministic && (
              <span className="text-xs px-2 py-0.5 bg-purple-200 text-purple-700 rounded-full">
                LLM Graded
              </span>
            )}
          </div>
          <p className="text-gray-800 whitespace-pre-wrap leading-relaxed">{feedback.feedback}</p>
        </div>
      )}

      {/* Additional Feedback for MC (if any) */}
      {feedback.feedback && feedback.feedback.trim() && exercise.type === 'MC' && (
        <div className="mb-6 p-4 bg-blue-50 rounded-lg border border-blue-200">
          <div className="font-semibold text-blue-900 mb-1">Additional Feedback:</div>
          <p className="text-blue-800 text-sm whitespace-pre-wrap">{feedback.feedback}</p>
        </div>
      )}

      {/* Actions */}
      <div className="flex items-center justify-between gap-4">
        <button
          onClick={() => setShowFlagDialog(true)}
          className="text-sm text-gray-600 hover:text-red-600 flex items-center gap-1 transition-colors"
        >
          Flag this question
        </button>

        <div className="flex gap-2">
          <button
            onClick={onFinish}
            className="border-2 border-gray-300 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-50 font-medium"
          >
            Finish Practice
          </button>
          <button
            onClick={onNext}
            disabled={isGenerating}
            className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 font-medium disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isGenerating ? 'Generating...' : 'Next Exercise →'}
          </button>
        </div>
      </div>

      {/* Flag Dialog */}
      {showFlagDialog && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg p-6 max-w-md w-full shadow-xl">
            <h3 className="text-xl font-bold mb-3">Report Issue</h3>
            <p className="text-sm text-gray-600 mb-4">
              This question will be hidden from future practice until reviewed by an admin.
            </p>
            <textarea
              placeholder="Optional: Describe the issue..."
              value={flagReason}
              onChange={(e) => setFlagReason(e.target.value)}
              className="w-full border border-gray-300 rounded-lg p-3 mb-4 focus:outline-none focus:ring-2 focus:ring-blue-500"
              rows={3}
            />
            <div className="flex gap-2">
              <button
                onClick={handleFlag}
                disabled={flagging}
                className="flex-1 bg-red-600 text-white px-4 py-2 rounded-lg hover:bg-red-700 disabled:opacity-50"
              >
                {flagging ? 'Flagging...' : 'Flag Question'}
              </button>
              <button
                onClick={() => setShowFlagDialog(false)}
                className="flex-1 border border-gray-300 px-4 py-2 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/* ========== PRACTICE RESULTS ========== */

interface PracticeResultsProps {
  exercises: any[];
  scores: Map<number, number>;
  startTime: Date;
  conceptName: string;
  conceptId: number;
  currentMastery: number | null;
  onPracticeAgain: () => void;
  onReturn: () => void;
}

function PracticeResults({
  exercises,
  scores,
  startTime,
  conceptName,
  conceptId,
  currentMastery,
  onPracticeAgain,
  onReturn
}: PracticeResultsProps) {
  // Calculate total score (scores are in 0-1 scale, convert to percentage)
  // Only count exercises that were actually graded (have scores)
  const scoreValues = Array.from(scores.values());
  const completedCount = scoreValues.length;
  const totalScore = scoreValues.reduce((sum, score) => sum + score, 0);
  const maxScore = completedCount; // Maximum is 1.0 per exercise
  const percentage = maxScore > 0 ? (totalScore / maxScore) * 100 : 0;

  // Calculate duration
  const durationMs = Date.now() - startTime.getTime();
  const minutes = Math.floor(durationMs / 60000);
  const seconds = Math.floor((durationMs % 60000) / 1000);

  // Determine mastery level based on percentage
  const getMasteryLevel = () => {
    if (percentage >= 80) return { label: 'Mastered', color: 'text-green-600', bg: 'bg-green-50', border: 'border-green-200' };
    if (percentage >= 60) return { label: 'Developing', color: 'text-yellow-600', bg: 'bg-yellow-50', border: 'border-yellow-200' };
    return { label: 'Needs Practice', color: 'text-orange-600', bg: 'bg-orange-50', border: 'border-orange-200' };
  };

  const mastery = getMasteryLevel();

  return (
    <div className="max-w-2xl mx-auto p-6">
      {/* Header */}
      <div className="text-center mb-8">
        <h2 className="text-3xl font-bold mb-2">Practice Complete</h2>
        <p className="text-gray-600">Concept: {conceptName}</p>
      </div>

      {/* Score Card */}
      <div className="bg-white border rounded-xl p-8 mb-6 text-center shadow-sm">
        <div className="text-6xl font-bold text-blue-600 mb-3">
          {Math.round(percentage)}%
        </div>
        <div className="text-gray-600 text-lg mb-2">
          {totalScore.toFixed(2)} / {maxScore.toFixed(2)} points
        </div>
        <div className="text-sm text-gray-500">
          {completedCount} {completedCount === 1 ? 'question' : 'questions'} completed in {minutes}m {seconds}s
        </div>
      </div>

      {/* Current Mastery from Database */}
      {currentMastery !== null && (
        <div className="bg-white border rounded-lg p-6 mb-6">
          <h3 className="font-semibold text-lg mb-3">Current Mastery Level</h3>

          {/* Mastery Value and Bar */}
          <div className="mb-4">
            <div className="flex items-center justify-between mb-2">
              <span className="text-2xl font-bold text-blue-600">
                {Math.round(currentMastery * 100)}%
              </span>
              <span className={`px-3 py-1 rounded-full text-sm font-medium ${
                currentMastery >= 0.70
                  ? 'bg-green-100 text-green-700'
                  : currentMastery >= 0.65
                  ? 'bg-yellow-100 text-yellow-700'
                  : 'bg-orange-100 text-orange-700'
              }`}>
                {currentMastery >= 0.70 ? 'Mastered' : currentMastery >= 0.65 ? 'Working' : 'Needs Practice'}
              </span>
            </div>
            <div className="h-3 bg-gray-200 rounded-full overflow-hidden">
              <div
                className={`h-full transition-all ${
                  currentMastery >= 0.70
                    ? 'bg-green-500'
                    : currentMastery >= 0.65
                    ? 'bg-yellow-500'
                    : 'bg-orange-500'
                }`}
                style={{ width: `${currentMastery * 100}%` }}
              />
            </div>
          </div>

          {/* EWMA Explanation */}
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
            <h4 className="font-semibold text-blue-900 mb-2 text-sm">How Mastery is Calculated</h4>
            <div className="text-sm text-blue-800 space-y-2">
              <p>
                Your mastery is tracked using <strong>EWMA (Exponentially Weighted Moving Average)</strong>,
                which gives more weight to recent performance while still considering past results.
              </p>
              <div className="grid grid-cols-1 gap-1 mt-2">
                <div><strong>Formula:</strong> New Mastery = 0.4 × Current Score + 0.6 × Previous Mastery</div>
                <div><strong>Starting Point:</strong> All concepts begin at 0% (no demonstrated knowledge yet)</div>
                <div><strong>Score Usage:</strong></div>
                <ul className="ml-4 list-disc text-xs">
                  <li>Direct score (0.0-1.0) from grading is used for mastery calculation</li>
                  <li>Both questions correct (1.0 or 0.9) → ~100% contribution</li>
                  <li>One question correct (0.5) → ~50% contribution</li>
                  <li>Both questions wrong (0.2 or 0.0) → ~0% contribution</li>
                </ul>
                <div className="mt-2"><strong>Thresholds:</strong></div>
                <ul className="ml-4 list-disc">
                  <li>&lt;65%: Needs Practice (orange)</li>
                  <li>65-69%: Working (yellow)</li>
                  <li>≥70%: Mastered (green)</li>
                </ul>
                <div className="mt-2"><strong>Expected Progress (with perfect scores):</strong></div>
                <ul className="ml-4 list-disc text-xs">
                  <li>2 exercises → 64% (approaching Working)</li>
                  <li>3 exercises → 78% (Mastered!)</li>
                  <li>4 exercises → 87% (Mastered)</li>
                </ul>
              </div>

              {/* Example Progression */}
              <div className="mt-3 pt-3 border-t border-blue-300">
                <h5 className="font-semibold text-blue-900 mb-2">Example: Mastery Development</h5>
                <div className="space-y-1.5 text-xs font-mono bg-white rounded p-2 border border-blue-200">
                  <div className="font-bold text-gray-700">Starting: 0% mastery (new concept)</div>
                  <div className="text-gray-600">
                    Exercise 1 (both correct): 0.4 × 100% + 0.6 × 0% = <strong className="text-orange-600">40%</strong> → Needs Practice
                  </div>
                  <div className="text-gray-600">
                    Exercise 2 (both correct): 0.4 × 100% + 0.6 × 40% = <strong className="text-orange-600">64%</strong> → Needs Practice
                  </div>
                  <div className="text-gray-600">
                    Exercise 3 (both correct): 0.4 × 100% + 0.6 × 64% = <strong className="text-green-600">78%</strong> ✓ Mastered!
                  </div>
                  <div className="text-gray-600">
                    Exercise 4 (both correct): 0.4 × 100% + 0.6 × 78% = <strong className="text-green-600">87%</strong> ✓ Mastered
                  </div>
                  <div className="text-gray-600">
                    Exercise 5 (one correct): 0.4 × 50% + 0.6 × 87% = <strong className="text-green-600">72%</strong> ✓ Still Mastered
                  </div>
                  <div className="text-gray-600">
                    Exercise 6 (both correct): 0.4 × 100% + 0.6 × 72% = <strong className="text-green-600">83%</strong> ✓ Mastered
                  </div>
                  <div className="mt-2 pt-2 border-t border-blue-200 text-blue-900">
                    <strong>Key insight:</strong> Takes 3 perfect exercises to reach Mastered from 0%;
                    occasional mistakes cause decline but mastery is forgiving
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Performance Breakdown */}
      <div className="bg-white border rounded-lg p-6 mb-6">
        <h3 className="font-semibold text-lg mb-4">Performance Breakdown</h3>
        <div className="space-y-3">
          {exercises.map((exercise, index) => {
            const score = scores.get(index) || 0;
            const scorePercent = score * 100; // Score is already 0-1 scale
            return (
              <div key={index} className="flex items-center gap-3">
                <span className="text-sm font-medium text-gray-600 min-w-[80px]">
                  Question {index + 1}
                </span>
                <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
                  <div
                    className={`h-full ${scorePercent >= 80 ? 'bg-green-500' : scorePercent >= 60 ? 'bg-yellow-500' : 'bg-orange-500'}`}
                    style={{ width: `${scorePercent}%` }}
                  />
                </div>
                <span className="text-sm font-medium text-gray-700 min-w-[60px] text-right">
                  {Math.round(scorePercent)}%
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Actions */}
      <div className="grid grid-cols-2 gap-4">
        <button
          onClick={onPracticeAgain}
          className="bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 font-medium text-center"
        >
          Practice Again
        </button>
        <button
          onClick={onReturn}
          className="border border-gray-300 px-6 py-3 rounded-lg hover:bg-gray-50 font-medium text-center"
        >
          Return to Concept
        </button>
      </div>

      {/* Encouragement Message */}
      <div className="mt-6 text-center">
        {percentage >= 80 ? (
          <p className="text-green-700 font-medium">
            Excellent work! You have shown strong mastery of this concept.
          </p>
        ) : percentage >= 60 ? (
          <p className="text-yellow-700 font-medium">
            Good progress! Keep practicing to strengthen your understanding.
          </p>
        ) : (
          <p className="text-orange-700 font-medium">
            Keep going! Review the material and try again.
          </p>
        )}
      </div>
    </div>
  );
}
