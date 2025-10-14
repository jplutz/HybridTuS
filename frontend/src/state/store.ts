import { create } from "zustand";

interface ConsoleEvent {
    ts: number;
    level: "info" | "debug" | "warn" | "error";
    origin: "UI" | "API" | "STUB" | "LLM" | "GRADER" | "DB" | "RECOMMENDER";
    code: string;
    message: string;
}

interface SessionState {
    sessionId: string | null;
    currentPhaseIndex: number;
    console: ConsoleEvent[];
    startSession: (id: string) => void;
    advancePhase: () => void;
    logEvent: (event: ConsoleEvent) => void;
    reset: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
    sessionId: null,
    currentPhaseIndex: 0,
    console: [],
    startSession: (id) => set({ sessionId: id, currentPhaseIndex: 0 }),
    advancePhase: () =>
        set((state) => ({ currentPhaseIndex: state.currentPhaseIndex + 1 })),
    logEvent: (event) =>
        set((state) => ({ console: [...state.console, event] })),
    reset: () => set({ sessionId: null, currentPhaseIndex: 0, console: [] }),
}));
