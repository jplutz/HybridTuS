/**
 * LLM Content Warning Modal
 *
 * Displays a warning when user transitions from curated learning content
 * to LLM-generated practice exercises.
 *
 * Ensures users are aware they're leaving human-curated materials.
 */

import React from 'react';

interface LLMContentWarningProps {
  isOpen: boolean;
  onCancel: () => void;
  onContinue: () => void;
}

export default function LLMContentWarning({ isOpen, onCancel, onContinue }: LLMContentWarningProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg p-6 max-w-lg w-full shadow-xl">
        {/* Header */}
        <div className="flex items-start gap-3 mb-4">
          <div>
            <h3 className="text-xl font-bold text-gray-900">
              Entering Practice Mode
            </h3>
            <p className="text-sm text-gray-600 mt-1">
              You are about to leave curated learning content
            </p>
          </div>
        </div>

        {/* Content */}
        <div className="space-y-3 mb-6 text-sm text-gray-700">
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-3">
            <div className="font-semibold text-blue-900 mb-1">
              What you've been learning:
            </div>
            <div className="text-blue-800">
              The learning modules (Theory, Examples, Figures, Activities) are
              <strong> carefully curated by instructors</strong> to ensure accuracy
              and pedagogical quality.
            </div>
          </div>

          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3">
            <div className="font-semibold text-yellow-900 mb-1">
              Practice exercises are LLM-generated:
            </div>
            <div className="text-yellow-800">
              Practice questions are <strong>automatically generated using AI</strong> (Claude).
              While designed to test your understanding, they may occasionally contain errors
              or inaccuracies.
            </div>
          </div>

          <div className="bg-green-50 border border-green-200 rounded-lg p-3">
            <div className="font-semibold text-green-900 mb-1">
              You can help improve quality:
            </div>
            <div className="text-green-800">
              If you encounter a question that seems incorrect or unclear, you can
              <strong> flag it for review</strong>. Flagged questions are hidden from
              future practice sessions.
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex gap-3">
          <button
            onClick={onCancel}
            className="flex-1 px-4 py-3 border border-gray-300 rounded-lg hover:bg-gray-50 font-medium transition-colors"
          >
            Go Back
          </button>
          <button
            onClick={onContinue}
            className="flex-1 px-4 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-medium transition-colors"
          >
            Continue to Practice
          </button>
        </div>

        {/* Footer Note */}
        <div className="mt-4 pt-4 border-t border-gray-200">
          <div className="text-xs text-gray-500 text-center">
            This warning will only appear once per session
          </div>
        </div>
      </div>
    </div>
  );
}
