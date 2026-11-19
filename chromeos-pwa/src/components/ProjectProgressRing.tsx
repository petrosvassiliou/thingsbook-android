import React from 'react';
import { Check } from 'lucide-react';

interface ProjectProgressRingProps {
  completed: number;
  total: number;
  size?: number;
  isProjectCompleted?: boolean;
  showCount?: boolean;
}

/**
 * Thingsbook 3.0-alpha.2 — Horizontal Linear Progress Pill
 * Replaces circular pie/ring indicators with a clean, copyright-distinct
 * rounded linear progress bar + optional tabular count.
 */
export const ProjectProgressRing: React.FC<ProjectProgressRingProps> = ({
  completed,
  total,
  size = 16,
  isProjectCompleted = false,
  showCount = false
}) => {
  const ratio = isProjectCompleted
    ? 1
    : total > 0
      ? Math.min(1, Math.max(0, completed / total))
      : 0;
  const isLarge = size >= 22;
  const trackWidth = isLarge ? 68 : size >= 15 ? 34 : 26;
  const trackHeight = isLarge ? 6 : 4.5;

  if (ratio >= 1 && isProjectCompleted) {
    return (
      <span className="project-linear-done-badge" aria-hidden="true">
        <Check size={isLarge ? 12 : 10} strokeWidth={3} />
      </span>
    );
  }

  return (
    <span className="project-linear-progress" aria-hidden="true">
      <span
        className="project-linear-track"
        style={{ width: trackWidth, height: trackHeight }}
      >
        <span
          className="project-linear-fill"
          style={{ width: `${Math.round(ratio * 100)}%` }}
        />
      </span>
      {(showCount || isLarge) && (
        <span className="project-linear-label">
          {completed}/{total}
        </span>
      )}
    </span>
  );
};
