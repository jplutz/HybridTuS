import React from "react";
import { useSessionStore } from "@/state/store";

interface PhaseRendererProps {
    phaseName: string;
}

export default function PhaseRenderer({ phaseName }: PhaseRendererProps) {
    const logEvent = useSessionStore((s) => s.logEvent);

    const handleGrade = () => {
        logEvent({
            ts: Date.now(),
            level: "info",
            origin: "UI",
            code: "GRADE_DEMO",
            message: `Submitted mock answer for phase "${phaseName}"`,
        });
    };

    return (
        <div className="rounded-2xl border bg-white p-4 shadow-soft">
            <h3 className="text-lg font-semibold mb-2">{phaseName}</h3>
            <p className="text-sm text-gray-700 mb-3">
                Placeholder content for the <strong>{phaseName}</strong> phase. The
                actual text, examples, and exercises will later be loaded dynamically.
            </p>
            <button
                onClick={handleGrade}
                className="rounded-md bg-blue-600 text-white px-4 py-2 text-sm hover:bg-blue-700"
            >
                Submit Mock Answer
            </button>
        </div>
    );
}
