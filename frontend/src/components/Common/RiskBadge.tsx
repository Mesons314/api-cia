import React from 'react';
import { RiskLevel } from '../../types/api';

interface RiskBadgeProps {
  level: RiskLevel | string;
  size?: 'sm' | 'md' | 'lg';
}

export const RiskBadge: React.FC<RiskBadgeProps> = ({ level, size = 'md' }) => {
  const normalizedLevel = (level || 'LOW').toUpperCase();

  const config = {
    LOW: {
      bg: 'bg-emerald-950/60',
      border: 'border-emerald-500/30',
      text: 'text-emerald-400',
      dot: 'bg-emerald-400',
    },
    MEDIUM: {
      bg: 'bg-amber-950/60',
      border: 'border-amber-500/30',
      text: 'text-amber-400',
      dot: 'bg-amber-400',
    },
    HIGH: {
      bg: 'bg-orange-950/60',
      border: 'border-orange-500/30',
      text: 'text-orange-400',
      dot: 'bg-orange-400',
    },
    CRITICAL: {
      bg: 'bg-red-950/60',
      border: 'border-red-500/30',
      text: 'text-red-400',
      dot: 'bg-red-400',
    },
  }[normalizedLevel] || {
    bg: 'bg-slate-800',
    border: 'border-slate-700',
    text: 'text-slate-400',
    dot: 'bg-slate-400',
  };

  const sizeClasses = {
    sm: 'text-xs px-2 py-0.5 gap-1.5',
    md: 'text-xs px-2.5 py-1 gap-1.5 font-medium',
    lg: 'text-sm px-3.5 py-1.5 gap-2 font-semibold',
  }[size];

  return (
    <span
      className={`inline-flex items-center rounded-md border font-mono tracking-wider ${config.bg} ${config.border} ${config.text} ${sizeClasses}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${config.dot}`} />
      {normalizedLevel}
    </span>
  );
};

export default RiskBadge;
