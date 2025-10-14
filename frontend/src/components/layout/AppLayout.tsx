import React from "react";
import { Outlet, NavLink } from "react-router-dom";
import ConsolePanel from "@/components/console/ConsolePanel";
import LearnerSelector from "@/components/learner/LearnerSelector";

export default function AppLayout() {
    return (
        <div className="min-h-screen flex flex-col">
            <header className="sticky top-0 z-10 border-b bg-white/80 backdrop-blur">
                <div className="mx-auto max-w-7xl px-4 py-3 flex items-center justify-between">
                    <div className="flex items-center gap-6">
                        <NavLink to="/courses" className="flex items-center gap-2 text-xl font-bold text-gray-900 hover:text-blue-600 transition-colors">
                            <span className="text-2xl">📚</span>
                            <span>HybridTuS</span>
                        </NavLink>
                        <nav className="flex items-center gap-4 text-sm">
                            <NavLink
                                to="/courses"
                                className={({ isActive }) =>
                                    `px-2 py-1 rounded ${isActive ? "bg-gray-900 text-white" : "text-gray-700 hover:bg-gray-100"}`
                                }
                            >
                                Courses
                            </NavLink>
                            <NavLink
                                to="/admin"
                                className={({ isActive }) =>
                                    `px-2 py-1 rounded ${isActive ? "bg-gray-900 text-white" : "text-gray-700 hover:bg-gray-100"}`
                                }
                            >
                                Admin
                            </NavLink>
                        </nav>
                    </div>

                    <div className="flex items-center gap-4">
                        <LearnerSelector />
                        <div className="text-xs text-gray-500">v0.1.0</div>
                    </div>
                </div>
            </header>

            <main className="flex-1 mx-auto max-w-7xl px-4 py-6">
                <Outlet />
            </main>

            <ConsolePanel />
        </div>
    );
}
