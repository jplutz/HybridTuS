import React from "react";
import { useSessionStore } from "@/state/store";

export default function ResultDrawer() {
    const events = useSessionStore((s) => s.console);
    const lastResult = [...events]
        .reverse()
        .find((e) => e.code.startsWith("GRADE_"));

    return (
        <aside className="col-span-3 rounded-2xl border bg-white p-4 shadow-soft">
            <p className="font-medium mb-2">Results & Feedback</p>

            {!lastResult ? (
                <div className="text-sm text-gray-700">Awaiting submission…</div>
            ) : (
                <div className="space-y-2 text-sm">
                    <div>
                        <span className="font-medium text-gray-800">Last result:</span>{" "}
                        <span>{lastResult.message}</span>
                    </div>
                    <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                        <div
                            className="h-full bg-blue-500 transition-all"
                            style={{ width: "70%" }}
                        />
                    </div>
                    <div className="text-xs text-gray-500">Mastery (client est.): 70%</div>
                    <button className="mt-2 rounded-md border px-3 py-1 text-xs text-gray-700 hover:bg-gray-100">
                        Doubt Accuracy
                    </button>
                </div>
            )}
        </aside>
    );
}
