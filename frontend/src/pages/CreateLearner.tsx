/**
 * Create Learner Page
 *
 * Allows creation of new learner profiles with FSLSM style preferences.
 *
 * Features:
 * - Name and email input
 * - Four FSLSM dimension sliders (-11 to +11)
 * - Link to official FSLSM questionnaire
 * - Live cluster preview
 * - Automatic learner selection after creation
 */

import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { api } from '@/adapters';
import { useLearner } from '@/contexts/LearnerContext';

export default function CreateLearner() {
  const navigate = useNavigate();
  const { setLearner } = useLearner();

  const [name, setName] = useState('');
  const [styleActiveReflective, setStyleActiveReflective] = useState(0);
  const [styleSensingIntuitive, setStyleSensingIntuitive] = useState(0);
  const [styleVisualVerbal, setStyleVisualVerbal] = useState(0);
  const [styleSequentialGlobal, setStyleSequentialGlobal] = useState(0);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Compute cluster key for preview
  const computeCluster = () => {
    const d1 = styleActiveReflective >= 0 ? 'ACT' : 'REF';
    const d2 = styleSensingIntuitive >= 0 ? 'SEN' : 'INT';
    const d3 = styleVisualVerbal >= 0 ? 'VIS' : 'VRB';
    const d4 = styleSequentialGlobal >= 0 ? 'SEQ' : 'GLO';
    return `${d1}_${d2}_${d3}_${d4}`;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError('Name is required');
      return;
    }

    setCreating(true);
    try {
      const newLearner = await api.createLearner({
        displayName: name.trim(),
        externalRef: `${name.trim().toLowerCase().replace(/\s+/g, '.')}@learner.local`,
        styleActiveReflective,
        styleSensingIntuitive,
        styleVisualVerbal,
        styleSequentialGlobal,
      });

      console.log('[CreateLearner] Created new learner:', newLearner);

      // Automatically select the new learner
      setLearner(newLearner);

      // Navigate to courses
      navigate('/courses');
    } catch (err) {
      console.error('[CreateLearner] Failed to create:', err);
      setError(err instanceof Error ? err.message : 'Failed to create learner');
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto p-6">
      {/* Header */}
      <div className="mb-6">
        <Link to="/courses" className="text-sm text-gray-600 hover:text-gray-900 mb-2 inline-block">
          ← Back to Courses
        </Link>
        <h1 className="text-3xl font-bold">Create New Learner Profile</h1>
      </div>

      {/* FSLSM Questionnaire Link */}
      <div className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
        <div className="flex items-start gap-3">
          <div className="flex-1">
            <h3 className="font-semibold mb-2">Determine Your Learning Style</h3>
            <p className="text-sm text-gray-700 mb-3">
              Take the official Felder-Silverman Learning Styles questionnaire to understand your learning preferences.
              The questionnaire will give you four scores ranging from -11 to +11 for each dimension.
            </p>
            <a
              href="https://learningstyles.webtools.ncsu.edu/"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm font-medium transition-colors"
            >
              Take FSLSM Questionnaire
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
              </svg>
            </a>
          </div>
        </div>
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Basic Info */}
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Name <span className="text-red-600">*</span>
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Enter your name"
              className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
            />
          </div>

        </div>

        {/* FSLSM Dimensions */}
        <div className="border rounded-lg p-6 bg-gray-50">
          <h3 className="text-lg font-semibold mb-4">FSLSM Learning Style Dimensions</h3>
          <p className="text-sm text-gray-600 mb-6">
            Adjust the sliders based on your questionnaire results, or use the default values (0) if you haven't taken it yet.
          </p>

          <div className="space-y-6">
            {/* Active/Reflective */}
            <FslsmSlider
              label="Active / Reflective"
              value={styleActiveReflective}
              onChange={setStyleActiveReflective}
              leftLabel="Reflective"
              rightLabel="Active"
              description="Do you prefer to learn by trying things out (Active) or by thinking things through first (Reflective)?"
            />

            {/* Sensing/Intuitive */}
            <FslsmSlider
              label="Sensing / Intuitive"
              value={styleSensingIntuitive}
              onChange={setStyleSensingIntuitive}
              leftLabel="Intuitive"
              rightLabel="Sensing"
              description="Do you prefer concrete, practical content (Sensing) or abstract, theoretical content (Intuitive)?"
            />

            {/* Visual/Verbal */}
            <FslsmSlider
              label="Visual / Verbal"
              value={styleVisualVerbal}
              onChange={setStyleVisualVerbal}
              leftLabel="Verbal"
              rightLabel="Visual"
              description="Do you remember best what you see (Visual) or what you hear/read (Verbal)?"
            />

            {/* Sequential/Global */}
            <FslsmSlider
              label="Sequential / Global"
              value={styleSequentialGlobal}
              onChange={setStyleSequentialGlobal}
              leftLabel="Global"
              rightLabel="Sequential"
              description="Do you learn in small incremental steps (Sequential) or in large jumps/big picture (Global)?"
            />
          </div>

          {/* Cluster Preview */}
          <div className="mt-6 p-4 bg-white border border-gray-200 rounded-lg">
            <div className="text-sm text-gray-600 mb-1">Your FSLSM Cluster:</div>
            <div className="font-mono font-bold text-lg text-blue-600">
              {computeCluster()}
            </div>
            <p className="text-xs text-gray-500 mt-2">
              This cluster determines your personalized learning path recommendations
            </p>
          </div>
        </div>

        {/* Error Message */}
        {error && (
          <div className="p-4 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {error}
          </div>
        )}

        {/* Actions */}
        <div className="flex gap-4">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="flex-1 px-6 py-3 border border-gray-300 rounded-lg hover:bg-gray-50 font-medium transition-colors"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={creating}
            className="flex-1 px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 font-medium transition-colors"
          >
            {creating ? 'Creating...' : 'Create Learner'}
          </button>
        </div>
      </form>
    </div>
  );
}

/**
 * FSLSM Slider Component
 * Range slider for individual learning style dimension
 */
interface FslsmSliderProps {
  label: string;
  value: number;
  onChange: (value: number) => void;
  leftLabel: string;
  rightLabel: string;
  description: string;
}

function FslsmSlider({ label, value, onChange, leftLabel, rightLabel, description }: FslsmSliderProps) {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-2">{label}</label>
      <p className="text-xs text-gray-600 mb-3">{description}</p>

      <div className="flex items-center gap-4">
        <span className="text-xs text-gray-600 w-20 text-right">{leftLabel}</span>

        <div className="flex-1 relative">
          <input
            type="range"
            min="-11"
            max="11"
            step="1"
            value={value}
            onChange={(e) => onChange(parseInt(e.target.value))}
            className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-blue-600"
          />
          <div className="flex justify-between text-xs text-gray-500 mt-1 px-1">
            <span>-11</span>
            <span>-5</span>
            <span>0</span>
            <span>+5</span>
            <span>+11</span>
          </div>
        </div>

        <span className="text-xs text-gray-600 w-20">{rightLabel}</span>
      </div>

      <div className="text-center mt-2">
        <span className="inline-block px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm font-medium">
          {value > 0 ? '+' : ''}{value}
        </span>
      </div>
    </div>
  );
}
