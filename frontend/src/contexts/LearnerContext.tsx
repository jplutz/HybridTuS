/**
 * Learner Context Provider
 *
 * Global state management for selected learner identity.
 * Eliminates hardcoded learnerId by providing centralized learner selection.
 *
 * Features:
 * - Persists selection to localStorage
 * - Provides useLearner() hook for all components
 * - Supports guest mode (no learner selected)
 */

import React, { createContext, useContext, useState, ReactNode, useEffect } from 'react';
import type { LearnerDto } from '@/types/api';

interface LearnerContextType {
  learner: LearnerDto | null;
  setLearner: (learner: LearnerDto | null) => void;
  isGuest: boolean;
}

const LearnerContext = createContext<LearnerContextType | undefined>(undefined);

export function LearnerProvider({ children }: { children: ReactNode }) {
  const [learner, setLearnerState] = useState<LearnerDto | null>(() => {
    // Always start in guest mode (null)
    // This prevents issues when data is cleared but localStorage still has a deleted learner
    // User must explicitly select a learner from the dropdown each session
    console.log('[LearnerContext] Starting in guest mode');
    return null;
  });

  const setLearner = (newLearner: LearnerDto | null) => {
    setLearnerState(newLearner);

    // Note: We don't persist to localStorage anymore
    // This ensures the app always starts in guest mode on page load
    if (newLearner) {
      console.log('[LearnerContext] Learner selected:', newLearner.displayName);
    } else {
      console.log('[LearnerContext] Learner cleared (guest mode)');
    }
  };

  const isGuest = !learner;

  return (
    <LearnerContext.Provider value={{ learner, setLearner, isGuest }}>
      {children}
    </LearnerContext.Provider>
  );
}

/**
 * Hook to access learner context.
 * Throws error if used outside LearnerProvider.
 */
export function useLearner() {
  const context = useContext(LearnerContext);
  if (!context) {
    throw new Error('useLearner must be used within LearnerProvider');
  }
  return context;
}
