/**
 * Admin Control Center
 *
 * Unified admin interface with two main tabs:
 * 1. Pipeline - Data generation, mining, validation, evaluation
 * 2. Flagged Exercises - Review and manage flagged exercises
 */

import React, { useState, useEffect } from 'react';
import { api } from '@/adapters';

type TabType = 'pipeline' | 'flagged';

export default function AdminControlCenter() {
  const [activeTab, setActiveTab] = useState<TabType>('pipeline');

  return (
    <div className="max-w-7xl mx-auto p-6">
      {/* Header */}
      <header className="mb-6">
        <h1 className="text-3xl font-bold">Admin Control Center</h1>
        <p className="text-gray-600 mt-2">
          Manage data pipeline and review flagged exercises
        </p>
      </header>

      {/* Tab Navigation */}
      <div className="border-b border-gray-200 mb-6">
        <div className="flex gap-4">
          <button
            onClick={() => setActiveTab('pipeline')}
            className={`w-48 px-6 py-3 border-b-2 font-medium transition-colors ${
              activeTab === 'pipeline'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-600 hover:text-gray-900'
            }`}
          >
            Pipeline
          </button>
          <button
            onClick={() => setActiveTab('flagged')}
            className={`w-48 px-6 py-3 border-b-2 font-medium transition-colors ${
              activeTab === 'flagged'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-600 hover:text-gray-900'
            }`}
          >
            Flagged Exercises
          </button>
        </div>
      </div>

      {/* Tab Content */}
      {activeTab === 'pipeline' && <PipelineTab />}
      {activeTab === 'flagged' && <FlaggedExercisesTab />}
    </div>
  );
}

/* ========== PIPELINE TAB ========== */

function PipelineTab() {
  const [dataGenReport, setDataGenReport] = useState<any | null>(null);
  const [miningStats, setMiningStats] = useState<any | null>(null);
  const [evalReport, setEvalReport] = useState<any | null>(null);
  const [validation, setValidation] = useState<any | null>(null);

  const [expandedSection, setExpandedSection] = useState<string | null>(null);
  const [loading, setLoading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notification, setNotification] = useState<{ type: 'info' | 'warning' | 'success', message: string } | null>(null);

  // Load data status on mount
  useEffect(() => {
    loadDataStatus();
  }, []);

  const loadDataStatus = async () => {
    try {
      const status = await api.getDataStatus();
      if (status.exists) {
        setDataGenReport(status);
      }
    } catch (err) {
      console.error('[AdminControlCenter] Failed to load data status:', err);
    }
  };

  const toggleSection = (section: string) => {
    setExpandedSection(expandedSection === section ? null : section);
  };

  const handleGenerateData = async () => {
    // Prevent regeneration if data already exists
    if (dataGenReport && dataGenReport.exists) {
      setError('Data already exists. Cannot regenerate. Please refresh the page to continue with existing data.');
      return;
    }

    setLoading('generate');
    setError(null);
    setNotification(null);
    try {
      const report = await api.generateData();
      setDataGenReport(report);

      setNotification({
        type: 'success',
        message: 'Data generation complete!'
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Data generation failed');
    } finally {
      setLoading(null);
    }
  };

  const handleRunMining = async () => {
    setLoading('mining');
    setError(null);
    try {
      const stats = await api.runMining();
      setMiningStats(stats);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Mining failed');
    } finally {
      setLoading(null);
    }
  };

  const handleRunEvaluation = async () => {
    setLoading('evaluation');
    setError(null);
    try {
      const report = await api.runEvaluation();
      setEvalReport(report);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Evaluation failed');
    } finally {
      setLoading(null);
    }
  };

  const handleCheckValidation = async () => {
    setLoading('validation');
    setError(null);
    try {
      const report = await api.checkValidation();
      setValidation(report);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Validation failed');
    } finally {
      setLoading(null);
    }
  };

  const handleRunPipeline = async () => {
    setLoading('pipeline');
    setError(null);
    setNotification(null);
    try {
      const result = await api.runPipeline();

      // Update state with results (may skip generation if data exists)
      if (result.generation) {
        setDataGenReport(result.generation);
      }
      setMiningStats(result.mining || null);
      setEvalReport(result.evaluation || null);
      setValidation(result.validation || null);

      setNotification({
        type: 'success',
        message: result.generationSkipped
          ? 'Pipeline completed successfully (data generation skipped - already exists)!'
          : 'Pipeline completed successfully!'
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Pipeline failed');
    } finally {
      setLoading(null);
    }
  };

  return (
    <div>
      {/* Run Complete Pipeline Button */}
      <button
        onClick={handleRunPipeline}
        disabled={loading !== null}
        className="w-full bg-green-600 text-white px-6 py-3 rounded-lg hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed font-semibold mb-6"
      >
        {loading === 'pipeline' ? 'Running...' : 'Run Complete Pipeline'}
      </button>

      {/* Notification Display */}
      {notification && (
        <div className={`mb-6 p-4 rounded-lg border ${
          notification.type === 'success' ? 'bg-green-50 border-green-200 text-green-700' :
          notification.type === 'warning' ? 'bg-yellow-50 border-yellow-200 text-yellow-700' :
          'bg-blue-50 border-blue-200 text-blue-700'
        }`}>
          <strong>{notification.type === 'success' ? 'Success:' : notification.type === 'warning' ? 'Warning:' : 'Info:'}</strong> {notification.message}
        </div>
      )}

      {/* Error Display */}
      {error && (
        <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700">
          <strong>Error:</strong> {error}
        </div>
      )}

      {/* Data Generation Section */}
      <DataGenerationSection
        report={dataGenReport}
        onGenerate={handleGenerateData}
        loading={loading === 'generate'}
      />

      {/* Mining Section */}
      <MiningSection
        stats={miningStats}
        onMine={handleRunMining}
        loading={loading === 'mining'}
        expanded={expandedSection === 'mining'}
        onToggle={() => toggleSection('mining')}
      />

      {/* Validation Section */}
      <ValidationSection
        validation={validation}
        onValidate={handleCheckValidation}
        loading={loading === 'validation'}
      />

      {/* Evaluation Section */}
      <EvaluationSection
        report={evalReport}
        onEvaluate={handleRunEvaluation}
        loading={loading === 'evaluation'}
        expanded={expandedSection === 'evaluation'}
        onToggle={() => toggleSection('evaluation')}
      />
    </div>
  );
}

/* ========== FLAGGED EXERCISES TAB ========== */

interface FlaggedExercise {
  id: number;
  exerciseId: number;
  learnerId: number;
  learnerName: string;
  exerciseType: string;
  questionText: string;
  userAnswer: string;
  gradingScore: number | null;
  gradingFeedback: string | null;
  flagReason: string;
  flaggedAt: string;
  reviewed: boolean;
  codeContent: string | null; // For CODE_WRITE (learner's code) and CODE_READ (code from question)
}

function FlaggedExercisesTab() {
  const [exercises, setExercises] = useState<FlaggedExercise[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<'all' | 'unreviewed' | 'reviewed'>('unreviewed');
  const [actioningFlagId, setActioningFlagId] = useState<number | null>(null);

  useEffect(() => {
    loadFlaggedExercises();
  }, []);

  const loadFlaggedExercises = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await api.getFlaggedExercises();
      setExercises(data);
    } catch (err) {
      console.error('[AdminControlCenter] Failed to load flagged exercises:', err);
      setError(err instanceof Error ? err.message : 'Failed to load flagged exercises');
    } finally {
      setLoading(false);
    }
  };

  const handleClearFlag = async (flagId: number) => {
    if (!confirm('Are you sure you want to clear this flag? The exercise will no longer be flagged.')) {
      return;
    }

    try {
      setActioningFlagId(flagId);
      await api.clearFlag(flagId);
      setExercises(exercises.filter(ex => ex.id !== flagId));
      alert('Flag cleared successfully');
    } catch (err) {
      console.error('[AdminControlCenter] Failed to clear flag:', err);
      alert('Failed to clear flag: ' + (err instanceof Error ? err.message : 'Unknown error'));
    } finally {
      setActioningFlagId(null);
    }
  };

  const handleAcceptFlag = async (flagId: number) => {
    if (!confirm('Accept this flag? The exercise will remain flagged but marked as reviewed.')) {
      return;
    }

    try {
      setActioningFlagId(flagId);
      await api.acceptFlag(flagId);
      setExercises(exercises.map(ex =>
        ex.id === flagId ? { ...ex, reviewed: true } : ex
      ));
      alert('Flag accepted and marked as reviewed');
    } catch (err) {
      console.error('[AdminControlCenter] Failed to accept flag:', err);
      alert('Failed to accept flag: ' + (err instanceof Error ? err.message : 'Unknown error'));
    } finally {
      setActioningFlagId(null);
    }
  };

  const filteredExercises = exercises.filter(ex => {
    if (filter === 'unreviewed') return !ex.reviewed;
    if (filter === 'reviewed') return ex.reviewed;
    return true;
  });

  if (loading) {
    return (
      <div className="text-center py-12">
        <div className="text-xl font-semibold mb-2">Loading flagged exercises...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="border rounded-lg p-6 bg-red-50 border-red-200">
        <div className="text-xl font-semibold text-red-700 mb-2">Error Loading Exercises</div>
        <div className="text-red-600">{error}</div>
        <button
          onClick={loadFlaggedExercises}
          className="mt-4 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Filter Tabs */}
      <div className="border-b border-gray-200">
        <div className="flex gap-4">
          <button
            onClick={() => setFilter('unreviewed')}
            className={`px-4 py-2 border-b-2 font-medium transition-colors ${
              filter === 'unreviewed'
                ? 'border-orange-500 text-orange-600'
                : 'border-transparent text-gray-600 hover:text-gray-900'
            }`}
          >
            Needs Review ({exercises.filter(ex => !ex.reviewed).length})
          </button>
          <button
            onClick={() => setFilter('reviewed')}
            className={`px-4 py-2 border-b-2 font-medium transition-colors ${
              filter === 'reviewed'
                ? 'border-orange-500 text-orange-600'
                : 'border-transparent text-gray-600 hover:text-gray-900'
            }`}
          >
            Reviewed ({exercises.filter(ex => ex.reviewed).length})
          </button>
          <button
            onClick={() => setFilter('all')}
            className={`px-4 py-2 border-b-2 font-medium transition-colors ${
              filter === 'all'
                ? 'border-orange-500 text-orange-600'
                : 'border-transparent text-gray-600 hover:text-gray-900'
            }`}
          >
            All ({exercises.length})
          </button>
        </div>
      </div>

      {/* Empty State */}
      {filteredExercises.length === 0 && (
        <div className="text-center py-12 border rounded-lg bg-gray-50">
          <svg className="w-16 h-16 mx-auto text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <h3 className="text-lg font-semibold text-gray-900 mb-2">
            {filter === 'unreviewed' ? 'No Exercises Need Review' :
             filter === 'reviewed' ? 'No Reviewed Exercises' :
             'No Flagged Exercises'}
          </h3>
          <p className="text-gray-600">
            {filter === 'unreviewed' ? 'All flagged exercises have been reviewed.' :
             filter === 'reviewed' ? 'No exercises have been reviewed yet.' :
             'No exercises have been flagged by learners yet.'}
          </p>
        </div>
      )}

      {/* Flagged Exercises List */}
      <div className="space-y-4">
        {filteredExercises.map((exercise) => (
          <div
            key={exercise.id}
            className={`border rounded-lg p-6 ${
              exercise.reviewed
                ? 'bg-gray-50 border-gray-300'
                : 'bg-white border-yellow-300 shadow-sm'
            }`}
          >
            {/* Header */}
            <div className="flex items-start justify-between mb-4">
              <div className="flex-1">
                <div className="flex items-center gap-3 mb-2">
                  <span className="px-2 py-1 bg-blue-100 text-blue-800 text-xs font-medium rounded">
                    {exercise.exerciseType}
                  </span>
                  <span className="text-sm text-gray-600">
                    Exercise ID: {exercise.exerciseId}
                  </span>
                  <span className="text-sm text-gray-600">
                    Flagged by: {exercise.learnerName}
                  </span>
                  {exercise.reviewed && (
                    <span className="px-2 py-1 bg-green-100 text-green-800 text-xs font-medium rounded">
                      Reviewed
                    </span>
                  )}
                </div>
                <p className="text-xs text-gray-500">
                  Flagged on {new Date(exercise.flaggedAt).toLocaleString()}
                </p>
              </div>
            </div>

            {/* Flag Reason */}
            <div className="mb-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
              <div className="font-semibold text-yellow-900 text-sm mb-1">Reason for Flagging:</div>
              <p className="text-sm text-yellow-800">{exercise.flagReason || 'No reason provided'}</p>
            </div>

            {/* Question */}
            <div className="mb-4">
              <div className="font-semibold text-gray-900 mb-2">Question:</div>
              <div className="p-3 bg-gray-50 border border-gray-200 rounded-lg">
                <p className="text-gray-800 whitespace-pre-wrap">{exercise.questionText}</p>
              </div>
            </div>

            {/* Code Content (for CODE_WRITE and CODE_READ) */}
            {exercise.codeContent && (exercise.exerciseType === 'CODE_WRITE' || exercise.exerciseType === 'CODE_READ') && (
              <div className="mb-4">
                <div className="flex items-center gap-2 mb-2">
                  <div className="font-semibold text-gray-900">
                    {exercise.exerciseType === 'CODE_WRITE' ? "Learner's Code:" : "Code Snippet (from question):"}
                  </div>
                  <span className="px-2 py-0.5 bg-indigo-100 text-indigo-800 text-xs font-medium rounded">
                    {exercise.exerciseType === 'CODE_WRITE' ? 'Written by Learner' : 'For Analysis'}
                  </span>
                </div>
                <div className="bg-gray-900 text-gray-100 rounded-lg p-4 overflow-x-auto border-2 border-indigo-300">
                  <pre className="text-sm font-mono">
                    <code>{exercise.codeContent}</code>
                  </pre>
                </div>
              </div>
            )}

            {/* User Answer */}
            <div className="mb-4">
              <div className="font-semibold text-gray-900 mb-2">
                {exercise.exerciseType === 'CODE_READ' ? "Learner's Explanation:" : "Learner's Answer:"}
              </div>
              <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg">
                <p className="text-gray-800 whitespace-pre-wrap">{exercise.userAnswer}</p>
              </div>
            </div>

            {/* Grading */}
            {(exercise.gradingScore !== null || exercise.gradingFeedback) && (
              <div className="mb-4">
                <div className="font-semibold text-gray-900 mb-2">Grading Results:</div>
                <div className="p-3 bg-purple-50 border border-purple-200 rounded-lg">
                  {exercise.gradingScore !== null && (
                    <div className="mb-2">
                      <span className="font-medium text-purple-900">Score:</span>{' '}
                      <span className="text-purple-800">{Math.round(exercise.gradingScore * 100)}%</span>
                    </div>
                  )}
                  {exercise.gradingFeedback && (
                    <div>
                      <span className="font-medium text-purple-900">Feedback:</span>
                      <p className="text-purple-800 whitespace-pre-wrap mt-1">{exercise.gradingFeedback}</p>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Action Buttons */}
            {!exercise.reviewed && (
              <div className="flex gap-3 pt-4 border-t border-gray-200">
                <button
                  onClick={() => handleClearFlag(exercise.id)}
                  disabled={actioningFlagId === exercise.id}
                  className="flex-1 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed font-medium transition-colors"
                >
                  {actioningFlagId === exercise.id ? 'Processing...' : 'Clear Flag (Remove)'}
                </button>
                <button
                  onClick={() => handleAcceptFlag(exercise.id)}
                  disabled={actioningFlagId === exercise.id}
                  className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed font-medium transition-colors"
                >
                  {actioningFlagId === exercise.id ? 'Processing...' : 'Accept & Mark Reviewed'}
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

/* ========== PIPELINE SECTION COMPONENTS ========== */

interface DataGenerationSectionProps {
  report: any | null;
  onGenerate: () => void;
  loading: boolean;
}

function DataGenerationSection({ report, onGenerate, loading }: DataGenerationSectionProps) {
  const dataExists = report && report.exists;

  return (
    <div className="border rounded-lg p-6 bg-white mb-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-xl font-bold">Step 1: Data Generation</h3>
        <button
          onClick={onGenerate}
          disabled={loading || dataExists}
          className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          title={dataExists ? 'Data already exists - regeneration is disabled' : ''}
        >
          {loading ? 'Generating...' : dataExists ? 'Data Already Generated' : 'Generate Synthetic Data'}
        </button>
      </div>

      {dataExists ? (
        <div className="space-y-2 text-sm">
          <div className="flex items-center gap-2">
            <span className="font-medium">Status:</span>
            <span className="text-green-600 font-semibold">Done</span>
          </div>
          <div className="text-gray-700">
            <div>• Learners: {report.numLearners || 'N/A'}</div>
            <div>• Sessions: {report.totalSessions || 'N/A'}</div>
            <div>• Interactions: {report.totalInteractions || 'N/A'}</div>
          </div>
          <div className="mt-3 p-3 bg-green-50 border border-green-200 rounded-lg">
            <p className="text-sm text-green-800">
              <strong>Info:</strong> Data already exists. Regeneration is disabled to prevent duplicate key errors. Proceed to the next steps.
            </p>
          </div>
        </div>
      ) : (
        <div className="text-gray-500 text-sm">No data generation run yet</div>
      )}
    </div>
  );
}

interface MiningSectionProps {
  stats: any | null;
  onMine: () => void;
  loading: boolean;
  expanded: boolean;
  onToggle: () => void;
}

function MiningSection({ stats, onMine, loading, expanded, onToggle }: MiningSectionProps) {
  return (
    <div className="border rounded-lg p-6 bg-white mb-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-xl font-bold">Step 2: Sequence Mining</h3>
        <div className="flex gap-2">
          {stats && (
            <button
              onClick={onToggle}
              className="text-blue-600 hover:underline text-sm"
            >
              {expanded ? '▲ Collapse' : '▼ View Details'}
            </button>
          )}
          <button
            onClick={onMine}
            disabled={loading}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Mining...' : 'Run Mining'}
          </button>
        </div>
      </div>

      {stats ? (
        <div className="space-y-2 text-sm">
          <div className="flex items-center gap-2">
            <span className="font-medium">Status:</span>
            <span className="text-green-600 font-semibold">Done</span>
          </div>
          <div className="text-gray-700">
            <div>• Patterns mined: {stats.patternsExtracted || 0}</div>
            <div>• Clusters: {stats.distinctClusters || 0}</div>
            <div>• Duration: {stats.durationMs || 0} ms</div>
          </div>

          {expanded && stats.patternDetails && (
            <div className="mt-4 p-4 bg-gray-50 rounded border border-gray-200">
              <div className="font-medium mb-2">Pattern Details:</div>
              <pre className="text-xs overflow-auto">
                {JSON.stringify(stats.patternDetails, null, 2)}
              </pre>
            </div>
          )}
        </div>
      ) : (
        <div className="text-gray-500 text-sm">No mining run yet</div>
      )}
    </div>
  );
}

interface EvaluationSectionProps {
  report: any | null;
  onEvaluate: () => void;
  loading: boolean;
  expanded: boolean;
  onToggle: () => void;
}

function EvaluationSection({ report, onEvaluate, loading, expanded, onToggle }: EvaluationSectionProps) {
  return (
    <div className="border rounded-lg p-6 bg-white mb-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-xl font-bold">Step 4: Evaluation</h3>
        <div className="flex gap-2">
          {report && (
            <button
              onClick={onToggle}
              className="text-blue-600 hover:underline text-sm"
            >
              {expanded ? '▲ Collapse' : '▼ View Per-Cluster Breakdown'}
            </button>
          )}
          <button
            onClick={onEvaluate}
            disabled={loading}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Evaluating...' : 'Run Evaluation'}
          </button>
        </div>
      </div>

      {report ? (
        <div className="space-y-2 text-sm">
          <div className="flex items-center gap-2">
            <span className="font-medium">Status:</span>
            <span className="text-green-600 font-semibold">Done</span>
          </div>
          <div className="text-gray-700">
            <div>• Pattern Mining Accuracy: {((report.accuracy || 0) * 100).toFixed(1)}%</div>
            <div>• FSLSM Baseline Accuracy: {((report.baselineAccuracy || 0) * 100).toFixed(1)}%</div>
            <div>• Improvement: +{((report.absoluteImprovement || 0) * 100).toFixed(1)} percentage points</div>
          </div>

          {expanded && report.perClusterBreakdown && (
            <div className="mt-4 p-4 bg-gray-50 rounded border border-gray-200">
              <div className="font-medium mb-2">Per-Cluster Breakdown:</div>
              <pre className="text-xs overflow-auto">
                {JSON.stringify(report.perClusterBreakdown, null, 2)}
              </pre>
            </div>
          )}
        </div>
      ) : (
        <div className="text-gray-500 text-sm">No evaluation run yet</div>
      )}
    </div>
  );
}

interface ValidationSectionProps {
  validation: any | null;
  onValidate: () => void;
  loading: boolean;
}

function ValidationSection({ validation, onValidate, loading }: ValidationSectionProps) {
  const allPassed = validation?.overallStatus === 'PASSED';

  return (
    <div className="border rounded-lg p-6 bg-white mb-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-xl font-bold">Step 3: Validation</h3>
        <button
          onClick={onValidate}
          disabled={loading}
          className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50"
        >
          {loading ? 'Checking...' : 'Check Thresholds'}
        </button>
      </div>

      {validation ? (
        <div className="space-y-2 text-sm">
          <div className="flex items-center gap-2">
            <span className="font-medium">Status:</span>
            <span className={`font-semibold ${allPassed ? 'text-green-600' : 'text-red-600'}`}>
              {validation.overallStatus}
            </span>
          </div>
          <div className="text-gray-700 space-y-1">
            <div className="flex items-center gap-2">
              <span className={validation.sessionCheckPassed ? 'text-green-600' : 'text-red-600'}>
                {validation.sessionCheckPassed ? '[PASS]' : '[FAIL]'}
              </span>
              <span>totalSessions: {validation.totalSessions} (min: {validation.minSessionsRequired})</span>
            </div>
            <div className="flex items-center gap-2">
              <span className={validation.patternCheckPassed ? 'text-green-600' : 'text-red-600'}>
                {validation.patternCheckPassed ? '[PASS]' : '[FAIL]'}
              </span>
              <span>totalPatterns: {validation.totalPatterns} (min: {validation.minPatternsRequired})</span>
            </div>
            <div className="flex items-center gap-2">
              <span className={validation.learnerCheckPassed ? 'text-green-600' : 'text-red-600'}>
                {validation.learnerCheckPassed ? '[PASS]' : '[FAIL]'}
              </span>
              <span>learnerThresholdCompliance: {validation.learnerThresholdCompliance} (min: {validation.minLearnersThreshold})</span>
            </div>
            <div className="flex items-center gap-2">
              <span className={validation.supportCheckPassed ? 'text-green-600' : 'text-red-600'}>
                {validation.supportCheckPassed ? '[PASS]' : '[FAIL]'}
              </span>
              <span>supportThresholdCompliance: {validation.supportThresholdCompliance} (min: {validation.minSupportThreshold})</span>
            </div>
          </div>
        </div>
      ) : (
        <div className="text-gray-500 text-sm">No validation run yet</div>
      )}
    </div>
  );
}
