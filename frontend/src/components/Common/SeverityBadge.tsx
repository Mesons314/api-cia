import React from 'react';
import { ViolationSeverity } from '../../types/api';

interface SeverityBadgeProps {
  severity: ViolationSeverity | string;
}

export const SeverityBadge: React.FC<SeverityBadgeProps> = ({ severity }) => {
  const s = (severity || 'INFO').toUpperCase();

  const styles: Record<string, { bg: string; text: string; border: string }> = {
    BREAKING: { bg: 'bg-red-500/10', text: 'text-red-400', border: 'border-red-500/30' },
    CRITICAL: { bg: 'bg-red-500/10', text: 'text-red-400', border: 'border-red-500/30' },
    HIGH: { bg: 'bg-orange-500/10', text: 'text-orange-400', border: 'border-orange-500/30' },
    WARNING: { bg: 'bg-amber-500/10', text: 'text-amber-400', border: 'border-amber-500/30' },
    MEDIUM: { bg: 'bg-amber-500/10', text: 'text-amber-400', border: 'border-amber-500/30' },
    INFO: { bg: 'bg-blue-500/10', text: 'text-blue-400', border: 'border-blue-500/30' },
    LOW: { bg: 'bg-emerald-500/10', text: 'text-emerald-400', border: 'border-emerald-500/30' },
  };

  const current = styles[s] || styles.INFO;

  return (
    <span
      className={`inline-flex items-center text-[10px] font-mono font-semibold px-2 py-0.5 rounded border uppercase tracking-wider ${current.bg} ${current.text} ${current.border}`}
    >
      {s}
    </span>
  );
};

export default SeverityBadge;
