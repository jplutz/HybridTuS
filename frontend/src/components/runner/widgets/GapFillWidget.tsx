import React, { useState } from "react";
import { useSessionStore } from "@/state/store";

interface GapFillWidgetProps {
    textWithGaps: string; // example: "The capital of France is {{1}}."
    correctAnswers?: Record<string, string>;
    phaseName?: string;
}

export default function GapFillWidget({
                                          textWithGaps,
                                          correctAnswers = {},
                                          phaseName = "Practice",
                                      }: GapFillWidgetProps) {
    const [answers, setAnswers] = useState<Record<string, string>>({});
    const [submitted, setSubmitted] = useState(false);
    const logEvent = useSessionStore((s) => s.logEvent);

    const handleChange = (id: string, value: string) => {
        setAnswers((s) => ({ ...s, [id]: value }));
    };

    const handleSubmit = () => {
        setSubmitted(true);
        let correctCount = 0;
        Object.keys(correctAnswers).forEach((id) => {
            if (
                answers[id]?.trim().toLowerCase() ===
                correctAnswers[id].trim().toLowerCase()
            ) {
                correctCount++;
            }
        });
        const total = Object.keys(correctAnswers).length || 1;
        const score = correctCount / total;
        logEvent({
            ts: Date.now(),
            level: score === 1 ? "info" : "warn",
            origin: "UI",
            code: "GRADE_GAP",
            message: `${phaseName}: ${Math.round(score * 100)}% correct.`,
        });
    };

    const renderText = () =>
        textWithGaps.split(/(\{\{\d+\}\})/g).map((part, idx) => {
            const match = part.match(/\{\{(\d+)\}\}/);
            if (!match) return <span key={idx}>{part}</span>;
            const id = match[1];
            return (
                <input
                    key={id}
                    type="text"
                    value={answers[id] || ""}
                    disabled={submitted}
                    onChange={(e) => handleChange(id, e.target.value)}
                    className="mx-1 border-b border-gray-400 focus:outline-none focus:border-blue-500 text-sm w-20 text-center"
                />
            );
        });

    return (
        <div className="rounded-xl border bg-white p-4 shadow-soft">
            <div className="mb-3 text-sm text-gray-800">{renderText()}</div>
            <button
                onClick={handleSubmit}
                disabled={submitted}
                className="mt-4 rounded-md bg-blue-600 text-white px-4 py-2 text-sm hover:bg-blue-700 disabled:opacity-40"
            >
                {submitted ? "Submitted" : "Submit"}
            </button>
        </div>
    );
}
