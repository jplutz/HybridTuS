import React from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { api } from "@/adapters";
import { useLearner } from "@/contexts/LearnerContext";
import type { ConceptDto, CourseDto } from "@/types/api";

export default function CourseDetail() {
    const { courseId = "" } = useParams();
    const navigate = useNavigate();
    const { learner, isGuest } = useLearner();
    const [course, setCourse] = React.useState<CourseDto | null>(null);
    const [concepts, setConcepts] = React.useState<ConceptDto[]>([]);
    const [loading, setLoading] = React.useState(true);
    const [lockedConceptWarning, setLockedConceptWarning] = React.useState<{ concept: ConceptDto; unmetPrereqs: string[] } | null>(null);

    React.useEffect(() => {
        (async () => {
            try {
                setLoading(true);
                const id = parseInt(courseId, 10);
                if (!isNaN(id)) {
                    // Pass learnerId to getCourseConcepts to get mastery data automatically
                    const [courseData, conceptsData] = await Promise.all([
                        api.getCourse(id),
                        api.getCourseConcepts(id, learner?.id)
                    ]);
                    setCourse(courseData);
                    setConcepts(conceptsData);
                }
            } catch (error) {
                console.error("[CourseDetail] Failed to load:", error);
            } finally {
                setLoading(false);
            }
        })();
    }, [courseId, learner, isGuest]);

    // Helper to check if concept is available based on prerequisites
    const isConceptAvailable = (concept: ConceptDto): boolean => {
        if (!concept.prerequisites || concept.prerequisites.length === 0) {
            return true; // No prerequisites
        }
        // Check if all prerequisites are mastered (>= 0.7 mastery)
        return concept.prerequisites.every(prereq => {
            const prereqConcept = concepts.find(c => c.id === prereq.conceptId);
            return prereqConcept && prereqConcept.mastery !== null && prereqConcept.mastery >= 0.7;
        });
    };

    // Get unmet prerequisites for a concept
    const getUnmetPrerequisites = (concept: ConceptDto): string[] => {
        if (!concept.prerequisites || concept.prerequisites.length === 0) {
            return [];
        }
        return concept.prerequisites
            .filter(prereq => {
                const prereqConcept = concepts.find(c => c.id === prereq.conceptId);
                return !prereqConcept || prereqConcept.mastery === null || prereqConcept.mastery < 0.7;
            })
            .map(prereq => prereq.conceptName);
    };

    // Determine the status and styling for a concept
    const getConceptStatus = (concept: ConceptDto) => {
        const isMastered = concept.mastery !== null && concept.mastery >= 0.7;
        const isAvailable = isConceptAvailable(concept);

        if (isMastered) {
            return {
                status: 'mastered' as const,
                cardClass: 'border-2 border-green-400 bg-gradient-to-br from-green-50 to-emerald-50',
                badge: { text: 'Mastered', class: 'bg-green-100 text-green-700' },
                textClass: 'text-gray-900'
            };
        } else if (isAvailable) {
            return {
                status: 'available' as const,
                cardClass: 'border bg-white hover:shadow-lg',
                badge: { text: 'Open', class: 'bg-blue-50 text-blue-700' },
                textClass: 'text-gray-900'
            };
        } else {
            return {
                status: 'locked' as const,
                cardClass: 'border bg-gray-100 cursor-not-allowed opacity-75',
                badge: { text: 'Locked', class: 'bg-gray-200 text-gray-600' },
                textClass: 'text-gray-500'
            };
        }
    };

    // Handle concept click
    const handleConceptClick = (e: React.MouseEvent, concept: ConceptDto) => {
        const isAvailable = isConceptAvailable(concept);
        if (!isAvailable) {
            e.preventDefault();
            const unmetPrereqs = getUnmetPrerequisites(concept);
            setLockedConceptWarning({ concept, unmetPrereqs });
        }
    };

    // Handle warning modal confirmation
    const handleProceedToLockedConcept = () => {
        if (lockedConceptWarning) {
            navigate(`/courses/${courseId}/concepts/${lockedConceptWarning.concept.id}`);
            setLockedConceptWarning(null);
        }
    };

    if (loading) {
        return (
            <div className="p-6 space-y-6">
                <div className="text-gray-500">Loading course details...</div>
            </div>
        );
    }

    if (isGuest) {
        return (
            <div className="p-6 space-y-6">
                <nav className="text-sm text-gray-600 mb-4">
                    <Link to="/courses" className="hover:underline">
                        Courses
                    </Link>
                </nav>
                <div className="border rounded-lg p-8 text-center bg-yellow-50 border-yellow-200">
                    <h2 className="text-xl font-semibold mb-2">Learner Required</h2>
                    <p className="text-gray-600 mb-4">
                        Please select a learner from the dropdown in the header to view course details and concepts.
                    </p>
                    <p className="text-sm text-gray-500">
                        Guest mode allows browsing only. Select a learner identity to access course content.
                    </p>
                </div>
            </div>
        );
    }

    if (!course) {
        return (
            <div className="p-6 space-y-6">
                <nav className="text-sm text-gray-600 mb-4">
                    <Link to="/courses" className="hover:underline">
                        Courses
                    </Link>
                </nav>
                <div className="text-red-600">Course not found</div>
            </div>
        );
    }

    return (
        <div className="p-6 space-y-6">
            {/* Breadcrumb */}
            <nav className="text-sm text-gray-600">
                <Link to="/courses" className="hover:underline">
                    Courses
                </Link>
                <span className="mx-2">/</span>
                <span className="font-medium text-gray-900">{course.title}</span>
            </nav>

            {/* Header */}
            <div>
                <h1 className="text-2xl font-bold">{course.title}</h1>
                {course.description && (
                    <p className="text-gray-600 mt-2">{course.description}</p>
                )}
                <div className="text-sm text-gray-500 mt-2">
                    {concepts.length} {concepts.length === 1 ? 'concept' : 'concepts'}
                </div>
            </div>

            {/* Concepts Grid */}
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                {concepts
                    .slice()
                    .sort((a, b) => {
                        const statusA = getConceptStatus(a).status;
                        const statusB = getConceptStatus(b).status;

                        // Sort order: available -> mastered -> locked
                        const order = { available: 0, mastered: 1, locked: 2 };
                        return order[statusA] - order[statusB];
                    })
                    .map((concept) => {
                        const status = getConceptStatus(concept);
                        const isLocked = status.status === 'locked';
                        const unmetPrereqs = getUnmetPrerequisites(concept);

                    return (
                        <Link
                            key={concept.id}
                            to={`/courses/${courseId}/concepts/${concept.id}`}
                            onClick={(e) => handleConceptClick(e, concept)}
                            className={`rounded-lg p-5 transition-all space-y-3 ${status.cardClass}`}
                        >
                            <div className="flex items-start justify-between">
                                <div className="flex items-center gap-2 flex-1">
                                    {isLocked && (
                                        <svg className="w-5 h-5 text-gray-400 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                                            <path fillRule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clipRule="evenodd" />
                                        </svg>
                                    )}
                                    <h3 className={`font-semibold text-lg ${status.textClass}`}>{concept.title}</h3>
                                </div>
                                <div className={`text-xs px-2 py-1 rounded font-medium ml-2 flex-shrink-0 ${status.badge.class}`}>
                                    {status.badge.text}
                                </div>
                            </div>

                            {/* Prerequisites Info */}
                            {concept.prerequisites && concept.prerequisites.length > 0 && (
                                <div className="text-xs space-y-1">
                                    <div className="font-medium text-gray-600">Prerequisites:</div>
                                    <div className="flex flex-wrap gap-1">
                                        {concept.prerequisites.map(prereq => {
                                            const prereqConcept = concepts.find(c => c.id === prereq.conceptId);
                                            const isMet = prereqConcept && prereqConcept.mastery !== null && prereqConcept.mastery >= 0.7;
                                            return (
                                                <span
                                                    key={prereq.conceptId}
                                                    className={`px-2 py-0.5 rounded text-xs ${
                                                        isMet
                                                            ? 'bg-green-100 text-green-700'
                                                            : 'bg-red-100 text-red-700'
                                                    }`}
                                                >
                                                    {isMet ? '✓' : '✗'} {prereq.conceptName}
                                                </span>
                                            );
                                        })}
                                    </div>
                                </div>
                            )}

                            {/* Mastery Progress */}
                            <div className="space-y-2">
                                <div className={`flex items-center justify-between text-xs ${status.textClass}`}>
                                    <span>Mastery</span>
                                    <span className="font-medium">
                                        {concept.mastery !== null ? `${Math.round(concept.mastery * 100)}%` : 'Not started'}
                                    </span>
                                </div>
                                <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                                    <div
                                        className={`h-full transition-all ${
                                            status.status === 'mastered'
                                                ? 'bg-gradient-to-r from-green-500 to-emerald-500'
                                                : 'bg-gradient-to-r from-blue-500 to-purple-500'
                                        }`}
                                        style={{ width: `${concept.mastery !== null ? Math.round(concept.mastery * 100) : 0}%` }}
                                    />
                                </div>
                            </div>

                            {/* Status Icon */}
                            {status.status === 'mastered' && (
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

            {concepts.length === 0 && (
                <div className="text-center py-12 text-gray-500">
                    No concepts available for this course yet.
                </div>
            )}

            {/* Warning Modal for Locked Concepts */}
            {lockedConceptWarning && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 space-y-4">
                        <div className="flex items-start gap-3">
                            <div className="flex-shrink-0 w-10 h-10 rounded-full bg-yellow-100 flex items-center justify-center">
                                <svg className="w-6 h-6 text-yellow-600" fill="currentColor" viewBox="0 0 20 20">
                                    <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                                </svg>
                            </div>
                            <div className="flex-1">
                                <h3 className="text-lg font-semibold text-gray-900">Prerequisites Not Met</h3>
                                <p className="text-sm text-gray-600 mt-1">
                                    The concept <span className="font-semibold">"{lockedConceptWarning.concept.title}"</span> has prerequisites that you haven't mastered yet.
                                </p>
                            </div>
                        </div>

                        <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3">
                            <div className="text-sm font-medium text-yellow-900 mb-2">
                                You need to master these concepts first:
                            </div>
                            <ul className="list-disc list-inside text-sm text-yellow-800 space-y-1">
                                {lockedConceptWarning.unmetPrereqs.map((prereq, idx) => (
                                    <li key={idx}>{prereq}</li>
                                ))}
                            </ul>
                        </div>

                        <div className="text-sm text-gray-600">
                            We recommend mastering the prerequisites before proceeding. However, you can still access this concept if you wish.
                        </div>

                        <div className="flex gap-3 pt-2">
                            <button
                                onClick={() => setLockedConceptWarning(null)}
                                className="flex-1 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 font-medium transition-colors"
                            >
                                Go Back
                            </button>
                            <button
                                onClick={handleProceedToLockedConcept}
                                className="flex-1 px-4 py-2 bg-yellow-500 text-white rounded-lg hover:bg-yellow-600 font-medium transition-colors"
                            >
                                Proceed Anyway
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
