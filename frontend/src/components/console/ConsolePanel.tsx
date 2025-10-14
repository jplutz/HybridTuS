import React from "react";
import { useSessionStore } from "@/state/store";
import { clsx } from "clsx";

export default function ConsolePanel() {
    const events = useSessionStore((s) => s.console);
    const [visible, setVisible] = React.useState(false);

    return (
        <div className="fixed top-16 right-0 z-20 m-3">
            <button
                onClick={() => setVisible(!visible)}
                className="rounded-md bg-gray-800 text-white text-xs px-3 py-1 shadow-lg hover:bg-gray-700 transition-colors"
            >
                {visible ? "Hide Console" : "Show Console"}
            </button>

            {visible && (
                <div className="mt-2 w-[420px] max-h-[400px] overflow-y-auto rounded-md bg-black text-gray-200 text-xs font-mono shadow-xl p-2 border border-gray-700">
                    {events.length === 0 ? (
                        <div className="opacity-60">No events logged.</div>
                    ) : (
                        events.map((e, i) => (
                            <div
                                key={i}
                                className={clsx("border-l-2 pl-2 mb-1", {
                                    "border-green-500": e.level === "info",
                                    "border-yellow-500": e.level === "warn",
                                    "border-red-500": e.level === "error",
                                    "border-gray-500": e.level === "debug",
                                })}
                            >
                <span className="text-gray-400">
                  [{new Date(e.ts).toLocaleTimeString()}]
                </span>{" "}
                                <span className={clsx("font-semibold", {
                                    "text-blue-400": e.origin === "DB",
                                    "text-purple-400": e.origin === "RECOMMENDER",
                                    "text-green-400": e.origin === "API",
                                    "text-yellow-400": e.origin === "LLM",
                                    "text-orange-400": e.origin === "GRADER",
                                    "text-gray-400": e.origin === "UI" || e.origin === "STUB",
                                })}>{e.origin}</span> →{" "}
                                <span>{e.message}</span>
                            </div>
                        ))
                    )}
                </div>
            )}
        </div>
    );
}
