import React from 'react';

interface ProjectProgressRingProps {
  completed: number;
  total: number;
  size?: number;
  isProjectCompleted?: boolean;
}

/**
 * Thingsbook 3.0 — Material 3 Expressive Open-Track Progress Arc
 * Replaces the legacy pie-slice wedge with a clean rounded-cap Sky Light Blue
 * circular progress arc and a filled Sky Light Blue checkmark badge at 100%.
 */
export const ProjectProgressRing: React.FC<ProjectProgressRingProps> = ({
  completed,
  total,
  size = 16,
  isProjectCompleted = false
}) => {
  const ratio = isProjectCompleted ? 1 : total > 0 ? Math.min(1, Math.max(0, completed / total)) : 0;
  const strokeWidth = Math.max(1.8, size * 0.14);
  const r = (size - strokeWidth - 1.2) / 2;
  const cx = size / 2;
  const cy = size / 2;
  const circumference = 2 * Math.PI * r;
  const dashOffset = circumference * (1 - ratio);

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      aria-hidden="true"
      style={{ flexShrink: 0 }}
    >
      {ratio >= 1 ? (
        <>
          <circle cx={cx} cy={cy} r={r + strokeWidth * 0.35} fill="var(--things-blue)" />
          <path
            d={`M ${size * 0.30} ${size * 0.52} L ${size * 0.44} ${size * 0.66} L ${size * 0.72} ${size * 0.36}`}
            fill="none"
            stroke="#FFFFFF"
            strokeWidth={Math.max(1.5, size * 0.12)}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </>
      ) : (
        <>
          <circle
            cx={cx}
            cy={cy}
            r={r}
            fill="none"
            stroke="var(--things-blue)"
            strokeWidth={strokeWidth}
            opacity={0.22}
          />
          {ratio > 0 && (
            <circle
              cx={cx}
              cy={cy}
              r={r}
              fill="none"
              stroke="var(--things-blue)"
              strokeWidth={strokeWidth}
              strokeDasharray={circumference}
              strokeDashoffset={dashOffset}
              strokeLinecap="round"
              transform={`rotate(-90 ${cx} ${cy})`}
            />
          )}
        </>
      )}
    </svg>
  );
};
