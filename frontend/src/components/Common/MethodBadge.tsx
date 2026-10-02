import React from 'react';

interface MethodBadgeProps {
  method?: string;
  className?: string;
}

export const MethodBadge: React.FC<MethodBadgeProps> = ({ method = 'GET', className = '' }) => {
  const m = method.toUpperCase();

  const colors: Record<string, string> = {
    GET: 'bg-blue-950/70 border-blue-500/40 text-blue-400',
    POST: 'bg-emerald-950/70 border-emerald-500/40 text-emerald-400',
    PUT: 'bg-amber-950/70 border-amber-500/40 text-amber-400',
    DELETE: 'bg-red-950/70 border-red-500/40 text-red-400',
    PATCH: 'bg-purple-950/70 border-purple-500/40 text-purple-400',
    OPTIONS: 'bg-slate-800 border-slate-700 text-slate-400',
    HEAD: 'bg-slate-800 border-slate-700 text-slate-400',
  };

  const style = colors[m] || 'bg-slate-800 border-slate-700 text-slate-300';

  return (
    <span
      className={`inline-flex items-center justify-center font-mono font-bold text-[11px] px-2 py-0.5 rounded border ${style} ${className}`}
    >
      {m}
    </span>
  );
};

export default MethodBadge;
