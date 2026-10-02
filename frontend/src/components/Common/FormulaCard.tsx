import React from 'react';
import { ImpactScoreDTO } from '../../types/api';
import RiskBadge from './RiskBadge';
import { Calculator } from 'lucide-react';

interface FormulaCardProps {
  score?: ImpactScoreDTO;
  dStruct?: number;
  dBlast?: number;
}

export const FormulaCard: React.FC<FormulaCardProps> = ({
  score,
  dStruct = 0,
  dBlast = 0,
}) => {
  const actualDStruct = score?.dStruct ?? dStruct;
  const actualDBlast = score?.dBlast ?? dBlast;
  const actualSTotal = score?.sTotal ?? Math.min(1, 0.7 * actualDStruct + 0.3 * actualDBlast);
  const riskLevel = score?.riskLevel ?? (actualSTotal >= 0.75 ? 'CRITICAL' : actualSTotal >= 0.5 ? 'HIGH' : actualSTotal >= 0.2 ? 'MEDIUM' : 'LOW');

  const w1 = score?.breakdown?.w1 ?? 0.70;
  const w2 = score?.breakdown?.w2 ?? 0.30;
  const w1Val = w1 * actualDStruct;
  const w2Val = w2 * actualDBlast;

  const percentage = Math.round(actualSTotal * 100);

  return (
    <div className="rounded-lg border border-slate-800 bg-[#0c1220]/95 p-5 shadow-xl">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800/80 pb-4">
        <div className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 items-center justify-center rounded-md bg-blue-500/10 text-blue-400 border border-blue-500/20">
            <Calculator className="h-4 w-4" />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-slate-200">Composite Risk Scoring Engine</h3>
            <p className="text-xs text-slate-500 font-mono">
              S_total = min(1, w₁ × D_struct + w₂ × D_blast)
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <div className="text-right">
            <div className="text-xs text-slate-400">Calculated Risk Score</div>
            <div className="text-xl font-bold font-mono text-slate-100">{percentage}%</div>
          </div>
          <RiskBadge level={riskLevel} size="lg" />
        </div>
      </div>

      <div className="mt-5 grid grid-cols-1 md:grid-cols-2 gap-5">
        {/* Structural Impact */}
        <div className="rounded-md border border-slate-800/70 bg-[#111827]/60 p-4">
          <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
            <span className="font-semibold text-slate-300">Structural Impact (D_struct)</span>
            <span className="font-mono text-slate-400">Weight: w₁ = {w1.toFixed(2)}</span>
          </div>
          <div className="flex items-baseline justify-between mb-2 font-mono">
            <span className="text-xl font-bold text-slate-200">{actualDStruct.toFixed(2)}</span>
            <span className="text-xs text-slate-400">
              Contribution: +{w1Val.toFixed(2)}
            </span>
          </div>
          <div className="h-2 w-full rounded-full bg-slate-800 overflow-hidden">
            <div
              className="h-full bg-blue-500 rounded-full transition-all duration-500"
              style={{ width: `${Math.min(100, Math.max(0, actualDStruct * 100))}%` }}
            />
          </div>
          <div className="mt-2 text-[11px] text-slate-500">
            Evaluated from SGM breaking and non-breaking contract changes
          </div>
        </div>

        {/* Blast Radius Impact */}
        <div className="rounded-md border border-slate-800/70 bg-[#111827]/60 p-4">
          <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
            <span className="font-semibold text-slate-300">Blast Radius (D_blast)</span>
            <span className="font-mono text-slate-400">Weight: w₂ = {w2.toFixed(2)}</span>
          </div>
          <div className="flex items-baseline justify-between mb-2 font-mono">
            <span className="text-xl font-bold text-slate-200">{actualDBlast.toFixed(2)}</span>
            <span className="text-xs text-slate-400">
              Contribution: +{w2Val.toFixed(2)}
            </span>
          </div>
          <div className="h-2 w-full rounded-full bg-slate-800 overflow-hidden">
            <div
              className="h-full bg-amber-500 rounded-full transition-all duration-500"
              style={{ width: `${Math.min(100, Math.max(0, actualDBlast * 100))}%` }}
            />
          </div>
          <div className="mt-2 text-[11px] text-slate-500">
            Derived from discovered downstream consumer microservices
          </div>
        </div>
      </div>

      {/* Formula Step-by-Step Evaluation */}
      <div className="mt-4 rounded border border-slate-800/80 bg-black/40 px-4 py-3 font-mono text-xs text-slate-300">
        <div className="text-slate-500 text-[11px] uppercase tracking-wider mb-1">Calculation Breakdown</div>
        <div className="space-y-1">
          <div>
            <span className="text-slate-400">S_total</span> = min(1.00, ({w1.toFixed(2)} × {actualDStruct.toFixed(2)}) + ({w2.toFixed(2)} × {actualDBlast.toFixed(2)}))
          </div>
          <div>
            <span className="text-slate-400">S_total</span> = min(1.00, {w1Val.toFixed(2)} + {w2Val.toFixed(2)}) = <span className="font-bold text-blue-400">{actualSTotal.toFixed(2)}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default FormulaCard;
