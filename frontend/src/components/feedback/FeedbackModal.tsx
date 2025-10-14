import React from "react";
import { useSessionStore } from "@/state/store";

interface FeedbackModalProps {
    open: boolean;
    onClose: () => void;
}

export default function FeedbackModal({ open, onClose }: FeedbackModalProps) {
    const logEvent = useSessionStore((s) => s.logEvent);
    const [values, setValues] = React.useState({
        activeReflective: 0,
        sensingIntuitive: 0,
        visualVerbal: 0,
        sequentialGlobal: 0,
    });

    if (!open) return null;

    const handleSubmit = () => {
        logEvent({
            ts: Date.now(),
            level: "info",
            origin: "UI",
            code: "SESSION_FEEDBACK",
            message: `FSLSM feedback submitted`,
        });
        onClose();
    };

    const setVal = (key: keyof typeof values, v: number) =>
        setValues((s) => ({ ...s, [key]: v }));

    const Slider = ({
                        label,
                        field,
                    }: {
        label: string;
        field: keyof typeof values;
    }) => (
        <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-1">
                {label}
            </label>
            <input
                type="range"
                min={-11}
                max={11}
                value={values[field]}
                onChange={(e) => setVal(field, Number(e.target.value))}
                className="w-full"
            />
            <div className="text-xs text-gray-500 text-right">{values[field]}</div>
        </div>
    );

    return (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center">
            <div className="bg-white rounded-2xl shadow-soft w-full max-w-md p-6">
                <h3 className="text-lg font-semibold mb-4">
                    Session Feedback (FSLSM Dimensions)
                </h3>

                <Slider label="Active ↔ Reflective" field="activeReflective" />
                <Slider label="Sensing ↔ Intuitive" field="sensingIntuitive" />
                <Slider label="Visual ↔ Verbal" field="visualVerbal" />
                <Slider label="Sequential ↔ Global" field="sequentialGlobal" />

                <div className="mt-4 flex justify-end gap-2">
                    <button
                        onClick={onClose}
                        className="px-4 py-2 text-sm rounded-md border hover:bg-gray-100"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSubmit}
                        className="px-4 py-2 text-sm rounded-md bg-blue-600 text-white hover:bg-blue-700"
                    >
                        Submit
                    </button>
                </div>
            </div>
        </div>
    );
}
