/**
 * Concept Learning Session Page
 * Displays modular learning content where user can navigate freely
 * System recommends next module based on sequence mining
 */
import React, { useEffect, useState, useMemo, useRef } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { api } from "@/adapters";
import { useLearner } from "@/contexts/LearnerContext";
import RecommendationExplanation from "@/components/recommendation/RecommendationExplanation";
import LLMContentWarning from "@/components/practice/LLMContentWarning";
import EnhancedMarkdown from "@/components/content/EnhancedMarkdown";
import type { SessionDto, ModuleDto } from "@/types/api";
import { useSessionStore } from "@/state/store";

export default function ConceptSession() {
  const { courseId, conceptId } = useParams<"courseId" | "conceptId">();
  const { learner, isGuest } = useLearner();
  const navigate = useNavigate();
  const [session, setSession] = useState<SessionDto | null>(null);
  const [selectedModule, setSelectedModule] = useState<ModuleDto | null>(null);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showWarning, setShowWarning] = useState(false);
  const logEvent = useSessionStore((s) => s.logEvent);

  // Helper function to log recommendation trace from session data
  const logRecommendationTrace = (trace: string[]) => {
    if (!trace || trace.length === 0) {
      return;
    }

    // Log each trace line directly (backend formats the trace)
    trace.forEach((line) => {
      // Parse backend trace lines to extract appropriate log level
      let level: "info" | "debug" | "warn" | "error" = "debug";

      // Key lines that should be highlighted as info
      if (line.includes("[RECOMMENDATION]") ||
          line.includes("[ACTUAL RECOMMENDATION]") ||
          line.includes("[SUCCESS]") ||
          line.includes("Available LO types:") ||
          line.includes("Content source:") ||
          line.includes("[SELECTED]") ||
          line.includes("[FALLBACK]")) {
        level = "info";
      } else if (line.includes("[WARNING]") || line.includes("[ERROR]")) {
        level = "warn";
      }

      logEvent({
        ts: Date.now(),
        level,
        origin: "RECOMMENDER",
        code: "TRACE",
        message: line
      });
    });
  };

  // Load session on mount
  useEffect(() => {
    if (!conceptId) return;
    const id = parseInt(conceptId, 10);
    if (isNaN(id)) return;

    // Require learner to be selected
    if (isGuest) {
      setLoading(false);
      return;
    }

    if (!learner) {
      setLoading(false);
      return;
    }

    (async () => {
      try {
        setLoading(true);
        const sessionData = await api.getOrCreateSession(learner.id, id);
        setSession(sessionData);

        // Log recommendation trace
        logRecommendationTrace(sessionData.recommendationTrace);

        // Auto-expand group containing recommended module
        if (sessionData.recommendedModuleId) {
          const recommended = sessionData.modules.find(m => m.id === sessionData.recommendedModuleId);
          if (recommended) {
            setExpandedGroups(prev => new Set([...prev, recommended.type]));
            setSelectedModule(recommended);
          }
        } else {
          const firstUnvisited = sessionData.modules.find(
            m => !sessionData.visitedModuleIds.includes(m.id)
          );
          const toSelect = firstUnvisited || sessionData.modules[0];
          if (toSelect) {
            setExpandedGroups(prev => new Set([...prev, toSelect.type]));
            setSelectedModule(toSelect);
          }
        }
      } catch (err) {
        console.error("[ConceptSession] Failed to load session:", err);
        setError(err instanceof Error ? err.message : "Failed to load session");
      } finally {
        setLoading(false);
      }
    })();
  }, [conceptId, learner, isGuest]);

  // Group modules by type
  const groupedModules = useMemo(() => {
    if (!session || !session.modules || !Array.isArray(session.modules)) return {};

    const groups: Record<string, { label: string; icon: string; modules: ModuleDto[] }> = {
      T: { label: 'Theory', icon: '[T]', modules: [] },
      E: { label: 'Examples', icon: '[E]', modules: [] },
      F: { label: 'Figures', icon: '[F]', modules: [] },
      A: { label: 'Activities', icon: '[A]', modules: [] },
      Test: { label: 'Tests', icon: '[Test]', modules: [] },
    };

    session.modules.forEach(module => {
      if (!module || !module.type) return; // Skip invalid modules
      const type = module.type;
      if (groups[type]) {
        groups[type].modules.push(module);
      }
    });

    return groups;
  }, [session]);

  // Check if all content modules are completed (excluding Test)
  const allContentCompleted = useMemo(() => {
    if (!session || !session.modules || !session.doneModuleIds) return false;

    const contentModules = session.modules.filter(m => m.type !== 'Test');
    if (contentModules.length === 0) return false;

    return contentModules.every(m => session.doneModuleIds.includes(m.id));
  }, [session]);

  // Toggle group expansion
  const toggleGroup = (type: string) => {
    setExpandedGroups(prev => {
      const newSet = new Set(prev);
      if (newSet.has(type)) {
        newSet.delete(type);
      } else {
        newSet.add(type);
      }
      return newSet;
    });
  };

  // Record module visit when selected
  const handleModuleSelect = async (module: ModuleDto) => {
    if (!session || !session.visitedModuleIds) return;

    setSelectedModule(module);

    // Record visit if not already visited
    if (!session.visitedModuleIds.includes(module.id)) {
      try {
        await api.recordModuleVisit(session.sessionId, module.id);
        // Update local state
        setSession(prev => prev && prev.visitedModuleIds ? {
          ...prev,
          visitedModuleIds: [...prev.visitedModuleIds, module.id]
        } : prev);
      } catch (err) {
        console.error("[ConceptSession] Failed to record visit:", err);
      }
    }
  };

  // Mark module as done and refresh recommendation
  const handleMarkAsDone = async (moduleId: number) => {
    if (!session || !learner || !conceptId || !session.doneModuleIds || !session.visitedModuleIds) return;

    try {
      // Optimistically update UI immediately - add to done list
      setSession(prev => {
        if (!prev || !prev.doneModuleIds || !prev.visitedModuleIds) return prev;

        // Avoid duplicates
        const updatedDoneIds = prev.doneModuleIds.includes(moduleId)
          ? prev.doneModuleIds
          : [...prev.doneModuleIds, moduleId];

        const updatedVisitedIds = prev.visitedModuleIds.includes(moduleId)
          ? prev.visitedModuleIds
          : [...prev.visitedModuleIds, moduleId];

        return {
          ...prev,
          doneModuleIds: updatedDoneIds,
          visitedModuleIds: updatedVisitedIds
        };
      });

      // Mark as done on backend
      await api.markModuleAsDone(session.sessionId, moduleId);

      // Fetch updated session with new recommendation
      const updatedSession = await api.getOrCreateSession(learner.id, parseInt(conceptId, 10));

      // Log recommendation trace which includes the new recommendation details
      logRecommendationTrace(updatedSession.recommendationTrace);

      // Update state with new session data (including new recommendations)
      setSession(updatedSession);

      // Update selected module to reflect the new data (keeps same module but with updated state)
      if (selectedModule && selectedModule.id === moduleId) {
        const updatedModule = updatedSession.modules.find(m => m.id === moduleId);
        if (updatedModule) {
          setSelectedModule(updatedModule);
        }
      }

      // Auto-expand and highlight new recommended module if available
      if (updatedSession.recommendedModuleId) {
        const newRecommended = updatedSession.modules.find(m => m.id === updatedSession.recommendedModuleId);
        if (newRecommended) {
          // Expand group containing recommended module
          setExpandedGroups(prev => new Set([...prev, newRecommended.type]));
        }
      }
    } catch (err) {
      console.error("[ConceptSession] Failed to mark as done:", err);

      // Log error to visible console
      logEvent({
        ts: Date.now(),
        level: "error",
        origin: "DB",
        code: "ERROR",
        message: `Failed to mark module as done: ${err instanceof Error ? err.message : String(err)}`
      });

      // Revert optimistic update on error by fetching fresh data
      try {
        const updatedSession = await api.getOrCreateSession(learner.id, parseInt(conceptId, 10));
        setSession(updatedSession);
      } catch (revertErr) {
        console.error("[ConceptSession] Failed to revert optimistic update:", revertErr);
      }
      throw err;
    }
  };

  // Mark module as revisited and refresh recommendation
  const handleMarkAsRevisited = async (moduleId: number) => {
    if (!session || !learner || !conceptId || !session.revisitedModuleIds) return;

    try {
      // Optimistically update UI - add to revisited list
      setSession(prev => {
        if (!prev || !prev.revisitedModuleIds) return prev;

        return {
          ...prev,
          revisitedModuleIds: [...prev.revisitedModuleIds, moduleId]
        };
      });

      // Mark as revisited on backend
      await api.markModuleAsRevisited(session.sessionId, moduleId);

      // Fetch updated session with new recommendation
      const updatedSession = await api.getOrCreateSession(learner.id, parseInt(conceptId, 10));

      // Log recommendation trace which includes the new recommendation details
      logRecommendationTrace(updatedSession.recommendationTrace);

      // Update state with new session data (including new recommendations)
      setSession(updatedSession);

      // Update selected module to reflect the new data
      if (selectedModule && selectedModule.id === moduleId) {
        const updatedModule = updatedSession.modules.find(m => m.id === moduleId);
        if (updatedModule) {
          setSelectedModule(updatedModule);
        }
      }

      // Auto-expand and highlight new recommended module if available
      if (updatedSession.recommendedModuleId) {
        const newRecommended = updatedSession.modules.find(m => m.id === updatedSession.recommendedModuleId);
        if (newRecommended) {
          setExpandedGroups(prev => new Set([...prev, newRecommended.type]));
        }
      }
    } catch (err) {
      console.error("[ConceptSession] Failed to mark as revisited:", err);

      // Log error to visible console
      logEvent({
        ts: Date.now(),
        level: "error",
        origin: "DB",
        code: "ERROR",
        message: `Failed to mark module as revisited: ${err instanceof Error ? err.message : String(err)}`
      });

      // Revert optimistic update on error
      try {
        const updatedSession = await api.getOrCreateSession(learner.id, parseInt(conceptId, 10));
        setSession(updatedSession);
      } catch (revertErr) {
        console.error("[ConceptSession] Failed to revert optimistic update:", revertErr);
      }
      throw err;
    }
  };

  // Guest mode check
  if (isGuest) {
    return (
      <div className="p-6">
        <Breadcrumb courseId={courseId} conceptId={conceptId} />
        <div className="border rounded-lg p-8 text-center bg-yellow-50 border-yellow-200 mt-6">
          <h2 className="text-xl font-semibold mb-2">Learner Selection Required</h2>
          <p className="text-gray-600 mb-4">
            Please select a learner from the dropdown in the header to start a learning session.
          </p>
          <button
            onClick={() => navigate('/courses')}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            Back to Courses
          </button>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="p-6">
        <Breadcrumb courseId={courseId} conceptId={conceptId} />
        <h1 className="text-xl font-semibold">Loading session…</h1>
      </div>
    );
  }

  if (error || !session) {
    return (
      <div className="p-6">
        <Breadcrumb courseId={courseId} conceptId={conceptId} />
        <h1 className="text-xl font-semibold text-red-600">
          {error || "Session not found"}
        </h1>
      </div>
    );
  }

  // Additional safety check for session data integrity
  if (!session.modules || !session.doneModuleIds || !session.visitedModuleIds) {
    return (
      <div className="p-6">
        <Breadcrumb courseId={courseId} conceptId={conceptId} />
        <h1 className="text-xl font-semibold text-red-600">
          Session data is incomplete or corrupted
        </h1>
      </div>
    );
  }

  return (
    <>
      {/* Main Content with padding for fixed chat */}
      <div className="p-6 space-y-6 pb-24">
        <Breadcrumb courseId={courseId} conceptId={conceptId} conceptName={session.conceptName} />

        <header className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold">{session.conceptName}</h1>
            <div className="text-sm text-gray-500">
              {session.doneModuleIds.filter(id => {
                const module = session.modules.find(m => m.id === id);
                return module && module.type !== 'Test';
              }).length} of {session.modules.filter(m => m.type !== 'Test').length} content modules completed
            </div>
          </div>
          <ProgressIndicator
            total={session.modules.filter(m => m.type !== 'Test').length}
            completed={session.doneModuleIds.filter(id => {
              const module = session.modules.find(m => m.id === id);
              return module && module.type !== 'Test';
            }).length}
          />
        </header>

        <div className="grid lg:grid-cols-[320px_1fr] gap-6">
        {/* Module Menu Sidebar - Grouped by Type */}
        <aside className="space-y-3">
          <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">
            Learning Modules
          </h2>

          {/* Render module groups */}
          {Object.entries(groupedModules).map(([type, group]) => {
            if (group.modules.length === 0) return null;

            const doneCount = group.modules.filter(m =>
              session.doneModuleIds && session.doneModuleIds.includes(m.id)
            ).length;

            const isExpanded = expandedGroups.has(type);

            // Check if any module in this group is recommended
            const hasRecommended = group.modules.some(m => m.id === session.recommendedModuleId);

            return (
              <div key={type} className="border border-gray-200 rounded-lg overflow-hidden">
                {/* Group Header */}
                <button
                  onClick={() => toggleGroup(type)}
                  className="w-full flex items-center justify-between p-3 hover:bg-gray-50 transition-colors bg-white"
                >
                  <div className="flex items-center gap-2">
                    <span className="text-lg">{group.icon}</span>
                    <span className="font-medium text-sm">{group.label}</span>
                    {hasRecommended && (
                      <span className="text-xs bg-amber-500 text-white px-2 py-0.5 rounded-full font-medium">
                        Recommended
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-gray-600">
                      {doneCount}/{group.modules.length}
                    </span>
                    <svg
                      className={`w-4 h-4 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                    </svg>
                  </div>
                </button>

                {/* Group Modules */}
                {isExpanded && (
                  <div className="border-t border-gray-200 p-2 space-y-2 bg-gray-50">
                    {group.modules.map(module => (
                      <ModuleCard
                        key={module.id}
                        module={module}
                        selected={selectedModule?.id === module.id}
                        done={session.doneModuleIds ? session.doneModuleIds.includes(module.id) : false}
                        recommended={session.recommendedModuleId === module.id}
                        onClick={() => handleModuleSelect(module)}
                      />
                    ))}
                  </div>
                )}
              </div>
            );
          })}

          {courseId && conceptId && (
            <button
              onClick={() => setShowWarning(true)}
              className="w-full mt-4 px-4 py-3 rounded-lg border-2 border-green-500 bg-green-50 text-green-700 font-medium hover:bg-green-100 transition-colors"
            >
              Start Practice
            </button>
          )}
        </aside>

        {/* Content Area */}
        <main>
          {/* Completion Notification */}
          {allContentCompleted && (
            <CompletionNotification
              courseId={courseId}
              conceptId={conceptId}
              onPractice={() => setShowWarning(true)}
            />
          )}

          {/* Recommendation Explanation */}
          {learner && session?.recommendedModuleId && session?.modules && conceptId && !isNaN(Number(conceptId)) && !allContentCompleted && (
            <RecommendationExplanation
              conceptId={Number(conceptId)}
              learner={learner}
              recommendedModule={session.modules.find(m => m.id === session.recommendedModuleId) || null}
            />
          )}

          {selectedModule ? (
            <ModuleContent
              module={selectedModule}
              done={session.doneModuleIds ? session.doneModuleIds.includes(selectedModule.id) : false}
              onMarkAsDone={handleMarkAsDone}
              onMarkAsRevisited={handleMarkAsRevisited}
            />
          ) : (
            <div className="border rounded-lg p-8 text-center text-gray-500">
              Select a module from the menu to begin
            </div>
          )}
        </main>
      </div>
    </div>

    {/* Fixed Chat Bar */}
    <div className="fixed bottom-0 left-0 right-0 z-50">
      <ChatBar conceptId={conceptId} currentContent={selectedModule?.content} />
    </div>

    {/* LLM Content Warning Modal */}
    <LLMContentWarning
      isOpen={showWarning}
      onCancel={() => setShowWarning(false)}
      onContinue={() => {
        setShowWarning(false);
        if (courseId && conceptId) {
          navigate(`/courses/${courseId}/concepts/${conceptId}/practice`);
        }
      }}
    />
  </>
);
}

/* ========== COMPONENTS ========== */

function Breadcrumb({
  courseId,
  conceptId,
  conceptName
}: {
  courseId?: string;
  conceptId?: string;
  conceptName?: string;
}) {
  return (
    <nav className="text-sm text-gray-600">
      <Link className="hover:underline" to="/courses">
        Courses
      </Link>
      <span className="mx-2">/</span>
      {courseId && (
        <>
          <Link className="hover:underline" to={`/courses/${courseId}`}>
            Course {courseId}
          </Link>
          <span className="mx-2">/</span>
        </>
      )}
      <span className="font-medium">{conceptName || `Concept ${conceptId}`}</span>
    </nav>
  );
}

function CompletionNotification({
  courseId,
  conceptId,
  onPractice
}: {
  courseId?: string;
  conceptId?: string;
  onPractice: () => void;
}) {
  return (
    <div className="mb-6 rounded-lg border-2 border-green-400 bg-gradient-to-r from-green-50 to-emerald-50 p-6 shadow-md">
      <div className="flex items-start gap-4">
        {/* Success Icon */}
        <div className="flex-shrink-0">
          <div className="rounded-full bg-green-500 p-2">
            <svg className="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
            </svg>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1">
          <h3 className="text-lg font-bold text-green-900 mb-2">
            🎉 All Content Completed!
          </h3>
          <p className="text-green-800 mb-4">
            Great job! You've completed all learning materials for this concept.
            You can now:
          </p>

          <div className="flex flex-col sm:flex-row gap-3">
            <button
              onClick={onPractice}
              className="px-6 py-3 rounded-lg bg-green-600 text-white font-semibold hover:bg-green-700 transition-colors shadow-md flex items-center justify-center gap-2"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
              </svg>
              Continue to Practice
            </button>

            <div className="px-6 py-3 rounded-lg bg-white border-2 border-green-300 text-green-800 font-medium flex items-center justify-center gap-2">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              Or revisit any module for deeper understanding
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function ModuleCard({
  module,
  selected,
  done,
  recommended,
  onClick,
}: {
  module: ModuleDto;
  selected: boolean;
  done: boolean;
  recommended: boolean;
  onClick: () => void;
}) {
  // Type-specific colors
  const typeColors: Record<string, string> = {
    T: 'bg-blue-100 text-blue-800',
    E: 'bg-purple-100 text-purple-800',
    F: 'bg-green-100 text-green-800',
    A: 'bg-orange-100 text-orange-800',
    Test: 'bg-red-100 text-red-800',
  };

  const colorClass = typeColors[module.type] || 'bg-gray-200 text-gray-800';

  return (
    <button
      onClick={onClick}
      className={[
        "w-full rounded-lg border-2 transition-all text-left p-3",
        selected
          ? "border-blue-500 bg-blue-50 shadow-md"
          : done
          ? "border-green-300 bg-green-50/50"
          : recommended
          ? "border-amber-400 bg-amber-50/30"
          : "border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm",
      ].join(" ")}
    >
      <div className="flex items-start gap-2">
        <span className={`text-xs font-bold px-2 py-1 rounded ${colorClass}`}>
          {module.type}
        </span>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-medium text-sm">{module.title}</span>
            {done && (
              <svg className="w-4 h-4 text-green-600 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
              </svg>
            )}
          </div>
          <div className="text-xs text-gray-500 mt-0.5">
            ~{module.estimatedMinutes} min
          </div>
        </div>
      </div>
    </button>
  );
}

function ModuleContent({
  module,
  done,
  onMarkAsDone,
  onMarkAsRevisited
}: {
  module: ModuleDto;
  done: boolean;
  onMarkAsDone?: (moduleId: number) => Promise<void>;
  onMarkAsRevisited?: (moduleId: number) => Promise<void>;
}) {
  const [marking, setMarking] = useState(false);
  const [revisiting, setRevisiting] = useState(false);
  const [activityThoughts, setActivityThoughts] = useState('');
  const chatBarRef = useRef<{ discussActivity: (thoughts: string) => void }>(null);

  const handleMarkAsDone = async () => {
    if (!onMarkAsDone || done || marking) return;

    setMarking(true);
    try {
      await onMarkAsDone(module.id);
    } catch (err) {
      console.error('[ModuleContent] Failed to mark as done:', err);
    } finally {
      setMarking(false);
    }
  };

  const handleMarkAsRevisited = async () => {
    if (!onMarkAsRevisited || !done || revisiting) return;

    setRevisiting(true);
    try {
      await onMarkAsRevisited(module.id);
    } catch (err) {
      console.error('[ModuleContent] Failed to mark as revisited:', err);
    } finally {
      setRevisiting(false);
    }
  };

  const handleDiscussWithAI = () => {
    // Pass thoughts to ChatBar component through window event
    window.dispatchEvent(new CustomEvent('openChatWithContext', {
      detail: {
        activityTitle: module.title,
        activityContent: module.content,
        userThoughts: activityThoughts
      }
    }));
  };

  return (
    <div className="border rounded-lg p-8 bg-white shadow-sm min-h-[600px] relative pb-24">
      <div className="text-xs uppercase tracking-wide text-gray-500 mb-2">
        {module.type}
      </div>
      <h2 className="text-3xl font-bold mb-4">{module.title}</h2>
      <div className="text-sm text-gray-600 mb-4">
        Estimated time: {module.estimatedMinutes} minutes
      </div>

      {/* AI-Generated Content Notice */}
      <div className="mb-6 p-4 bg-amber-50 border border-amber-200 rounded-lg">
        <div className="flex items-start gap-3">
          <svg className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
          </svg>
          <div className="flex-1">
            <div className="font-semibold text-amber-900 text-sm mb-1">AI-Generated Placeholder Content</div>
            <p className="text-xs text-amber-800">
              This learning object content is AI-generated and serves as a placeholder for didactically appropriate curated content that should be developed by instructional designers and subject matter experts.
            </p>
          </div>
        </div>
      </div>

      <EnhancedMarkdown content={module.content} />

      {/* Discussion Box for Activities */}
      {module.type === 'A' && (
        <div className="mt-12 border-t-2 border-gray-200 pt-8">
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-6">
            <h3 className="text-lg font-semibold text-blue-900 mb-2">Reflect on Your Work</h3>
            <p className="text-sm text-blue-700 mb-4">
              Share your thoughts, solutions, or questions about this activity. Discuss your work with the AI assistant for feedback and guidance.
            </p>

            <textarea
              value={activityThoughts}
              onChange={(e) => setActivityThoughts(e.target.value)}
              placeholder="Write your thoughts, solutions, or questions here..."
              className="w-full border border-blue-300 rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500 min-h-[120px] resize-y"
            />

            <button
              onClick={handleDiscussWithAI}
              disabled={!activityThoughts.trim()}
              className="mt-3 px-6 py-3 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
              </svg>
              Discuss with AI Assistant
            </button>
          </div>
        </div>
      )}

      {/* Discussion Box for Self-Tests */}
      {module.type === 'Test' && (
        <div className="mt-12 border-t-2 border-gray-200 pt-8">
          <div className="bg-red-50 border border-red-200 rounded-lg p-6">
            <h3 className="text-lg font-semibold text-red-900 mb-2">Self-Assessment Reflection</h3>
            <p className="text-sm text-red-700 mb-4">
              After completing this self-test, reflect on your performance. Share your answers, questions, or areas where you need clarification with the AI assistant.
            </p>

            <textarea
              value={activityThoughts}
              onChange={(e) => setActivityThoughts(e.target.value)}
              placeholder="Share your test responses, questions, or areas of confusion..."
              className="w-full border border-red-300 rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-red-500 min-h-[120px] resize-y"
            />

            <button
              onClick={handleDiscussWithAI}
              disabled={!activityThoughts.trim()}
              className="mt-3 px-6 py-3 bg-red-600 text-white rounded-lg font-medium hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
              </svg>
              Discuss with AI Assistant
            </button>
          </div>
        </div>
      )}

      {/* Mark as Done Button - Bottom Right */}
      {!done && onMarkAsDone && (
        <div className="absolute bottom-6 right-6">
          <button
            onClick={handleMarkAsDone}
            disabled={marking}
            className="px-6 py-3 bg-green-600 text-white rounded-lg font-medium hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed shadow-lg transition-all"
          >
            {marking ? 'Marking as Done...' : 'Mark as Done'}
          </button>
        </div>
      )}

      {/* Already Completed - Show Revisit Button */}
      {done && onMarkAsRevisited && (
        <div className="absolute bottom-6 right-6 flex items-center gap-3">
          <div className="flex items-center gap-2 px-4 py-2 bg-green-100 text-green-800 rounded-lg border-2 border-green-300">
            <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
            </svg>
            <span className="font-medium">Completed</span>
          </div>
          <button
            onClick={handleMarkAsRevisited}
            disabled={revisiting}
            className="px-6 py-3 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed shadow-lg transition-all"
          >
            {revisiting ? 'Marking as Revisited...' : 'Mark as Revisited'}
          </button>
        </div>
      )}
    </div>
  );
}

function ProgressIndicator({ total, completed }: { total: number; completed: number }) {
  const pct = total > 0 ? (completed / total) * 100 : 0;

  return (
    <div className="flex items-center gap-3">
      <div className="w-48 h-2 bg-gray-200 rounded-full overflow-hidden">
        <div
          className="h-full bg-gradient-to-r from-blue-500 to-green-500 transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="text-sm text-gray-700 font-medium">
        {Math.round(pct)}%
      </span>
    </div>
  );
}

function ChatBar({
  conceptId,
  currentContent
}: {
  conceptId?: string;
  currentContent?: string;
}) {
  type ChatMessage = {
    role: "user" | "assistant";
    text: string;
    timestamp: number;
  };

  const [question, setQuestion] = useState("");
  const [loading, setLoading] = useState(false);
  const [isExpanded, setIsExpanded] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [error, setError] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Listen for activity discussion events
  useEffect(() => {
    const handleActivityDiscussion = (event: Event) => {
      const customEvent = event as CustomEvent;
      const { activityTitle, userThoughts } = customEvent.detail;

      // Create formatted message with context
      const contextMessage = `I'm working on the activity "${activityTitle}". Here are my thoughts:\n\n${userThoughts}\n\nCan you provide feedback and discuss this with me?`;

      // Set the question and expand the chat
      setQuestion(contextMessage);
      setIsExpanded(true);

      // Auto-send the message after a brief delay to allow UI update
      setTimeout(() => {
        handleAskWithCustomMessage(contextMessage);
      }, 100);
    };

    window.addEventListener('openChatWithContext', handleActivityDiscussion);

    return () => {
      window.removeEventListener('openChatWithContext', handleActivityDiscussion);
    };
  }, [conceptId]);

  // Auto-scroll to bottom when messages change or loading state changes
  useEffect(() => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages, loading]);

  const handleAskWithCustomMessage = async (customMessage: string) => {
    if (!customMessage.trim() || !conceptId) return;

    const userMessage: ChatMessage = {
      role: "user",
      text: customMessage.trim(),
      timestamp: Date.now()
    };

    // Add user message immediately
    setMessages(prev => [...prev, userMessage]);
    setQuestion("");
    setLoading(true);
    setError(null);

    try {
      const response = await api.sendChatMessage({
        conceptId: parseInt(conceptId, 10),
        contextPhase: null,
        userText: userMessage.text,
        exerciseContext: null
      });

      const assistantMessage: ChatMessage = {
        role: "assistant",
        text: response.text,
        timestamp: Date.now()
      };

      setMessages(prev => [...prev, assistantMessage]);
    } catch (err) {
      console.error("[Chat] Error:", err);
      setError(err instanceof Error ? err.message : "Failed to get response");

      // Add error message to chat
      const errorMessage: ChatMessage = {
        role: "assistant",
        text: "I'm having trouble responding right now. Please try again.",
        timestamp: Date.now()
      };
      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setLoading(false);
    }
  };

  const handleAsk = async () => {
    if (!question.trim()) return;
    await handleAskWithCustomMessage(question);
  };

  // Collapsed view - minimal bar with indicator
  if (!isExpanded) {
    return (
      <button
        onClick={() => setIsExpanded(true)}
        className="w-full flex items-center justify-between px-6 py-3 bg-blue-50 hover:bg-blue-100 border-t border-blue-200 transition-colors"
      >
        <div className="flex items-center gap-3">
          <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
          </svg>
          <div className="text-left">
            <div className="font-semibold text-blue-900 text-sm">AI Learning Assistant</div>
            <div className="text-xs text-blue-700">Click to ask questions about this concept</div>
          </div>
        </div>
        <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
        </svg>
      </button>
    );
  }

  // Expanded view - full chat interface with message history
  return (
    <div className="border-t border-gray-300 bg-white shadow-lg max-h-[500px] flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-gray-200 bg-blue-50">
        <div className="flex items-center gap-2">
          <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
          </svg>
          <div>
            <h3 className="font-semibold text-gray-900">AI Learning Assistant</h3>
            <div className="text-xs text-gray-600">Ask questions • Get help • Discuss your work</div>
          </div>
          {messages.length > 0 && (
            <button
              onClick={() => setMessages([])}
              className="text-xs text-gray-500 hover:text-gray-700 ml-2"
              title="Clear chat history"
            >
              Clear
            </button>
          )}
        </div>
        <button
          onClick={() => setIsExpanded(false)}
          className="text-gray-500 hover:text-gray-700 p-1"
          title="Collapse chat"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>
      </div>

      {/* Message History */}
      {messages.length > 0 && (
        <div className="flex-1 overflow-y-auto p-4 space-y-3 min-h-[200px] max-h-[300px]">
          {messages.map((msg, idx) => (
            <div
              key={idx}
              className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
            >
              <div
                className={`max-w-[80%] rounded-lg px-4 py-2 ${
                  msg.role === "user"
                    ? "bg-blue-600 text-white"
                    : "bg-gray-50 text-gray-900 border border-gray-200"
                }`}
              >
                {msg.role === "user" ? (
                  <div className="text-sm whitespace-pre-wrap">{msg.text}</div>
                ) : (
                  <div className="text-sm prose prose-sm max-w-none">
                    <EnhancedMarkdown content={msg.text} />
                  </div>
                )}
                <div
                  className={`text-xs mt-1 ${
                    msg.role === "user" ? "text-blue-200" : "text-gray-500"
                  }`}
                >
                  {new Date(msg.timestamp).toLocaleTimeString()}
                </div>
              </div>
            </div>
          ))}
          {loading && (
            <div className="flex justify-start">
              <div className="bg-gray-100 text-gray-900 border border-gray-200 rounded-lg px-4 py-2">
                <div className="flex items-center gap-2">
                  <div className="flex gap-1">
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: "0ms" }}></div>
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: "150ms" }}></div>
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: "300ms" }}></div>
                  </div>
                  <span className="text-xs text-gray-500">Thinking...</span>
                </div>
              </div>
            </div>
          )}
          {/* Auto-scroll anchor */}
          <div ref={messagesEndRef} />
        </div>
      )}

      {/* Input Area */}
      <div className="p-4 border-t border-gray-200">
        {error && (
          <div className="mb-2 text-xs text-red-600 bg-red-50 border border-red-200 rounded px-2 py-1">
            {error}
          </div>
        )}
        <div className="flex gap-2">
          <input
            type="text"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && !e.shiftKey && handleAsk()}
            placeholder="Ask about this concept..."
            disabled={loading}
            className="flex-1 border rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500"
          />
          <button
            onClick={handleAsk}
            disabled={loading || !question.trim()}
            className="px-6 py-2 rounded-lg bg-blue-600 text-white font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {loading ? "..." : "Ask"}
          </button>
        </div>
        <div className="text-xs text-gray-500 mt-2 flex items-center gap-1">
          <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
          </svg>
          This is an AI chatbot assistant. Responses are context-aware but may not always be accurate.
        </div>
      </div>
    </div>
  );
}
