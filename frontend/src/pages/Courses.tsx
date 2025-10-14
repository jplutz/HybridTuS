import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "@/adapters";
import { useLearner } from "@/contexts/LearnerContext";
import type { CourseDto, ConceptDto } from "@/types/api";

export default function Courses() {
    const { learner, isGuest } = useLearner();
    const [courses, setCourses] = useState<CourseDto[]>([]);
    const [masteryByCourse, setMasteryByCourse] = useState<Record<number, { mastered: number; total: number }>>({});
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        (async () => {
            try {
                setLoading(true);
                const cs = await api.getCourses();
                setCourses(cs);

                // Calculate mastery for each course if learner is selected
                if (learner) {
                    const masteryData: Record<number, { mastered: number; total: number }> = {};
                    for (const c of cs) {
                        const concepts = await api.getCourseConcepts(c.id, learner.id);
                        const total = concepts.length;
                        // Count concepts with mastery >= 0.7 (70%) as mastered
                        const mastered = concepts.filter(concept => concept.mastery !== null && concept.mastery >= 0.7).length;
                        masteryData[c.id] = { mastered, total };
                    }
                    setMasteryByCourse(masteryData);
                }

                console.log("[Courses] loaded", { courses: cs, learner: learner?.displayName });
            } catch (err) {
                console.error("[Courses] Failed to load:", err);
            } finally {
                setLoading(false);
            }
        })();
    }, [learner]);

    // Guest mode message
    if (isGuest) {
        return (
            <div className="p-6 space-y-6 min-w-[320px]">
                <h1 className="text-2xl font-bold">Courses</h1>
                <div className="border rounded-lg p-8 text-center bg-yellow-50 border-yellow-200">
                    <h2 className="text-xl font-semibold mb-2">Welcome to HybridTuS</h2>
                    <p className="text-gray-600 mb-4">
                        Please select a learner from the dropdown in the header to access courses and personalized learning sessions.
                    </p>
                    <p className="text-sm text-gray-500">
                        Guest mode allows browsing only. Select a learner identity to unlock interactive features.
                    </p>
                </div>
            </div>
        );
    }

    if (loading) {
        return (
            <div className="p-6 space-y-6 min-w-[320px]">
                <h1 className="text-2xl font-bold">Courses</h1>
                <div className="text-gray-500">Loading courses...</div>
            </div>
        );
    }

    return (
        <div className="p-6 space-y-6 min-w-[320px]">
            {/* Header */}
            <div>
                <h1 className="text-2xl font-bold">Courses</h1>
                <div className="text-sm text-gray-500 mt-2">
                    {courses.length} {courses.length === 1 ? 'course' : 'courses'}
                </div>
            </div>

            {/* Courses Grid */}
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                {courses.map((course) => {
                    const mastery = masteryByCourse[course.id] || { mastered: 0, total: course.conceptCount };
                    const completionPct = mastery.total > 0 ? (mastery.mastered / mastery.total) * 100 : 0;
                    const isComplete = completionPct === 100;
                    const isStarted = mastery.mastered > 0;

                    // Determine card styling and status - matching CourseDetail structure
                    const status = isComplete
                        ? {
                            cardClass: 'border-2 border-green-400 bg-gradient-to-br from-green-50 to-emerald-50',
                            badge: { text: 'Complete', class: 'bg-green-100 text-green-700' },
                            textClass: 'text-gray-900'
                          }
                        : isStarted
                        ? {
                            cardClass: 'border bg-white hover:shadow-lg',
                            badge: { text: 'In Progress', class: 'bg-blue-50 text-blue-700' },
                            textClass: 'text-gray-900'
                          }
                        : {
                            cardClass: 'border bg-white hover:shadow-lg',
                            badge: { text: 'Open', class: 'bg-blue-50 text-blue-700' },
                            textClass: 'text-gray-900'
                          };

                    return (
                        <Link
                            key={course.id}
                            to={`/courses/${course.id}`}
                            className={`rounded-lg p-5 transition-all space-y-3 min-w-[280px] ${status.cardClass}`}
                        >
                            <div className="flex items-start justify-between">
                                <div className="flex items-center gap-2 flex-1">
                                    <h3 className={`font-semibold text-lg ${status.textClass}`}>{course.title}</h3>
                                </div>
                                <div className={`text-xs px-2 py-1 rounded font-medium ml-2 flex-shrink-0 ${status.badge.class}`}>
                                    {status.badge.text}
                                </div>
                            </div>

                            {/* Completion Progress */}
                            <div className="space-y-2">
                                <div className={`flex items-center justify-between text-xs ${status.textClass}`}>
                                    <span>Completion</span>
                                    <span className="font-medium">
                                        {mastery.total > 0 ? `${Math.round(completionPct)}%` : 'Not started'}
                                    </span>
                                </div>
                                <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                                    <div
                                        className={`h-full transition-all ${
                                            isComplete
                                                ? 'bg-gradient-to-r from-green-500 to-emerald-500'
                                                : 'bg-gradient-to-r from-blue-500 to-purple-500'
                                        }`}
                                        style={{ width: `${completionPct}%` }}
                                    />
                                </div>
                            </div>

                            {/* Status Icon */}
                            {isComplete && (
                                <div className="flex items-center gap-1 text-green-700 text-xs">
                                    <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                                    </svg>
                                    <span className="font-medium">Completed</span>
                                </div>
                            )}
                        </Link>
                    );
                })}
            </div>
        </div>
    );
}
