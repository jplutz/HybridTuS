/**
 * Clean API Client for Protus-Hybrid Frontend
 * Single source of truth for all backend communication
 */

import type {
  CourseDto,
  ConceptDto,
  RecommendationResponse,
  InteractionRequest,
  GradeRequest,
  GradingResponse,
  LearnerDto,
  StyleUpdateRequest,
  ChatRequest,
  ChatResponse,
  SessionDto,
} from "@/types/api";
import { useSessionStore } from "@/state/store";
import { API_TOKEN } from "@/config/api";

// ========== CONFIGURATION ==========

// In development, Vite proxy handles /api requests
// In production, set VITE_API_BASE_URL environment variable
const BASE_URL = import.meta.env.VITE_API_BASE_URL || "";

// ========== CONSOLE LOGGING ==========

function logToConsole(origin: "DB" | "RECOMMENDER", level: "info" | "debug" | "warn" | "error", code: string, message: string) {
  useSessionStore.getState().logEvent({
    ts: Date.now(),
    level,
    origin,
    code,
    message,
  });
}

// ========== HTTP HELPER ==========

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${BASE_URL}${path}`;

  const res = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      "X-API-Token": API_TOKEN,
      ...(init?.headers || {}),
    },
    ...init,
  });

  if (!res.ok) {
    const errorText = await res.text().catch(() => "Unknown error");
    throw new Error(`HTTP ${res.status} ${res.statusText} @ ${path}: ${errorText}`);
  }

  // Handle 204 No Content or empty responses
  if (res.status === 204) {
    return undefined as T;
  }

  // Check if response has content
  const contentType = res.headers.get("content-type");
  const contentLength = res.headers.get("content-length");

  // If no content-type or content-length is 0, treat as empty
  if (!contentType || contentLength === "0") {
    return undefined as T;
  }

  // Only parse JSON if content-type indicates JSON
  if (contentType.includes("application/json")) {
    // Check if body is empty before parsing
    const text = await res.text();
    if (!text || text.trim().length === 0) {
      return undefined as T;
    }
    return JSON.parse(text) as T;
  }

  // For other content types, return undefined
  return undefined as T;
}

// ========== API CLIENT ==========

export const api = {
  // ========== COURSES & CONCEPTS ==========

  /**
   * Get all courses
   * GET /api/courses
   */
  async getCourses(): Promise<CourseDto[]> {
    return http<CourseDto[]>("/api/courses");
  },

  /**
   * Get single course by ID
   * GET /api/courses/:id
   */
  async getCourse(courseId: number): Promise<CourseDto> {
    return http<CourseDto>(`/api/courses/${courseId}`);
  },

  /**
   * Get all concepts for a course
   * GET /api/courses/:courseId/concepts?learnerId=X (optional)
   * If learnerId is provided, includes mastery data for that learner
   */
  async getCourseConcepts(courseId: number, learnerId?: number): Promise<ConceptDto[]> {
    const params = new URLSearchParams();
    if (learnerId !== undefined) {
      params.append('learnerId', String(learnerId));
    }
    const query = params.toString();
    return http<ConceptDto[]>(`/api/courses/${courseId}/concepts${query ? `?${query}` : ''}`);
  },

  /**
   * Get single concept by ID
   * GET /api/concepts/:id
   */
  async getConcept(conceptId: number): Promise<ConceptDto> {
    return http<ConceptDto>(`/api/concepts/${conceptId}`);
  },

  // ========== RECOMMENDATIONS ==========

  /**
   * Get next recommended learning object
   * GET /api/recommendations/next?learnerId=X&conceptId=Y
   */
  async getNextRecommendation(
    learnerId: number,
    conceptId?: number
  ): Promise<RecommendationResponse> {
    const params = new URLSearchParams({ learnerId: String(learnerId) });
    if (conceptId !== undefined) {
      params.append("conceptId", String(conceptId));
    }
    return http<RecommendationResponse>(`/api/recommendations/next?${params.toString()}`);
  },

  // ========== INTERACTIONS ==========

  /**
   * Log user interaction with learning object
   * POST /api/interactions
   * Returns: Response message indicating if interaction was recorded or filtered
   */
  async logInteraction(interaction: InteractionRequest): Promise<{ recorded: boolean; message: string }> {
    logToConsole("DB", "info", "INSERT", `Logged interaction: learnerId=${interaction.learnerId}, loId=${interaction.loId}`);

    const url = `${BASE_URL}/api/interactions`;
    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-API-Token": API_TOKEN,
      },
      body: JSON.stringify(interaction),
    });

    const message = await res.text();

    if (!res.ok) {
      throw new Error(`HTTP ${res.status} ${res.statusText}: ${message}`);
    }

    // Check if interaction was filtered (HTTP 202)
    const recorded = res.status === 200;
    return { recorded, message };
  },

  // ========== GRADING ==========

  /**
   * Submit exercise for grading
   * POST /api/exercises/grade
   */
  async gradeExercise(gradeRequest: GradeRequest): Promise<GradingResponse> {
    logToConsole("DB", "info", "INSERT", `Grading exercise: exerciseId=${gradeRequest.exerciseId}, learnerId=${gradeRequest.learnerId}`);
    return http<GradingResponse>("/api/exercises/grade", {
      method: "POST",
      body: JSON.stringify(gradeRequest),
    });
  },

  // ========== LEARNERS ==========

  /**
   * Get all learners
   * GET /api/learners
   */
  async getLearners(): Promise<LearnerDto[]> {
    return http<LearnerDto[]>("/api/learners");
  },

  /**
   * Get single learner by ID
   * GET /api/learners/:id
   */
  async getLearner(learnerId: number): Promise<LearnerDto> {
    return http<LearnerDto>(`/api/learners/${learnerId}`);
  },

  /**
   * Get mastery data for a learner across all concepts
   * GET /api/learners/:id/mastery
   * Returns: Map of conceptId -> mastery value (0.0-1.0)
   */
  async getLearnerMastery(learnerId: number): Promise<Record<number, number>> {
    return http<Record<number, number>>(`/api/learners/${learnerId}/mastery`);
  },

  /**
   * Create new learner
   * POST /api/learners
   */
  async createLearner(learner: Omit<LearnerDto, "id">): Promise<LearnerDto> {
    logToConsole("DB", "info", "INSERT", `Creating new learner: ${learner.displayName}`);
    return http<LearnerDto>("/api/learners", {
      method: "POST",
      body: JSON.stringify(learner),
    });
  },

  /**
   * Update learner's FSLSM style parameters
   * PATCH /api/learners/:id/styles
   */
  async updateLearnerStyles(
    learnerId: number,
    styles: StyleUpdateRequest
  ): Promise<LearnerDto> {
    logToConsole("DB", "info", "UPDATE", `Updating learning styles for learner ${learnerId}`);
    return http<LearnerDto>(`/api/learners/${learnerId}/styles`, {
      method: "PATCH",
      body: JSON.stringify(styles),
    });
  },

  // ========== CHAT ==========

  /**
   * Send chat message to LLM
   * POST /api/chat
   */
  async sendChatMessage(chatRequest: ChatRequest): Promise<ChatResponse> {
    return http<ChatResponse>("/api/chat", {
      method: "POST",
      body: JSON.stringify(chatRequest),
    });
  },

  // ========== SESSIONS ==========

  /**
   * Get or create learning session for a concept
   * GET /api/sessions?learnerId=X&conceptId=Y
   */
  async getOrCreateSession(learnerId: number, conceptId: number): Promise<SessionDto> {
    const params = new URLSearchParams({
      learnerId: String(learnerId),
      conceptId: String(conceptId),
    });
    return http<SessionDto>(`/api/sessions?${params.toString()}`);
  },

  /**
   * Record that learner viewed a module
   * POST /api/sessions/visit?sessionId=X&loId=Y
   */
  async recordModuleVisit(sessionId: string, loId: number): Promise<void> {
    logToConsole("DB", "info", "INSERT", `Module visited: sessionId=${sessionId}, moduleId=${loId}`);
    const params = new URLSearchParams({
      sessionId,
      loId: String(loId),
    });
    return http<void>(`/api/sessions/visit?${params.toString()}`, {
      method: "POST",
    });
  },

  /**
   * Mark module as done (completed)
   * POST /api/sessions/mark-done?sessionId=X&loId=Y
   */
  async markModuleAsDone(sessionId: string, loId: number): Promise<void> {
    logToConsole("DB", "info", "UPDATE", `Module marked as done: sessionId=${sessionId}, moduleId=${loId}`);
    const params = new URLSearchParams({
      sessionId,
      loId: String(loId),
    });
    return http<void>(`/api/sessions/mark-done?${params.toString()}`, {
      method: "POST",
    });
  },

  /**
   * Mark module as revisited (completed again after being done)
   * POST /api/sessions/mark-revisited?sessionId=X&loId=Y
   */
  async markModuleAsRevisited(sessionId: string, loId: number): Promise<void> {
    logToConsole("DB", "info", "UPDATE", `Module marked as revisited: sessionId=${sessionId}, moduleId=${loId}`);
    const params = new URLSearchParams({
      sessionId,
      loId: String(loId),
    });
    return http<void>(`/api/sessions/mark-revisited?${params.toString()}`, {
      method: "POST",
    });
  },

  /**
   * Mark session as completed
   * POST /api/sessions/{sessionId}/complete
   */
  async completeSession(sessionId: string): Promise<void> {
    logToConsole("DB", "info", "UPDATE", `Session completed: sessionId=${sessionId}`);
    return http<void>(`/api/sessions/${sessionId}/complete`, {
      method: "POST",
    });
  },

  // ========== EXERCISES ==========

  /**
   * Get exercises for a concept (excludes flagged and optionally attempted)
   * GET /api/exercises?conceptId=X&learnerId=Y (optional)
   * If learnerId is provided, excludes exercises already attempted by that learner
   */
  async getExercises(conceptId: number, learnerId?: number): Promise<any[]> {
    const params = new URLSearchParams({ conceptId: String(conceptId) });
    if (learnerId !== undefined) {
      params.append('learnerId', String(learnerId));
    }
    return http<any[]>(`/api/exercises?${params.toString()}`);
  },

  /**
   * Get exercise IDs already attempted by a learner for a concept
   * GET /api/exercises/attempted?learnerId=X&conceptId=Y
   */
  async getAttemptedExercises(learnerId: number, conceptId: number): Promise<number[]> {
    const params = new URLSearchParams({
      learnerId: String(learnerId),
      conceptId: String(conceptId),
    });
    return http<number[]>(`/api/exercises/attempted?${params.toString()}`);
  },

  /**
   * Generate new exercise for concept
   * POST /api/exercises/generate
   */
  async generateExercise(
    conceptId: number,
    type: string = "MC",
    difficulty: string = "medium",
    previousQuestions: string[] = []
  ): Promise<any> {
    logToConsole("DB", "info", "INSERT", `Generating exercise: conceptId=${conceptId}, type=${type}, difficulty=${difficulty}, avoiding ${previousQuestions.length} previous questions`);
    return http<any>("/api/exercises/generate", {
      method: "POST",
      body: JSON.stringify({
        conceptId,
        type,
        difficulty,
        previousQuestions,
      }),
    });
  },

  /**
   * Flag exercise as problematic
   * POST /api/exercises/{id}/flag
   */
  async flagExercise(
    exerciseId: number,
    learnerId: number,
    reason: string,
    userAnswer?: string,
    gradingScore?: number,
    gradingFeedback?: string
  ): Promise<void> {
    logToConsole("DB", "info", "UPDATE", `Exercise flagged: exerciseId=${exerciseId}, learnerId=${learnerId}, reason=${reason}`);
    return http<void>(`/api/exercises/${exerciseId}/flag`, {
      method: "POST",
      body: JSON.stringify({
        learnerId,
        userAnswer: userAnswer || null,
        gradingScore: gradingScore || null,
        gradingFeedback: gradingFeedback || null,
        reason
      }),
    });
  },

  /**
   * Clear/remove flag from exercise
   * DELETE /api/exercises/flags/{flagId}
   */
  async clearFlag(flagId: number): Promise<void> {
    logToConsole("DB", "info", "DELETE", `Clearing flag: flagId=${flagId}`);
    return http<void>(`/api/exercises/flags/${flagId}`, {
      method: "DELETE",
    });
  },

  /**
   * Accept flag (mark as reviewed but keep flagged)
   * PATCH /api/exercises/flags/{flagId}/accept
   */
  async acceptFlag(flagId: number): Promise<void> {
    logToConsole("DB", "info", "UPDATE", `Accepting flag: flagId=${flagId}`);
    return http<void>(`/api/exercises/flags/${flagId}/accept`, {
      method: "PATCH",
    });
  },

  // ========== ADMIN ==========

  /**
   * Get synthetic data generation status
   * GET /api/admin/data/status
   */
  async getDataStatus(): Promise<any> {
    return http<any>("/api/admin/data/status");
  },

  /**
   * Generate synthetic learner data
   * POST /api/admin/data/generate
   */
  async generateData(): Promise<any> {
    return http<any>("/api/admin/data/generate", {
      method: "POST",
    });
  },

  /**
   * Run sequence mining
   * POST /api/admin/mining/run
   */
  async runMining(): Promise<any> {
    return http<any>("/api/admin/mining/run", {
      method: "POST",
    });
  },

  /**
   * Run evaluation
   * POST /api/admin/evaluation/run
   */
  async runEvaluation(): Promise<any> {
    return http<any>("/api/admin/evaluation/run", {
      method: "POST",
    });
  },

  /**
   * Check validation thresholds
   * GET /api/admin/validation/check
   */
  async checkValidation(): Promise<any> {
    return http<any>("/api/admin/validation/check");
  },

  /**
   * Run complete pipeline (all steps)
   * POST /api/admin/pipeline/run
   */
  async runPipeline(): Promise<any> {
    return http<any>("/api/admin/pipeline/run", {
      method: "POST",
    });
  },

  /**
   * Get flagged exercises needing review
   * GET /api/admin/exercises/flagged
   */
  async getFlaggedExercises(): Promise<any[]> {
    return http<any[]>("/api/admin/exercises/flagged");
  },

  /**
   * Clear synthetic data
   * POST /api/admin/data/clear
   */
  async clearData(): Promise<void> {
    return http<void>("/api/admin/data/clear", {
      method: "POST",
    });
  },
};

// Export default for convenience
export default api;
