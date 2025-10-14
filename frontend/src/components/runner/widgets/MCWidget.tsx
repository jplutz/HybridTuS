import React, { useState } from "react";
import { useSessionStore } from "@/state/store";

interface MCOption {
    id: string;
    text: string;
}

interface MCWidgetProps {
    question: string;
    options: MCOption[];
    correctIds?: string[];
    phaseName?: string;
}

export default function MCWidget({
                                     question,
                                     options,
                                     correctIds = [],
                                     phaseName = "Practice",
                                 }: MCWidgetProps) {
    const [selected, setSelected] = useState<string | null>(null);
    const [submitted, setSubmitted] = useState(false);
    const logEvent = useSessionStore((s) => s.logEvent);

    const handleSubmit = () => {
        if (!selected) return;
        const correct = correctIds.includes(selected);
        setSubmitted(true);
        logEvent({
            ts: Date.now(),
            level: correct ? "info" : "warn",
            origin: "UI",
            code: "GRADE_MC",
            message: `${phaseName}: ${correct ? "Correct" : "Incorrect"} answer.`,
        });
    };

    return (
        <div className="rounded-xl border bg-white p-4 shadow-soft">
            <p className="font-medium mb-3">{question}</p>
            <div className="space-y-2">
                {options.map((o) => (
                    <label
                        key={o.id}
                        className={`flex items-center gap-2 text-sm cursor-pointer p-2 rounded-md border ${
                            selected === o.id ? "border-blue-500 bg-blue-50" : "border-gray-200"
                        }`}
                    >
                        <input
                            type="radio"
                            name="mc"
                            value={o.id}
                            checked={selected === o.id}
                            onChange={() => setSelected(o.id)}
                            disabled={submitted}
                        />
                        {o.text}
                    </label>
                ))}
            </div>
            <button
                onClick={handleSubmit}
                disabled={!selected || submitted}
                className="mt-4 rounded-md bg-blue-600 text-white px-4 py-2 text-sm hover:bg-blue-700 disabled:opacity-40"
            >
                {submitted ? "Submitted" : "Submit"}
            </button>
        </div>
    );
}
