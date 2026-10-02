import React from 'react';
import { X, ShieldAlert, AlertTriangle, ArrowRight, ExternalLink } from 'lucide-react';
import SeverityBadge from './SeverityBadge';
import MethodBadge from './MethodBadge';
import { ViolationDTO, SecurityAlertDTO } from '../../types/api';

interface FindingModalProps {
  sgmFinding?: ViolationDTO | null;
  samFinding?: SecurityAlertDTO | null;
  onClose: () => void;
}

export const FindingModal: React.FC<FindingModalProps> = ({
  sgmFinding,
  samFinding,
  onClose,
}) => {
  if (!sgmFinding && !samFinding) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
      <div className="relative w-full max-w-2xl rounded-xl border border-slate-700/80 bg-[#0c1220] shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 bg-[#111827]/70 px-6 py-4">
          <div className="flex items-center gap-3">
            {sgmFinding ? (
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-amber-500/10 text-amber-400 border border-amber-500/20">
                <AlertTriangle className="h-4 w-4" />
              </div>
            ) : (
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-red-500/10 text-red-400 border border-red-500/20">
                <ShieldAlert className="h-4 w-4" />
              </div>
            )}
            <div>
              <div className="flex items-center gap-2">
                <span className="font-mono font-bold text-sm text-slate-200">
                  {sgmFinding?.ruleId || samFinding?.checkId}
                </span>
                <SeverityBadge severity={sgmFinding?.severity || samFinding?.severity || 'INFO'} />
              </div>
              <p className="text-xs text-slate-400">
                {sgmFinding ? 'Static Governance Module (SGM) Rule' : 'Security Analysis Module (SAM) Check'}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-slate-200 transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
          {/* Target Endpoint or Location */}
          <div>
            <label className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
              Target / Endpoint
            </label>
            <div className="mt-1 flex items-center gap-2 p-2.5 rounded-lg border border-slate-800 bg-[#111827]/90 font-mono text-xs text-slate-200">
              {sgmFinding?.endpoint && (
                <>
                  <MethodBadge
                    method={
                      sgmFinding.endpoint.split(' ')[0] ||
                      sgmFinding.method ||
                      'GET'
                    }
                  />
                  <span>
                    {sgmFinding.endpoint.includes(' ')
                      ? sgmFinding.endpoint.split(' ').slice(1).join(' ')
                      : sgmFinding.endpoint}
                  </span>
                </>
              )}
              {samFinding && (
                <span>
                  {samFinding.endpoint || samFinding.location || samFinding.controller || 'Global Specification'}
                </span>
              )}
            </div>
          </div>

          {/* Description / Message */}
          <div>
            <label className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
              Violation Description
            </label>
            <div className="mt-1 p-3 rounded-lg border border-slate-800 bg-[#111827]/50 text-sm text-slate-200">
              {sgmFinding?.message || samFinding?.description}
            </div>
          </div>

          {/* Diff comparison if available */}
          {(sgmFinding?.oldValue || sgmFinding?.newValue) && (
            <div>
              <label className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                Contract Change Comparison
              </label>
              <div className="mt-1 grid grid-cols-1 md:grid-cols-2 gap-3">
                <div className="p-3 rounded-lg border border-red-900/30 bg-red-950/20 font-mono text-xs">
                  <div className="text-[11px] text-red-400 font-semibold mb-1">Previous Contract</div>
                  <div className="text-slate-300 break-all">{sgmFinding.oldValue || '—'}</div>
                </div>
                <div className="p-3 rounded-lg border border-emerald-900/30 bg-emerald-950/20 font-mono text-xs">
                  <div className="text-[11px] text-emerald-400 font-semibold mb-1">Current Contract</div>
                  <div className="text-slate-300 break-all">{sgmFinding.newValue || '—'}</div>
                </div>
              </div>
            </div>
          )}

          {/* Remediation if available (SAM) */}
          {samFinding?.remediation && (
            <div>
              <label className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                Recommended Remediation
              </label>
              <div className="mt-1 p-3 rounded-lg border border-blue-900/40 bg-blue-950/20 text-xs text-blue-200 leading-relaxed font-mono">
                {samFinding.remediation}
              </div>
            </div>
          )}

          {/* Location details */}
          {(sgmFinding?.controller || samFinding?.location || samFinding?.controller) && (
            <div className="pt-2 border-t border-slate-800/80 flex flex-wrap items-center justify-between text-xs text-slate-400 font-mono">
              <div>
                Source: {sgmFinding?.controller || samFinding?.location || samFinding?.controller}
              </div>
              {sgmFinding?.method && <div>Method: {sgmFinding.method}</div>}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="border-t border-slate-800 bg-[#111827]/40 px-6 py-3 flex justify-end">
          <button
            onClick={onClose}
            className="rounded-lg bg-slate-800 px-4 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-700 transition-colors"
          >
            Close Details
          </button>
        </div>
      </div>
    </div>
  );
};

export default FindingModal;
