/**
 * Unified Type Definitions for Protus-Hybrid Frontend
 * Single source of truth matching backend API contracts
 */

// ========== CORE DOMAIN TYPES ==========

/**
 * Learning Object types from backend
 * Based on FSLSM learning science principles for adaptive sequencing
 * Maps to LearningObject.type field
 *
 * The 5 canonical types used in this system:
 * T, E, A, F, Test
 */
export type LoType =
  | "T"      // Theory - Foundational explanations
  | "E"      // Example - Demonstrations and concrete applications
  | "A"      // Activity - Hands-on exercises, quizzes
  | "F"      // Figure - Visual representations (Visual learners only)
  | "Test";  // Test - Final knowledge checks

/**
 * Phase types for structured learning sessions
 * Used in UI navigation and session planning
 */
export type PhaseType =
  | "EXPLANATION"
  | "EXAMPLE"
  | "CHECKPOINT"
  | "ASSESSMENT"
  | "SUMMARY"
  | "PRACTICE";

// ========== COURSE & CONCEPT DTOs ==========

export interface CourseDto {
  id: number;
  title: string;
  description: string | null;
  conceptCount: number;
}

export interface ConceptDto {
  id: number;
  title: string;
  description: string | null;
  mastery: number | null; // 0.0-1.0 or null if not started
  prerequisites: ConceptPrereqDto[];
  courseId: number;
  orderIndex: number;
}

export interface ConceptPrereqDto {
  conceptId: number;
  conceptName: string;
}

// ========== LEARNING OBJECT DTOs ==========

export interface LearningObjectDto {
  id: number;
  conceptId: number;
  type: LoType;
  sourceUri: string | null;
  estTimeMin: number | null;
  version: number;
  rubricLink: string | null;
}

// ========== RECOMMENDATION DTOs ==========

export interface RecommendationResponse {
  loId: number;
  loType: LoType;
  conceptId: number;
  conceptName: string;
  version: number;
  sourceUri: string | null;
  estTimeMin: number | null;
  // Reasoning metadata
  recommendationSource: "mined" | "cold_start" | "intervention" | "lighter_content" | "fallback";
  supportScore: number | null;
  userScore: number | null;
  totalScore: number | null;
  isStuckIntervention: boolean;
}

// ========== INTERACTION & GRADING DTOs ==========

export interface InteractionRequest {
  learnerId: number;
  loId: number;
  score?: number; // deprecated, use scoreRaw
  scoreRaw?: number; // 1-5 scale
  hintCount?: number;
  durationSec?: number;
}

export interface GradeRequest {
  exerciseId: number;
  learnerId: number;
  userAnswer: string;
  explainReasoning?: boolean;
}

export interface GradingResponse {
  score: number; // Normalized score 0.0-1.0
  feedback: string;
  correct: boolean;
  deterministic: boolean;
  reasoning: string | null; // Optional LLM explanation
  scoreRaw: number; // Raw score 1-5 for EWMA mastery calculation
}

// ========== LEARNER & FSLSM DTOs ==========

export interface LearnerDto {
  id: number;
  displayName: string;
  externalRef: string;
  // FSLSM dimensions: -11 to +11
  styleActiveReflective: number;
  styleSensingIntuitive: number;
  styleVisualVerbal: number;
  styleSequentialGlobal: number;
}

export interface StyleUpdateRequest {
  styleActiveReflective?: number;
  styleSensingIntuitive?: number;
  styleVisualVerbal?: number;
  styleSequentialGlobal?: number;
}

// ========== CHAT DTOs ==========

export interface ExerciseContext {
  exerciseId: number;
  type: string;
  question: string;
  userAnswer: string | null;
  gradingScore: number | null;
  gradingFeedback: string | null;
}

export interface ChatRequest {
  conceptId: number;
  contextPhase: string | null;
  userText: string;
  exerciseContext?: ExerciseContext | null;
}

export interface ChatResponse {
  text: string;
}

// ========== SESSION DTOs ==========

/**
 * Learning session returned by backend
 * Contains all available modules for a concept
 */
export interface SessionDto {
  sessionId: string;
  learnerId: number;
  conceptId: number;
  conceptName: string;
  modules: ModuleDto[];
  visitedModuleIds: number[];
  doneModuleIds: number[];
  revisitedModuleIds: number[];
  recommendedModuleId: number | null;
  completed: boolean;
  recommendationTrace: string[];
}

/**
 * Module (Learning Object) in a session
 * User can navigate freely between modules
 */
export interface ModuleDto {
  id: number;
  type: string; // T, E, A, F, or Test
  title: string;
  content: string; // Markdown content
  estimatedMinutes: number;
}

// ========== API ERROR TYPES ==========

export interface ApiError {
  message: string;
  status: number;
  path: string;
}
