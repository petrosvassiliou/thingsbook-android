import React from 'react';

export interface InProgressIconProps {
  size?: number;
  className?: string;
  style?: React.CSSProperties;
}

export const InProgressIcon: React.FC<InProgressIconProps> = ({
  size = 15,
  className,
  style
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 16 16"
    fill="none"
    aria-hidden="true"
    className={className}
    style={{ flexShrink: 0, ...style }}
  >
    <circle
      cx="8"
      cy="8"
      r="6.35"
      stroke="currentColor"
      strokeWidth="1.65"
    />
    <path
      d="M 8 1.65 A 6.35 6.35 0 0 0 8 14.35 Z"
      fill="currentColor"
    />
  </svg>
);
