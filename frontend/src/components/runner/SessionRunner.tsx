import React from "react";
import { useSessionStore } from "@/state/store";
import PhaseRenderer from "@/components/runner/PhaseRenderer";
import ResultDrawer from "@/components/drawer/ResultDrawer";
import FeedbackModal from "@/components/feedback/FeedbackModal";

export default function SessionRunner() {
    const { sessionId, currentPhaseIndex, startSession, advancePhase, logEvent } =
        useSessionStore();
    const [feedbackOpen, setFeedbackOpen] = React.useState(false);

    const phases = [
        "Explanation",
        "Example",
        "Checkpoint",
        "Practice",
        "Assessment",
        "Summary",
    ];

    const handleStart = () => {
        const id = "local-" + Date.now();
        startSession(id);
        logEvent({
            ts: Date.now(),
            level: "info",
            origin: "UI",
            code: "SESSION_START",
            message: `Started session ${id}`,
        });
    };

    const handleNext = () => {
        if (currentPhaseIndex < phases.length - 1) {
            advancePhase();
            logEvent({
                ts: Date.now(),
                level: "info",
                origin: "UI",
                code: "PHASE_ADVANCE",
                message: `Advanced to phase index ${currentPhaseIndex + 1}`,
            });
        } else {
            setFeedbackOpen(true);
        }
    };

    if (!sessionId) {
        return (
            <div className="p-6">
                <h2 className="text-xl font-semibold mb-4">Session Runner</h2>
                <button
                    onClick={handleStart}
                    className="rounded-md bg-blue-600 text-white px-4 py-2 hover:bg-blue-700"
                >
                    Start Session
                </button>
            </div>
        );
    }

    const phaseName = phases[currentPhaseIndex] || "Summary";
    const isLast = currentPhaseIndex >= phases.length - 1;

    return (
        <>
            <div className="p-6 grid grid-cols-12 gap-4">
                <aside className="col-span-3 rounded-2xl border bg-white p-4 shadow-soft">
                    <p className="font-medium mb-2">Lesson Plan</p>
                    <ul className="text-sm text-gray-700 space-y-1">
                        {phases.map((p, i) => (
                            <li
                                key={p}
                                className={
                                    i === currentPhaseIndex
                                        ? "font-semibold text-blue-600"
                                        : i < currentPhaseIndex
                                            ? "text-gray-400 line-through"
                                            : ""
                                }
                            >
                                {p}
                            </li>
                        ))}
                    </ul>
                </aside>

                <main className="col-span-6">
                    <PhaseRenderer phaseName={phaseName} />
                    <div className="mt-4 flex justify-end">
                        <button
                            onClick={handleNext}
                            className="rounded-md bg-blue-600 text-white px-4 py-2 text-sm hover:bg-blue-700"
                        >
                            {isLast ? "Finish & Feedback" : "Next Phase"}
                        </button>
                    </div>
                </main>

                <ResultDrawer />
            </div>

            <FeedbackModal open={feedbackOpen} onClose={() => setFeedbackOpen(false)} />
        </>
    );
}
