import React from 'react';
import { ArrowRight, GitCompare, ShieldCheck, Network, Gauge } from 'lucide-react';

export const PipelineBanner: React.FC = () => {
  const steps = [
    {
      title: 'What Changed?',
      subtitle: 'SGM Governance & Diff',
      icon: <GitCompare className="h-4 w-4" />,
      color: 'text-blue-400 border-blue-500/30 bg-blue-950/40',
    },
    {
      title: 'Is It Secure?',
      subtitle: 'SAM Compliance Audit',
      icon: <ShieldCheck className="h-4 w-4" />,
      color: 'text-purple-400 border-purple-500/30 bg-purple-950/40',
    },
    {
      title: 'Who Is Affected?',
      subtitle: 'Dependency Scanning',
      icon: <Network className="h-4 w-4" />,
      color: 'text-amber-400 border-amber-500/30 bg-amber-950/40',
    },
    {
      title: 'How Significant?',
      subtitle: 'Blast Radius & Risk Score',
      icon: <Gauge className="h-4 w-4" />,
      color: 'text-emerald-400 border-emerald-500/30 bg-emerald-950/40',
    },
  ];

  return (
    <div className="rounded-lg border border-slate-800 bg-[#0c1220]/70 p-4">
      <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-3">
        Core Impact Analysis Pipeline
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {steps.map((step, idx) => (
          <div key={idx} className="relative flex items-center gap-3 rounded-md border border-slate-800/80 bg-[#111827]/40 p-3">
            <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md border ${step.color}`}>
              {step.icon}
            </div>
            <div className="min-w-0">
              <div className="text-xs font-semibold text-slate-200">{step.title}</div>
              <div className="text-[11px] text-slate-400 truncate">{step.subtitle}</div>
            </div>
            {idx < steps.length - 1 && (
              <ArrowRight className="hidden lg:block absolute -right-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-600 z-10" />
            )}
          </div>
        ))}
      </div>
    </div>
  );
};

export default PipelineBanner;
