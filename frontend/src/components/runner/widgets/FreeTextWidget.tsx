import React, { useState } from "react";
import { useSessionStore } from "@/state/store";

interface FreeTextWidgetProps {
    prompt: string;
    referenceAnswer?: string;
    phaseName?: string;
}

export default function FreeTextWidget({
                                           prompt,
                                           referenceAnswer = "",
                                           phaseName = "Practice",
                                       }: FreeTextWidgetProps) {
    const [input, setInput] = useState("");
    const [submitted, setSubmitted] = useState(false);
    const [feedback, setFeedback] = useState<string | null>(null);
    const logEvent = useSessionStore((s) => s.logEvent);

    const handleSubmit = () => {
        if (!input.trim()) return;
        setSubmitted(true);

        const correct =
            input.trim().toLowerCase() === referenceAnswer.trim().toLowerCase();
        const rationale = correct
            ? "Good explanation; aligns with expected concept."
            : "Response deviates from reference; review concept details.";

        setFeedback(rationale);

        logEvent({
            ts: Date.now(),
            level: correct ? "info" : "warn",
            origin: "UI",
            code: "GRADE_FREETEXT",
            message: `${phaseName}: ${correct ? "Correct" : "Incorrect"} (mock check).`,
        });
    };

    return (
        <div className="rounded-xl border bg-white p-4 shadow-soft">
            <p className="font-medium mb-3">{prompt}</p>
            <textarea
                value={input}
                onChange={(e) => setInput(e.target.value)}
                disabled={submitted}
                rows={5}
                placeholder="Type your answer here..."
                className="w-full border rounded-md p-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
            <div className="mt-3 flex justify-end">
                <button
                    onClick={handleSubmit}
                    disabled={submitted}
                    className="rounded-md bg-blue-600 text-white px-4 py-2 text-sm hover:bg-blue-700 disabled:opacity-40"
                >
                    {submitted ? "Submitted" : "Submit"}
                </button>
            </div>
            {feedback && (
                <div className="mt-3 text-sm text-gray-700">
                    <strong>Feedback:</strong> {feedback}
                </div>
            )}
        </div>
    );
}
