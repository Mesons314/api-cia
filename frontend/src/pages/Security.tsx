import React, { useEffect, useState } from 'react';
import {
  ShieldAlert,
  ShieldCheck,
  AlertTriangle,
  Lock,
  Globe,
  Database,
  RefreshCw,
  Search,
  CheckCircle2,
  ExternalLink,
  Code,
  Zap,
} from 'lucide-react';
import { securityApi } from '../api/securityApi';
import SeverityBadge from '../components/Common/SeverityBadge';
import MethodBadge from '../components/Common/MethodBadge';
import FindingModal from '../components/Common/FindingModal';
import { SAMResultDTO, SecurityAlertDTO, PublicEndpointDTO } from '../types/api';

export const Security: React.FC = () => {
  const [samResult, setSamResult] = useState<SAMResultDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Tab: 'audit' (Full Audit), 'no-auth' (Public routes), 'raw-audit' (Audit Raw OpenAPI)
  const [activeTab, setActiveTab] = useState<'audit' | 'no-auth' | 'raw-audit'>('audit');

  // Raw JSON audit input
  const [rawJson, setRawJson] = useState<string>('');
  const [selectedFinding, setSelectedFinding] = useState<SecurityAlertDTO | null>(null);

  // Source root override
  const [sourceRoot, setSourceRoot] = useState<string>('');

  useEffect(() => {
    runAudit();
  }, []);

  const runAudit = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await securityApi.getFullAudit(sourceRoot || undefined);
      setSamResult(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to execute security audit');
    } finally {
      setLoading(false);
    }
  };

  const handleAuditRawSpec = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rawJson.trim()) return;

    setLoading(true);
    setError(null);
    try {
      const result = await securityApi.auditRawSpec(rawJson);
      setSamResult(result);
      setActiveTab('audit');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to audit raw OpenAPI JSON');
    } finally {
      setLoading(false);
    }
  };

  const allAlerts = samResult?.alerts || [
    ...(samResult?.corsViolations || []),
    ...(samResult?.sensitiveDataAlerts || []),
    ...(samResult?.performanceAlerts || []),
  ];

  const publicEndpoints = samResult?.publicEndpoints || [];

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs uppercase tracking-wider text-purple-400 bg-purple-500/10 border border-purple-500/20 px-2 py-0.5 rounded">
              SAM Module
            </span>
          </div>
          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-100 font-mono">
            Security & Compliance Audit
          </h1>
          <p className="mt-1 text-xs text-slate-400">
            Static security analysis for Wildcard CORS, sensitive data leaks, missing authentication, and schema vulnerabilities.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={runAudit}
            disabled={loading}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-2 text-xs font-medium text-slate-300 hover:bg-slate-700 transition-all disabled:opacity-50"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Run SAM Audit</span>
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      {samResult && (
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
          <div className="rounded-lg border border-slate-800 bg-[#0c1220]/90 p-4 font-mono">
            <div className="text-xs text-slate-400 uppercase">Total Alerts</div>
            <div className="mt-1 text-2xl font-bold text-slate-100">
              {samResult.totalAlerts ?? allAlerts.length}
            </div>
            <div className="mt-1 text-[11px] text-slate-500">Security & compliance findings</div>
          </div>
          <div className="rounded-lg border border-red-900/40 bg-red-950/20 p-4 font-mono">
            <div className="text-xs text-red-400 uppercase">Critical & High</div>
            <div className="mt-1 text-2xl font-bold text-red-400">
              {(samResult.criticalCount ?? 0) + (samResult.highCount ?? 0)}
            </div>
            <div className="mt-1 text-[11px] text-slate-500">Immediate remediation needed</div>
          </div>
          <div className="rounded-lg border border-amber-900/40 bg-amber-950/20 p-4 font-mono">
            <div className="text-xs text-amber-400 uppercase">Public Endpoints</div>
            <div className="mt-1 text-2xl font-bold text-amber-400">
              {publicEndpoints.length}
            </div>
            <div className="mt-1 text-[11px] text-slate-500">Routes without authentication</div>
          </div>
          <div className="rounded-lg border border-blue-900/40 bg-blue-950/20 p-4 font-mono">
            <div className="text-xs text-blue-400 uppercase">Security Score (A_sec)</div>
            <div className="mt-1 text-2xl font-bold text-blue-400">
              {samResult.aSec !== undefined ? (samResult.aSec * 100).toFixed(0) + '%' : '100%'}
            </div>
            <div className="mt-1 text-[11px] text-slate-500">Normalized security compliance</div>
          </div>
        </div>
      )}

      {/* Mode Tabs */}
      <div className="flex border-b border-slate-800 text-xs font-semibold">
        <button
          onClick={() => setActiveTab('audit')}
          className={`flex items-center gap-2 px-5 py-3 border-b-2 transition-all ${
            activeTab === 'audit'
              ? 'border-blue-500 text-blue-400 bg-blue-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <ShieldAlert className="h-4 w-4" />
          <span>Security Findings ({allAlerts.length})</span>
        </button>
        <button
          onClick={() => setActiveTab('no-auth')}
          className={`flex items-center gap-2 px-5 py-3 border-b-2 transition-all ${
            activeTab === 'no-auth'
              ? 'border-blue-500 text-blue-400 bg-blue-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Lock className="h-4 w-4" />
          <span>Public / Unauthenticated Routes ({publicEndpoints.length})</span>
        </button>
        <button
          onClick={() => setActiveTab('raw-audit')}
          className={`flex items-center gap-2 px-5 py-3 border-b-2 transition-all ${
            activeTab === 'raw-audit'
              ? 'border-blue-500 text-blue-400 bg-blue-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Code className="h-4 w-4" />
          <span>Audit Raw OpenAPI JSON</span>
        </button>
      </div>

      {/* Error state */}
      {error && (
        <div className="rounded-lg border border-red-500/40 bg-red-950/40 p-4 text-xs text-red-300">
          {error}
        </div>
      )}

      {/* TAB 1: All Security Alerts */}
      {activeTab === 'audit' && (
        <div className="space-y-4">
          {loading ? (
            <div className="p-12 text-center text-xs text-slate-500">
              <RefreshCw className="h-5 w-5 animate-spin mx-auto mb-2 text-purple-500" />
              Scanning source ASTs & OpenAPI schemas for security vulnerabilities...
            </div>
          ) : allAlerts.length === 0 ? (
            <div className="rounded-xl border border-slate-800 bg-[#0c1220] p-12 text-center">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-950/50 text-emerald-400 mb-3 border border-emerald-500/20">
                <CheckCircle2 className="h-6 w-6" />
              </div>
              <h4 className="text-sm font-semibold text-slate-300">No Security Alerts Detected</h4>
              <p className="text-xs text-slate-500 max-w-md mx-auto mt-1">
                No wildcard CORS headers, sensitive credentials exposed in response DTOs, or circular references detected.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3">
              {allAlerts.map((alert, idx) => (
                <div
                  key={idx}
                  onClick={() => setSelectedFinding(alert)}
                  className="rounded-lg border border-slate-800 bg-[#0c1220]/90 p-4 hover:border-slate-700 hover:bg-slate-800/30 transition-all cursor-pointer group"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-3">
                      <span className="font-mono text-xs font-bold text-slate-300 bg-slate-800/80 px-2 py-0.5 rounded border border-slate-700/60">
                        {alert.checkId}
                      </span>
                      <SeverityBadge severity={alert.severity} />
                      <span className="text-xs font-semibold text-slate-200">
                        {alert.category}
                      </span>
                    </div>
                    <span className="text-[11px] text-blue-400 group-hover:underline flex items-center gap-1">
                      View Remediation <ExternalLink className="h-3 w-3" />
                    </span>
                  </div>

                  <div className="mt-2 text-xs text-slate-300">{alert.description}</div>

                  {alert.endpoint && (
                    <div className="mt-2 text-xs font-mono text-slate-400">
                      Location: <span className="text-slate-200">{alert.endpoint}</span>
                    </div>
                  )}

                  {alert.remediation && (
                    <div className="mt-2.5 rounded border border-blue-900/30 bg-blue-950/20 p-2.5 text-[11px] text-blue-300 font-mono">
                      Remediation: {alert.remediation}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 2: Unauthenticated Routes */}
      {activeTab === 'no-auth' && (
        <div className="rounded-xl border border-slate-800 bg-[#0c1220]/90 shadow-xl overflow-hidden">
          <div className="border-b border-slate-800 px-6 py-4">
            <h3 className="text-sm font-semibold text-slate-200">Public & Unauthenticated Endpoints</h3>
            <p className="text-xs text-slate-500">
              Endpoints exposed without Spring Security `@PreAuthorize` or authentication requirements
            </p>
          </div>

          {publicEndpoints.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-500">
              No unauthenticated endpoints found.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs font-mono">
                <thead className="border-b border-slate-800 bg-[#111827]/60 text-[11px] uppercase tracking-wider text-slate-400">
                  <tr>
                    <th className="px-6 py-3">HTTP Method</th>
                    <th className="px-6 py-3">Path</th>
                    <th className="px-6 py-3">Controller & Method</th>
                    <th className="px-6 py-3">Reason</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-slate-300">
                  {publicEndpoints.map((ep, idx) => (
                    <tr key={idx} className="hover:bg-slate-800/40">
                      <td className="px-6 py-3">
                        <MethodBadge method={ep.httpMethod} />
                      </td>
                      <td className="px-6 py-3 font-semibold text-slate-200">{ep.path}</td>
                      <td className="px-6 py-3 text-slate-400">
                        {ep.controllerClass ? `${ep.controllerClass}#${ep.controllerMethod}` : '—'}
                      </td>
                      <td className="px-6 py-3 text-amber-400/90 text-[11px]">
                        {ep.reason || 'No security annotation detected'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* TAB 3: Audit Raw Spec */}
      {activeTab === 'raw-audit' && (
        <form onSubmit={handleAuditRawSpec} className="rounded-xl border border-slate-800 bg-[#0c1220]/90 p-6 space-y-4">
          <div className="border-b border-slate-800 pb-3">
            <h3 className="text-sm font-semibold text-slate-200">
              Audit Raw OpenAPI Specification JSON
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">
              Paste OpenAPI 3.0 JSON directly to check for sensitive property names, missing security schemes, and circular refs.
            </p>
          </div>

          <div>
            <textarea
              rows={12}
              value={rawJson}
              onChange={(e) => setRawJson(e.target.value)}
              placeholder='{ "openapi": "3.0.0", "paths": { ... } }'
              className="w-full rounded-lg border border-slate-700 bg-slate-900/90 p-3 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
              required
            />
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={loading}
              className="inline-flex items-center gap-2 rounded-lg bg-purple-600 px-5 py-2 text-xs font-semibold text-white hover:bg-purple-500 transition-all disabled:opacity-50"
            >
              <Zap className="h-4 w-4" />
              <span>Audit Specification</span>
            </button>
          </div>
        </form>
      )}

      {/* Modal Inspector */}
      <FindingModal
        samFinding={selectedFinding}
        onClose={() => setSelectedFinding(null)}
      />
    </div>
  );
};

export default Security;
