/**
 * Shared ChatBar Component
 * Collapsible chat interface for concept learning and practice mode
 */

import React, { useState, useEffect, useRef } from 'react';
import { api } from '@/adapters';
import EnhancedMarkdown from '@/components/content/EnhancedMarkdown';

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  timestamp: number;
}

interface ChatBarProps {
  conceptId: string | number;
  contextPhase?: string | null;
  exerciseContext?: any | null;
  currentContent?: string;
}

export default function ChatBar({
  conceptId,
  contextPhase = null,
  exerciseContext = null,
  currentContent
}: ChatBarProps) {
  const [question, setQuestion] = useState('');
  const [loading, setLoading] = useState(false);
  const [isExpanded, setIsExpanded] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [error, setError] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Listen for activity discussion events
  useEffect(() => {
    const handleActivityDiscussion = (event: Event) => {
      const customEvent = event as CustomEvent;
      const { activityTitle, userThoughts } = customEvent.detail;

      // Create formatted message with context
      const contextMessage = `I'm working on the activity "${activityTitle}". Here are my thoughts:\n\n${userThoughts}\n\nCan you provide feedback and discuss this with me?`;

      // Set the question and expand the chat
      setQuestion(contextMessage);
      setIsExpanded(true);

      // Auto-send the message after a brief delay to allow UI update
      setTimeout(() => {
        handleAskWithCustomMessage(contextMessage);
      }, 100);
    };

    window.addEventListener('openChatWithContext', handleActivityDiscussion);

    return () => {
      window.removeEventListener('openChatWithContext', handleActivityDiscussion);
    };
  }, [conceptId]);

  // Auto-scroll to bottom when messages change or loading state changes
  useEffect(() => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, loading]);

  const handleAskWithCustomMessage = async (customMessage: string) => {
    if (!customMessage.trim() || !conceptId) return;

    const userMessage: ChatMessage = {
      role: 'user',
      text: customMessage.trim(),
      timestamp: Date.now()
    };

    // Add user message immediately
    setMessages(prev => [...prev, userMessage]);
    setQuestion('');
    setLoading(true);
    setError(null);

    try {
      const response = await api.sendChatMessage({
        conceptId: typeof conceptId === 'string' ? parseInt(conceptId, 10) : conceptId,
        contextPhase,
        userText: userMessage.text,
        exerciseContext
      });

      const assistantMessage: ChatMessage = {
        role: 'assistant',
        text: response.text,
        timestamp: Date.now()
      };

      setMessages(prev => [...prev, assistantMessage]);
    } catch (err) {
      console.error('[ChatBar] Error:', err);
      setError(err instanceof Error ? err.message : 'Failed to get response');

      // Add error message to chat
      const errorMessage: ChatMessage = {
        role: 'assistant',
        text: "I'm having trouble responding right now. Please try again.",
        timestamp: Date.now()
      };
      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setLoading(false);
    }
  };

  const handleAsk = async () => {
    if (!question.trim()) return;
    await handleAskWithCustomMessage(question);
  };

  // Collapsed view - minimal bar with indicator
  if (!isExpanded) {
    return (
      <button
        onClick={() => setIsExpanded(true)}
        className="w-full flex items-center justify-between px-6 py-3 bg-blue-50 hover:bg-blue-100 border-t border-blue-200 transition-colors"
      >
        <div className="flex items-center gap-3">
          <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
          </svg>
          <div className="text-left">
            <div className="font-semibold text-blue-900 text-sm">AI Learning Assistant</div>
            <div className="text-xs text-blue-700">
              {contextPhase === 'Practice Exercise' ? 'Get help with this exercise' : 'Ask questions about this concept'}
            </div>
          </div>
        </div>
        <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
        </svg>
      </button>
    );
  }

  // Expanded view - full chat interface with message history
  return (
    <div className="border-t border-gray-300 bg-white shadow-lg max-h-[500px] flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-gray-200 bg-blue-50">
        <div className="flex items-center gap-2">
          <svg className="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
          </svg>
          <div>
            <h3 className="font-semibold text-gray-900">AI Learning Assistant</h3>
            <div className="text-xs text-gray-600">Ask questions • Get help • Discuss your work</div>
          </div>
          {messages.length > 0 && (
            <button
              onClick={() => setMessages([])}
              className="text-xs text-gray-500 hover:text-gray-700 ml-2"
              title="Clear chat history"
            >
              Clear
            </button>
          )}
        </div>
        <button
          onClick={() => setIsExpanded(false)}
          className="text-gray-500 hover:text-gray-700 p-1"
          title="Collapse chat"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>
      </div>

      {/* Message History */}
      {messages.length > 0 && (
        <div className="flex-1 overflow-y-auto p-4 space-y-3 min-h-[200px] max-h-[300px]">
          {messages.map((msg, idx) => (
            <div
              key={idx}
              className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              <div
                className={`max-w-[80%] rounded-lg px-4 py-2 ${
                  msg.role === 'user'
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-50 text-gray-900 border border-gray-200'
                }`}
              >
                {msg.role === 'user' ? (
                  <div className="text-sm whitespace-pre-wrap">{msg.text}</div>
                ) : (
                  <div className="text-sm prose prose-sm max-w-none">
                    <EnhancedMarkdown content={msg.text} />
                  </div>
                )}
                <div
                  className={`text-xs mt-1 ${
                    msg.role === 'user' ? 'text-blue-200' : 'text-gray-500'
                  }`}
                >
                  {new Date(msg.timestamp).toLocaleTimeString()}
                </div>
              </div>
            </div>
          ))}
          {loading && (
            <div className="flex justify-start">
              <div className="bg-gray-100 text-gray-900 border border-gray-200 rounded-lg px-4 py-2">
                <div className="flex items-center gap-2">
                  <div className="flex gap-1">
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></div>
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></div>
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></div>
                  </div>
                  <span className="text-xs text-gray-500">Thinking...</span>
                </div>
              </div>
            </div>
          )}
          {/* Auto-scroll anchor */}
          <div ref={messagesEndRef} />
        </div>
      )}

      {/* Input Area */}
      <div className="p-4 border-t border-gray-200">
        {error && (
          <div className="mb-2 text-xs text-red-600 bg-red-50 border border-red-200 rounded px-2 py-1">
            {error}
          </div>
        )}
        <div className="flex gap-2">
          <input
            type="text"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleAsk()}
            placeholder={
              contextPhase === 'Practice Exercise'
                ? 'Ask about this exercise...'
                : 'Ask about this concept...'
            }
            disabled={loading}
            className="flex-1 border rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500"
          />
          <button
            onClick={handleAsk}
            disabled={loading || !question.trim()}
            className="px-6 py-2 rounded-lg bg-blue-600 text-white font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {loading ? '...' : 'Ask'}
          </button>
        </div>
        <div className="text-xs text-gray-500 mt-2 flex items-center gap-1">
          <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
          </svg>
          {contextPhase === 'Practice Exercise'
            ? "AI assistant providing hints and guidance. Responses may not always be accurate."
            : 'AI assistant. Responses are context-aware but may not always be accurate.'}
        </div>
      </div>
    </div>
  );
}
