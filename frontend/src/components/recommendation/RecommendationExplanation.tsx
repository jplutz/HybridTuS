/**
 * Recommendation Explanation Panel
 *
 * Displays why a content TYPE is recommended based on:
 * - Mined sequence patterns from similar learners
 * - FSLSM learning style preferences
 * - Successful learning paths
 *
 * Features:
 * - Collapsible panel
 * - TYPE-focused recommendations (not specific modules)
 * - FSLSM profile explanation
 */

import React, { useState } from 'react';
import type { LearnerDto, ModuleDto } from '@/types/api';

interface RecommendationExplanationProps {
  conceptId: number;
  learner: LearnerDto;
  recommendedModule: ModuleDto | null;
}

export default function RecommendationExplanation({
  conceptId,
  learner,
  recommendedModule,
}: RecommendationExplanationProps) {
  const [expanded, setExpanded] = useState(false);

  if (!recommendedModule) {
    return null;
  }

  // Determine FSLSM cluster
  const getFSLSMCluster = () => {
    const dim1 = learner.styleActiveReflective > 0 ? "Active" : "Reflective";
    const dim2 = learner.styleSensingIntuitive > 0 ? "Sensing" : "Intuitive";
    const dim3 = learner.styleVisualVerbal > 0 ? "Visual" : "Verbal";
    const dim4 = learner.styleSequentialGlobal > 0 ? "Sequential" : "Global";
    return `${dim1}, ${dim2}, ${dim3}, ${dim4}`;
  };

  // Get content type explanation
  const getTypeExplanation = (type: string) => {
    switch (type) {
      case 'T':
        return 'Theory content provides foundational explanations and conceptual frameworks, which helps build a solid understanding.';
      case 'E':
        return 'Example content demonstrates practical applications, making abstract concepts concrete through real-world scenarios.';
      case 'A':
        return 'Activity content engages you with hands-on exercises, reinforcing learning through active practice.';
      case 'F':
        return 'Figure content uses visual representations like diagrams and charts, which aids spatial understanding.';
      case 'Test':
        return 'Test content checks your knowledge and helps identify areas needing more attention.';
      default:
        return 'This content type supports your learning journey.';
    }
  };

  // Get FSLSM-based reasoning
  const getFSLSMReasoning = (type: string) => {
    const reasons = [];

    if (type === 'E' && learner.styleActiveReflective > 0) {
      reasons.push('Active learners benefit from concrete examples and demonstrations');
    }
    if (type === 'A' && learner.styleActiveReflective > 0) {
      reasons.push('Active learners learn best through hands-on practice');
    }
    if (type === 'F' && learner.styleVisualVerbal > 0) {
      reasons.push('Visual learners process information better through diagrams and visual aids');
    }
    if (type === 'T' && learner.styleSensingIntuitive < 0) {
      reasons.push('Intuitive learners appreciate theoretical frameworks and abstract concepts');
    }
    if (type === 'E' && learner.styleSensingIntuitive > 0) {
      reasons.push('Sensing learners prefer concrete facts and practical examples');
    }

    if (reasons.length === 0) {
      return 'Learners with similar profiles have preferred this content type at this stage of their learning progress';
    }

    return reasons.join('; ');
  };

  const fslsmCluster = getFSLSMCluster();
  const typeExplanation = getTypeExplanation(recommendedModule.type);
  const fslsmReasoning = getFSLSMReasoning(recommendedModule.type);

  const contentTypeLabel = {
    'T': 'Theory',
    'E': 'Example',
    'A': 'Activity',
    'F': 'Figure/Visual',
    'Test': 'Assessment'
  }[recommendedModule.type] || recommendedModule.type;

  return (
    <div className="border border-blue-200 rounded-lg overflow-hidden bg-blue-50 mb-4 min-w-full">
      {/* Header - Always Visible with TYPE focus */}
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-3 px-4 py-3 hover:bg-blue-100 transition-colors text-left min-w-0"
      >
        <div className="flex-1">
          <span className="font-semibold text-blue-900">
            We recommend {contentTypeLabel} content for you
          </span>
          <p className="text-xs text-blue-700 mt-0.5">
            Based on successful patterns from learners with similar learning styles · Click to learn more
          </p>
        </div>
        <svg
          className={`w-5 h-5 text-blue-600 transition-transform ${expanded ? 'rotate-180' : ''}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {/* Expanded Content */}
      {expanded && (
        <div className="border-t border-blue-200 px-4 py-4 bg-white space-y-4">
          {/* Content Type Explanation */}
          <div>
            <h4 className="font-semibold text-sm text-gray-900 mb-2">
              Why {contentTypeLabel} content?
            </h4>
            <p className="text-sm text-gray-700 mb-3">
              {typeExplanation}
            </p>
          </div>

          {/* FSLSM Profile Match */}
          <div className="border-t border-gray-200 pt-4">
            <h4 className="font-semibold text-sm text-gray-900 mb-2">
              Your Learning Style: {fslsmCluster}
            </h4>
            <p className="text-sm text-gray-700">
              {fslsmReasoning}
            </p>
          </div>

          {/* Flexibility Note */}
          <div className="text-xs text-gray-500 bg-gray-50 p-3 rounded border border-gray-200 mt-4">
            <strong>Note:</strong> This is a recommendation, not a requirement. You can explore any content
            in any order. The system adapts to your choices and continues learning from your progress.
          </div>
        </div>
      )}
    </div>
  );
}
