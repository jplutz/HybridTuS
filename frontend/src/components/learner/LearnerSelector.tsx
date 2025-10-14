/**
 * Learner Selector Component
 *
 * Dropdown selector for choosing learner identity.
 * Displays learner name, ID, and FSLSM cluster information.
 *
 * Features:
 * - Fetches all learners from API
 * - Shows FSLSM cluster badges with color coding
 * - Supports guest mode (no learner selected)
 * - Persists selection via LearnerContext
 */

import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '@/adapters';
import { useLearner } from '@/contexts/LearnerContext';
import type { LearnerDto } from '@/types/api';

export default function LearnerSelector() {
  const { learner, setLearner } = useLearner();
  const [learners, setLearners] = useState<LearnerDto[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [syntheticExpanded, setSyntheticExpanded] = useState(false);

  // Fetch all learners on mount
  useEffect(() => {
    (async () => {
      try {
        setLoading(true);
        const allLearners = await api.getLearners();
        setLearners(allLearners);
        console.log('[LearnerSelector] Loaded', allLearners.length, 'learners');
      } catch (err) {
        console.error('[LearnerSelector] Failed to load learners:', err);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleSelect = (selected: LearnerDto | null) => {
    setLearner(selected);
    setOpen(false);
  };

  // Close dropdown when clicking outside
  useEffect(() => {
    if (!open) return;

    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (!target.closest('.learner-selector')) {
        setOpen(false);
      }
    };

    document.addEventListener('click', handleClickOutside);
    return () => document.removeEventListener('click', handleClickOutside);
  }, [open]);

  return (
    <div className="learner-selector relative">
      {/* Selector Button */}
      <button
        onClick={() => setOpen(!open)}
        disabled={loading}
        className="flex items-center gap-2 px-3 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed min-w-[200px]"
      >
        {loading ? (
          <span className="text-sm text-gray-500">Loading...</span>
        ) : learner ? (
          <>
            <span className="text-sm font-medium truncate">{learner.displayName}</span>
            <FslsmBadge learner={learner} />
          </>
        ) : (
          <span className="text-sm text-gray-500">Select Learner</span>
        )}
        <svg
          className={`ml-auto w-4 h-4 transition-transform ${open ? 'rotate-180' : ''}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {/* Dropdown Menu */}
      {open && !loading && (() => {
        // Separate synthetic and real learners
        const syntheticLearners = learners.filter(l => l.externalRef?.startsWith('synth_'));
        const realLearners = learners.filter(l => !l.externalRef?.startsWith('synth_'));

        return (
          <div className="absolute right-0 mt-2 w-80 bg-white border border-gray-200 rounded-lg shadow-lg z-50 max-h-96 overflow-y-auto">
            {/* Real Learners */}
            {realLearners.length > 0 && (
              <div>
                <div className="px-4 py-2 bg-gray-50 text-xs font-semibold text-gray-600 border-b border-gray-200">
                  Real Learners
                </div>
                {realLearners.map((l) => (
                  <button
                    key={l.id}
                    onClick={() => handleSelect(l)}
                    className={[
                      'w-full text-left px-4 py-3 hover:bg-gray-50 flex items-center gap-3 border-b border-gray-100 transition-colors',
                      learner?.id === l.id ? 'bg-blue-50' : '',
                    ].join(' ')}
                  >
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{l.displayName}</span>
                        <span className="text-xs text-gray-500">#{l.id}</span>
                      </div>
                      <div className="text-xs text-gray-600 mt-0.5">{l.externalRef}</div>
                    </div>
                    <FslsmBadge learner={l} />
                  </button>
                ))}
              </div>
            )}

            {/* Synthetic Learners - Collapsible */}
            {syntheticLearners.length > 0 && (
              <div className="border-t-2 border-gray-300">
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setSyntheticExpanded(!syntheticExpanded);
                  }}
                  className="w-full text-left px-4 py-3 bg-gray-50 hover:bg-gray-100 flex items-center justify-between transition-colors"
                >
                  <div>
                    <div className="text-xs font-semibold text-gray-600">Synthetic Learners</div>
                    <div className="text-xs text-gray-500 mt-0.5">
                      {syntheticLearners.length} learners across 16 FSLSM clusters
                    </div>
                  </div>
                  <svg
                    className={`w-4 h-4 text-gray-500 transition-transform ${syntheticExpanded ? 'rotate-180' : ''}`}
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>

                {syntheticExpanded && (
                  <div className="max-h-60 overflow-y-auto">
                    {syntheticLearners.map((l) => (
                      <button
                        key={l.id}
                        onClick={() => handleSelect(l)}
                        className={[
                          'w-full text-left px-4 py-3 hover:bg-gray-50 flex items-center gap-3 border-b border-gray-100 transition-colors',
                          learner?.id === l.id ? 'bg-blue-50' : '',
                        ].join(' ')}
                      >
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-medium text-sm">{l.displayName}</span>
                            <span className="text-xs text-gray-500">#{l.id}</span>
                          </div>
                        </div>
                        <FslsmBadge learner={l} />
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Guest Mode Option */}
            <button
              onClick={() => handleSelect(null)}
              className="w-full text-left px-4 py-3 hover:bg-gray-50 text-gray-600 border-t-2 border-gray-300 transition-colors"
            >
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium">Guest Mode</span>
                <span className="text-xs text-gray-500">(Browse only)</span>
              </div>
            </button>

            {/* Create New Learner Link */}
            <Link
              to="/learners/new"
              className="block w-full text-left px-4 py-3 hover:bg-gray-50 text-blue-600 font-medium border-t border-gray-200 transition-colors"
              onClick={() => setOpen(false)}
            >
              + Create New Learner
            </Link>
          </div>
        );
      })()}
    </div>
  );
}

/**
 * FSLSM Badge Component
 * Displays learner's FSLSM cluster with color coding
 */
function FslsmBadge({ learner }: { learner: LearnerDto }) {
  // Compute FSLSM cluster key
  const d1 = learner.styleActiveReflective >= 0 ? 'ACT' : 'REF';
  const d2 = learner.styleSensingIntuitive >= 0 ? 'SEN' : 'INT';
  const d3 = learner.styleVisualVerbal >= 0 ? 'VIS' : 'VRB';
  const d4 = learner.styleSequentialGlobal >= 0 ? 'SEQ' : 'GLO';

  const clusterKey = `${d1}_${d2}_${d3}_${d4}`;

  // Color coding based on dominant dimension
  const bgColor =
    Math.abs(learner.styleActiveReflective) > 5
      ? 'bg-red-100 text-red-700'
      : Math.abs(learner.styleSensingIntuitive) > 5
      ? 'bg-green-100 text-green-700'
      : Math.abs(learner.styleVisualVerbal) > 5
      ? 'bg-blue-100 text-blue-700'
      : Math.abs(learner.styleSequentialGlobal) > 5
      ? 'bg-purple-100 text-purple-700'
      : 'bg-gray-100 text-gray-700';

  return (
    <span
      className={`px-2 py-1 rounded text-xs font-mono whitespace-nowrap ${bgColor}`}
      title={`Active/Reflective: ${learner.styleActiveReflective}, Sensing/Intuitive: ${learner.styleSensingIntuitive}, Visual/Verbal: ${learner.styleVisualVerbal}, Sequential/Global: ${learner.styleSequentialGlobal}`}
    >
      {clusterKey}
    </span>
  );
}
