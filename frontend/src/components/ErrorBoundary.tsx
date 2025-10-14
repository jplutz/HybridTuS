/**
 * Error Boundary Component
 *
 * Catches React errors and displays a user-friendly message
 * instead of crashing the entire application (white page).
 *
 * This prevents errors in one component from breaking the whole app.
 */

import React, { Component, ReactNode } from 'react';
import { Link } from 'react-router-dom';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: React.ErrorInfo | null;
}

export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    // Update state so the next render will show the fallback UI
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    // Log the error to console for debugging
    console.error('[ErrorBoundary] Caught error:', error);
    console.error('[ErrorBoundary] Error info:', errorInfo);

    this.setState({
      error,
      errorInfo,
    });
  }

  handleReset = () => {
    // Reset the error boundary state
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });

    // Navigate to courses page
    window.location.href = '/courses';
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50 p-4">
          <div className="max-w-2xl w-full bg-white rounded-lg shadow-lg p-8">
            {/* Error Icon */}
            <div className="flex justify-center mb-6">
              <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center">
                <svg className="w-8 h-8 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
              </div>
            </div>

            {/* Error Message */}
            <div className="text-center mb-6">
              <h1 className="text-3xl font-bold text-gray-900 mb-2">
                Oops! Something went wrong
              </h1>
              <p className="text-gray-600 mb-4">
                We encountered an unexpected error. Don't worry, your data is safe.
              </p>
            </div>

            {/* Error Details (collapsed by default) */}
            <details className="mb-6 bg-gray-50 border border-gray-200 rounded-lg p-4">
              <summary className="cursor-pointer font-medium text-gray-700 hover:text-gray-900">
                Technical Details
              </summary>
              <div className="mt-3 space-y-2">
                <div className="text-sm">
                  <span className="font-semibold text-gray-700">Error:</span>
                  <pre className="mt-1 text-xs bg-red-50 text-red-900 p-2 rounded overflow-x-auto">
                    {this.state.error?.toString()}
                  </pre>
                </div>
                {this.state.errorInfo && (
                  <div className="text-sm">
                    <span className="font-semibold text-gray-700">Component Stack:</span>
                    <pre className="mt-1 text-xs bg-gray-100 text-gray-800 p-2 rounded overflow-x-auto max-h-40 overflow-y-auto">
                      {this.state.errorInfo.componentStack}
                    </pre>
                  </div>
                )}
              </div>
            </details>

            {/* Action Buttons */}
            <div className="flex flex-col sm:flex-row gap-3">
              <button
                onClick={this.handleReset}
                className="flex-1 bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 font-medium transition-colors"
              >
                Go to Home
              </button>
              <button
                onClick={() => window.location.reload()}
                className="flex-1 border border-gray-300 px-6 py-3 rounded-lg hover:bg-gray-50 font-medium transition-colors"
              >
                Reload Page
              </button>
            </div>

            {/* Help Text */}
            <div className="mt-6 pt-6 border-t border-gray-200 text-center">
              <p className="text-sm text-gray-600">
                If this problem persists, please contact support with the technical details above.
              </p>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
