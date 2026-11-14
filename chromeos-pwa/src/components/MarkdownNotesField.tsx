import React, { useState, useRef, useEffect, useMemo } from 'react';
import { ExternalLink, Mail, Phone, Eye, Edit3 } from 'lucide-react';
import {
  extractDetectedLinksFromNotes,
  stripTrailingPunctuation,
  type DetectedNotesLink
} from '../domain/powerUtils';

export const DetectedNotesLinksRow: React.FC<{
  links: DetectedNotesLink[];
  compact?: boolean;
}> = ({ links, compact = false }) => {
  if (links.length === 0) return null;
  return (
    <div
      className={`detected-links-row ${compact ? 'compact' : ''}`}
      onClick={(e) => e.stopPropagation()}
    >
      {links.map((link) => (
        <a
          key={link.targetUri}
          href={link.targetUri}
          target={link.type === 'WEB' ? '_blank' : undefined}
          rel={link.type === 'WEB' ? 'noopener noreferrer' : undefined}
          className={`detected-link-chip type-${link.type.toLowerCase()}`}
          title={link.targetUri}
        >
          {link.type === 'WEB' && <ExternalLink size={12} />}
          {link.type === 'EMAIL' && <Mail size={12} />}
          {link.type === 'PHONE' && <Phone size={12} />}
          <span>{link.displayLabel}</span>
        </a>
      ))}
    </div>
  );
};

function renderInlineMarkdown(text: string): React.ReactNode[] {
  const tokenRegex =
    /(`[^`]+`|\*\*[^*]+\*\*|__[^_]+__|\*[^*\n]+\*|https?:\/\/[^\s<>()]+|www\.[^\s<>()]+|[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})/g;
  const nodes: React.ReactNode[] = [];
  let lastIndex = 0;
  let keyIdx = 0;

  for (const match of text.matchAll(tokenRegex)) {
    const start = match.index ?? 0;
    if (start > lastIndex) {
      nodes.push(text.slice(lastIndex, start));
    }
    const token = match[0];
    if (token.startsWith('`') && token.endsWith('`') && token.length >= 2) {
      nodes.push(
        <code key={`md-${keyIdx++}`} className="md-inline-code">
          {token.slice(1, -1)}
        </code>
      );
    } else if (
      (token.startsWith('**') && token.endsWith('**')) ||
      (token.startsWith('__') && token.endsWith('__'))
    ) {
      nodes.push(<strong key={`md-${keyIdx++}`}>{token.slice(2, -2)}</strong>);
    } else if (token.startsWith('*') && token.endsWith('*') && token.length >= 3) {
      nodes.push(<em key={`md-${keyIdx++}`}>{token.slice(1, -1)}</em>);
    } else if (/^(https?:\/\/|www\.)/i.test(token)) {
      const cleanUrl = stripTrailingPunctuation(token);
      const trailing = token.slice(cleanUrl.length);
      const href = /^https?:\/\//i.test(cleanUrl) ? cleanUrl : `https://${cleanUrl}`;
      nodes.push(
        <a
          key={`md-${keyIdx++}`}
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          className="md-inline-link"
          onClick={(e) => e.stopPropagation()}
        >
          {cleanUrl}
        </a>
      );
      if (trailing) nodes.push(trailing);
    } else if (token.includes('@')) {
      const cleanEmail = stripTrailingPunctuation(token);
      const trailing = token.slice(cleanEmail.length);
      nodes.push(
        <a
          key={`md-${keyIdx++}`}
          href={`mailto:${cleanEmail}`}
          className="md-inline-link"
          onClick={(e) => e.stopPropagation()}
        >
          {cleanEmail}
        </a>
      );
      if (trailing) nodes.push(trailing);
    } else {
      nodes.push(token);
    }
    lastIndex = start + token.length;
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }
  return nodes;
}

export const FormattedMarkdownText: React.FC<{
  text: string;
  onToggleCheckboxLine?: (lineIndex: number) => void;
  onActivateEdit?: () => void;
}> = ({ text, onToggleCheckboxLine, onActivateEdit }) => {
  const lines = text.split('\n');
  return (
    <div
      className="md-rendered-body"
      onClick={() => {
        if (onActivateEdit) onActivateEdit();
      }}
    >
      {lines.map((line, idx) => {
        const trimmed = line.trimStart();
        const headingMatch = trimmed.match(/^(#{1,4})\s+(.*)$/);
        if (headingMatch) {
          const level = headingMatch[1].length;
          return (
            <div key={idx} className={`md-heading md-h${level}`}>
              {renderInlineMarkdown(headingMatch[2])}
            </div>
          );
        }

        const checkboxMatch = trimmed.match(/^[-*]\s+\[([ xX])\]\s*(.*)$/);
        if (checkboxMatch) {
          const checked = checkboxMatch[1].toLowerCase() === 'x';
          return (
            <div key={idx} className={`md-checkbox-line ${checked ? 'checked' : ''}`}>
              <input
                type="checkbox"
                checked={checked}
                onChange={(e) => {
                  e.stopPropagation();
                  onToggleCheckboxLine?.(idx);
                }}
                onClick={(e) => e.stopPropagation()}
              />
              <span>{renderInlineMarkdown(checkboxMatch[2])}</span>
            </div>
          );
        }

        const bulletMatch = trimmed.match(/^[-*•]\s+(.*)$/);
        if (bulletMatch) {
          return (
            <div key={idx} className="md-bullet-line">
              <span className="md-bullet-dot">•</span>
              <span>{renderInlineMarkdown(bulletMatch[1])}</span>
            </div>
          );
        }

        if (!trimmed) {
          return <div key={idx} className="md-empty-line" />;
        }

        return (
          <div key={idx} className="md-paragraph-line">
            {renderInlineMarkdown(line)}
          </div>
        );
      })}
    </div>
  );
};

function hasMarkdownFormatting(text: string): boolean {
  return /(^|\n)\s*(#{1,4}\s+|[-*•]\s+|[-*]\s+\[[ xX]\])|(\*\*[^*]+\*\*|__[^_]+__|`[^`]+`|\*[^*\n]+\*)/.test(
    text
  );
}

export const MarkdownNotesField: React.FC<{
  value: string;
  onChange: (next: string) => void;
  placeholder?: string;
  rows?: number;
  className?: string;
  showDetectedLinks?: boolean;
}> = ({
  value,
  onChange,
  placeholder = 'Notes',
  rows = 2,
  className = 'task-notes-editor',
  showDetectedLinks = true
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const detectedLinks = useMemo(() => extractDetectedLinksFromNotes(value), [value]);
  const showFormattedPreview = !isEditing && value.trim().length > 0 && hasMarkdownFormatting(value);

  useEffect(() => {
    if (isEditing && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [isEditing]);

  const insertSnippet = (prefix: string, suffix = '') => {
    const el = textareaRef.current;
    if (!el) {
      onChange(`${value}${value && !value.endsWith('\n') ? '\n' : ''}${prefix}${suffix}`);
      setIsEditing(true);
      return;
    }
    const start = el.selectionStart ?? value.length;
    const end = el.selectionEnd ?? value.length;
    const selected = value.slice(start, end);
    const replacement = `${prefix}${selected}${suffix}`;
    const nextValue = value.slice(0, start) + replacement + value.slice(end);
    onChange(nextValue);
    requestAnimationFrame(() => {
      if (textareaRef.current) {
        textareaRef.current.focus();
        const cursor = start + prefix.length + selected.length;
        textareaRef.current.setSelectionRange(cursor, cursor);
      }
    });
  };

  const handleToggleCheckboxLine = (lineIndex: number) => {
    const lines = value.split('\n');
    const target = lines[lineIndex];
    if (target == null) return;
    lines[lineIndex] = target.replace(/^(\s*[-*]\s+\[)([ xX])(\])/, (_, pre, mark, post) =>
      `${pre}${mark.toLowerCase() === 'x' ? ' ' : 'x'}${post}`
    );
    onChange(lines.join('\n'));
  };

  return (
    <div className="md-notes-container">
      {(isEditing || value.trim().length > 0) && (
        <div className="md-notes-toolbar" onMouseDown={(e) => e.preventDefault()}>
          <button
            type="button"
            className="md-fmt-btn"
            title="Heading (#)"
            onClick={() => insertSnippet('# ')}
          >
            H1
          </button>
          <button
            type="button"
            className="md-fmt-btn"
            title="Bold (**text**)"
            onClick={() => insertSnippet('**', '**')}
          >
            <strong>B</strong>
          </button>
          <button
            type="button"
            className="md-fmt-btn"
            title="Italic (*text*)"
            onClick={() => insertSnippet('*', '*')}
          >
            <em>I</em>
          </button>
          <button
            type="button"
            className="md-fmt-btn"
            title="Inline Code (`code`)"
            onClick={() => insertSnippet('`', '`')}
          >
            {'<>'}
          </button>
          <button
            type="button"
            className="md-fmt-btn"
            title="Bullet List (- )"
            onClick={() => insertSnippet('- ')}
          >
            • List
          </button>
          <button
            type="button"
            className="md-fmt-btn"
            title="Markdown Checkbox (- [ ] )"
            onClick={() => insertSnippet('- [ ] ')}
          >
            ☑
          </button>
          {hasMarkdownFormatting(value) && (
            <button
              type="button"
              className={`md-fmt-btn preview-toggle ${showFormattedPreview ? 'active' : ''}`}
              title={showFormattedPreview ? 'Edit Markdown' : 'Preview Markdown'}
              onClick={() => setIsEditing((prev) => !prev)}
            >
              {showFormattedPreview ? <Edit3 size={11} /> : <Eye size={11} />}
            </button>
          )}
        </div>
      )}

      {showFormattedPreview ? (
        <FormattedMarkdownText
          text={value}
          onToggleCheckboxLine={handleToggleCheckboxLine}
          onActivateEdit={() => setIsEditing(true)}
        />
      ) : (
        <textarea
          ref={textareaRef}
          className={className}
          placeholder={placeholder}
          value={value}
          rows={Math.max(rows, Math.min(10, value.split('\n').length))}
          onFocus={() => setIsEditing(true)}
          onBlur={() => setIsEditing(false)}
          onChange={(e) => onChange(e.target.value)}
        />
      )}

      {showDetectedLinks && <DetectedNotesLinksRow links={detectedLinks} />}
    </div>
  );
};
