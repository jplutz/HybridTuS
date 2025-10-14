import React, { useState, useEffect } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';

interface EnhancedMarkdownProps {
  content: string;
}

/**
 * Enhanced markdown renderer with support for:
 * - Code blocks with syntax highlighting
 * - Figures with descriptions (![figure](path)\n*caption*)
 * - Standard markdown formatting
 * - Clickable images with lightbox view
 */
export default function EnhancedMarkdown({ content }: EnhancedMarkdownProps) {
  const [lightboxImage, setLightboxImage] = useState<{ src: string; alt: string } | null>(null);

  // Handle Escape key to close lightbox
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && lightboxImage) {
        setLightboxImage(null);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [lightboxImage]);
  const lines = content.split('\n');
  const elements: JSX.Element[] = [];
  let idx = 0;

  while (idx < lines.length) {
    const line = lines[idx];

    // Code block detection (```language)
    if (line.trim().startsWith('```')) {
      const language = line.trim().slice(3).trim() || 'text';
      const codeLines: string[] = [];
      idx++;

      // Collect code lines until closing ```
      while (idx < lines.length && !lines[idx].trim().startsWith('```')) {
        codeLines.push(lines[idx]);
        idx++;
      }

      elements.push(
        <div key={idx} className="my-4">
          <SyntaxHighlighter
            language={language}
            style={vscDarkPlus}
            customStyle={{
              borderRadius: '0.5rem',
              padding: '1rem',
              fontSize: '0.9rem',
            }}
          >
            {codeLines.join('\n')}
          </SyntaxHighlighter>
        </div>
      );

      idx++; // Skip closing ```
      continue;
    }

    // Image/Figure detection (standard markdown: ![alt](path) followed by optional *caption*)
    if (line.trim().startsWith('![')) {
      const imageMatch = line.match(/!\[([^\]]*)\]\(([^)]+)\)/);
      if (imageMatch) {
        const altText = imageMatch[1];
        const imagePath = imageMatch[2];
        let caption = '';

        // Check if next line is a caption (starts with *)
        if (idx + 1 < lines.length && lines[idx + 1].trim().startsWith('*')) {
          caption = lines[idx + 1].trim().slice(1, -1); // Remove * from both ends
          idx++; // Skip caption line
        }

        elements.push(
          <div key={idx} className="my-6 border rounded-lg p-4 bg-gray-50">
            <div
              className="bg-white rounded-lg border-2 border-gray-300 p-4 mb-3 flex items-center justify-center cursor-pointer hover:border-blue-400 transition-colors group relative"
              onClick={() => setLightboxImage({ src: imagePath, alt: altText })}
              title="Click to enlarge"
            >
              <img
                src={imagePath}
                alt={altText}
                className="max-w-full h-auto rounded"
                onError={(e) => {
                  // Show placeholder if image fails to load
                  const target = e.target as HTMLImageElement;
                  target.style.display = 'none';
                  const parent = target.parentElement;
                  if (parent) {
                    parent.innerHTML = `
                      <div class="text-gray-400 text-center p-8">
                        <svg class="w-16 h-16 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                        <div class="text-sm font-mono">${imagePath}</div>
                        <div class="text-xs mt-1">Image not found</div>
                      </div>
                    `;
                  }
                }}
              />
              {/* Zoom indicator overlay */}
              <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity bg-black/10 rounded-lg">
                <div className="bg-white/90 rounded-full p-2 shadow-lg">
                  <svg className="w-6 h-6 text-gray-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v6m3-3H7" />
                  </svg>
                </div>
              </div>
            </div>
            {(caption || altText) && (
              <div className="text-sm text-gray-700 italic text-center">
                {caption || altText}
              </div>
            )}
          </div>
        );

        idx++;
        continue;
      }
    }

    // Heading 1
    if (line.startsWith('# ')) {
      elements.push(
        <h1 key={idx} className="text-2xl font-bold mt-6 mb-3">
          {line.substring(2)}
        </h1>
      );
      idx++;
      continue;
    }

    // Heading 2
    if (line.startsWith('## ')) {
      elements.push(
        <h2 key={idx} className="text-xl font-semibold mt-5 mb-2">
          {line.substring(3)}
        </h2>
      );
      idx++;
      continue;
    }

    // Heading 3
    if (line.startsWith('### ')) {
      elements.push(
        <h3 key={idx} className="text-lg font-semibold mt-4 mb-2">
          {line.substring(4)}
        </h3>
      );
      idx++;
      continue;
    }

    // Bullet point
    if (line.startsWith('- ')) {
      elements.push(
        <li key={idx} className="ml-4 mb-1">
          {renderInlineFormatting(line.substring(2))}
        </li>
      );
      idx++;
      continue;
    }

    // Bold paragraph
    if (line.startsWith('**') && line.endsWith('**')) {
      elements.push(
        <p key={idx} className="font-bold mb-2">
          {line.slice(2, -2)}
        </p>
      );
      idx++;
      continue;
    }

    // Empty line
    if (line.trim() === '') {
      elements.push(<br key={idx} />);
      idx++;
      continue;
    }

    // Regular paragraph
    elements.push(
      <p key={idx} className="mb-2">
        {renderInlineFormatting(line)}
      </p>
    );
    idx++;
  }

  return (
    <>
      <article className="prose prose-lg max-w-none pb-16">{elements}</article>

      {/* Lightbox Modal */}
      {lightboxImage && (
        <div
          className="fixed inset-0 z-[9999] bg-black/90 flex items-center justify-center p-4"
          onClick={() => setLightboxImage(null)}
        >
          <div className="relative max-w-[95vw] max-h-[95vh]">
            {/* Close button */}
            <button
              onClick={() => setLightboxImage(null)}
              className="absolute -top-12 right-0 text-white hover:text-gray-300 transition-colors"
              title="Close (Esc)"
            >
              <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>

            {/* Image */}
            <img
              src={lightboxImage.src}
              alt={lightboxImage.alt}
              className="max-w-full max-h-[90vh] w-auto h-auto rounded-lg shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            />

            {/* Caption */}
            {lightboxImage.alt && (
              <div className="absolute -bottom-12 left-0 right-0 text-center text-white text-sm">
                {lightboxImage.alt}
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}

/**
 * Render inline formatting like **bold** and `code`
 */
function renderInlineFormatting(text: string): React.ReactNode {
  const parts: React.ReactNode[] = [];
  let current = '';
  let i = 0;

  while (i < text.length) {
    // Bold text: **text**
    if (text[i] === '*' && text[i + 1] === '*') {
      if (current) {
        parts.push(current);
        current = '';
      }
      i += 2;
      let boldText = '';
      while (i < text.length && !(text[i] === '*' && text[i + 1] === '*')) {
        boldText += text[i];
        i++;
      }
      parts.push(<strong key={i}>{boldText}</strong>);
      i += 2;
      continue;
    }

    // Inline code: `code`
    if (text[i] === '`') {
      if (current) {
        parts.push(current);
        current = '';
      }
      i++;
      let codeText = '';
      while (i < text.length && text[i] !== '`') {
        codeText += text[i];
        i++;
      }
      parts.push(
        <code key={i} className="px-1.5 py-0.5 bg-gray-100 text-gray-800 rounded text-sm font-mono">
          {codeText}
        </code>
      );
      i++;
      continue;
    }

    current += text[i];
    i++;
  }

  if (current) {
    parts.push(current);
  }

  return parts.length > 0 ? parts : text;
}
