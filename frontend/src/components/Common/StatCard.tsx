import React, { ReactNode } from 'react';

interface StatCardProps {
  label: string;
  value: string | number;
  subtext?: string;
  icon?: ReactNode;
  variant?: 'default' | 'danger' | 'warning' | 'info' | 'success';
}

export const StatCard: React.FC<StatCardProps> = ({
  label,
  value,
  subtext,
  icon,
  variant = 'default',
}) => {
  const borderColors = {
    default: 'border-slate-800 hover:border-slate-700',
    danger: 'border-red-900/40 hover:border-red-800/60',
    warning: 'border-amber-900/40 hover:border-amber-800/60',
    info: 'border-blue-900/40 hover:border-blue-800/60',
    success: 'border-emerald-900/40 hover:border-emerald-800/60',
  }[variant];

  const valueColors = {
    default: 'text-slate-100',
    danger: 'text-red-400',
    warning: 'text-amber-400',
    info: 'text-blue-400',
    success: 'text-emerald-400',
  }[variant];

  return (
    <div className={`p-4 rounded-lg bg-[#0c1220]/90 border ${borderColors} transition-all`}>
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">{label}</span>
        {icon && <span className="text-slate-500">{icon}</span>}
      </div>
      <div className={`mt-2 text-2xl font-bold font-mono ${valueColors}`}>{value}</div>
      {subtext && <div className="mt-1 text-xs text-slate-500">{subtext}</div>}
    </div>
  );
};

export default StatCard;
