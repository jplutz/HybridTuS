/**
 * HybridTuS Frontend Application
 * Clean routing structure for course → concept → session flow
 */
import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { LearnerProvider } from "@/contexts/LearnerContext";
import ErrorBoundary from "@/components/ErrorBoundary";
import AppLayout from "@/components/layout/AppLayout";
import Courses from "@/pages/Courses";
import CourseDetail from "@/pages/CourseDetail";
import ConceptSession from "@/pages/ConceptSession";
import CreateLearner from "@/pages/CreateLearner";
import AdminControlCenter from "@/pages/admin/AdminControlCenter";
import PracticeMode from "@/pages/PracticeMode";

export default function App() {
  return (
    <ErrorBoundary>
      <LearnerProvider>
        <Routes>
          <Route element={<AppLayout />}>
            {/* Root redirects to courses list */}
            <Route path="/" element={<Navigate to="/courses" replace />} />

            {/* Learner management */}
            <Route path="/learners/new" element={<CreateLearner />} />

            {/* Admin Control Center */}
            <Route path="/admin" element={<AdminControlCenter />} />

            {/* Course list page */}
            <Route path="/courses" element={<Courses />} />

            {/* Course detail page with concepts */}
            <Route path="/courses/:courseId" element={<CourseDetail />} />

            {/* Concept learning session */}
            <Route
              path="/courses/:courseId/concepts/:conceptId"
              element={<ConceptSession />}
            />

            {/* Practice mode */}
            <Route
              path="/courses/:courseId/concepts/:conceptId/practice"
              element={<PracticeMode />}
            />
          </Route>
        </Routes>
      </LearnerProvider>
    </ErrorBoundary>
  );
}
