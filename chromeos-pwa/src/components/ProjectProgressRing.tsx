import React from 'react';

interface ProjectProgressRingProps {
  completed: number;
  total: number;
  size?: number;
  isProjectCompleted?: boolean;
}

export const ProjectProgressRing: React.FC<ProjectProgressRingProps> = ({
  completed,
  total,
  size = 16,
  isProjectCompleted = false
}) => {
  const ratio = isProjectCompleted ? 1 : total > 0 ? Math.min(1, Math.max(0, completed / total)) : 0;
  const r = (size - 3) / 2;
  const cx = size / 2;
  const cy = size / 2;

  // Render an inner pie sector matching Things 3 project progress indicator
  const pieRadius = r - 1.6;
  let piePath = '';
  if (ratio > 0 && ratio < 1) {
    const angle = ratio * Math.PI * 2 - Math.PI / 2;
    const x = cx + pieRadius * Math.cos(angle);
    const y = cy + pieRadius * Math.sin(angle);
    const largeArc = ratio > 0.5 ? 1 : 0;
    piePath = `M ${cx} ${cy} L ${cx} ${cy - pieRadius} A ${pieRadius} ${pieRadius} 0 ${largeArc} 1 ${x} ${y} Z`;
  }

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      aria-hidden="true"
      style={{ flexShrink: 0 }}
    >
      <circle
        cx={cx}
        cy={cy}
        r={r}
        fill="none"
        stroke="var(--things-blue)"
        strokeWidth="1.5"
        opacity={0.85}
      />
      {ratio >= 1 ? (
        <circle cx={cx} cy={cy} r={pieRadius} fill="var(--things-blue)" />
      ) : piePath ? (
        <path d={piePath} fill="var(--things-blue)" />
      ) : null}
    </svg>
  );
};
