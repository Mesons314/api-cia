import React, { useEffect, useState } from 'react';
import { useParams, useLocation, Link } from 'react-router-dom';
import {
  GitCompare,
  ShieldAlert,
  Target,
  ArrowLeft,
  Download,
  AlertTriangle,
  FileCode,
  CheckCircle2,
  ExternalLink,
  Code2,
  Send,
  Layers,
  Search,
  Filter,
} from 'lucide-react';
import { analysisApi } from '../api/analysisApi';
import { exportApi } from '../api/exportApi';
import RiskBadge from '../components/Common/RiskBadge';
import SeverityBadge from '../components/Common/SeverityBadge';
import MethodBadge from '../components/Common/MethodBadge';
import FormulaCard from '../components/Common/FormulaCard';
import FindingModal from '../components/Common/FindingModal';
import BlastRadiusGraph from '../components/BlastRadius/BlastRadiusGraph';
import { AnalysisResponseDTO, ViolationDTO, SecurityAlertDTO } from '../types/api';

export const AnalysisDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();

  const [report, setReport] = useState<AnalysisResponseDTO | null>(
    (location.state as { report?: AnalysisResponseDTO })?.report || null
  );
  const [loading, setLoading] = useState<boolean>(!report && !!id && id !== 'latest');
  const [error, setError] = useState<string | null>(null);

  // Active Tab: 'sgm' (What Changed), 'sam' (Security Findings), 'blast' (Blast Radius)
  const [activeTab, setActiveTab] = useState<'sgm' | 'sam' | 'blast'>('sgm');

  // Finding modal inspector state
  const [selectedSgmFinding, setSelectedSgmFinding] = useState<ViolationDTO | null>(null);
  const [selectedSamFinding, setSelectedSamFinding] = useState<SecurityAlertDTO | null>(null);

  // Filters for SGM
  const [severityFilter, setSeverityFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  useEffect(() => {
    if (id && id !== 'latest') {
      fetchReport(Number(id));
    }
  }, [id]);

  const fetchReport = async (reportId: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await analysisApi.getReportById(reportId);
      setReport(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Could not fetch analysis report');
    } finally {
      setLoading(false);
    }
  };

  const handleDownloadSdk = async (specId?: number) => {
    if (!specId) return;
    try {
      const blob = await exportApi.downloadSdkBySpecId(specId, 'typescript-axios');
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `apicia-sdk-spec-${specId}.zip`;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (e) {
      alert('Failed to download SDK package.');
    }
  };

  const handleDownloadPostman = async (specId?: number) => {
    if (!specId) return;
    try {
      const blob = await exportApi.exportPostmanBySpecId(specId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `apicia-postman-collection-spec-${specId}.json`;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (e) {
      alert('Failed to download Postman collection.');
    }
  };

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center text-xs text-slate-400 font-mono">
        <GitCompare className="h-5 w-5 animate-spin text-blue-500 mr-2" />
        Loading analysis report #{id}...
      </div>
    );
  }

  if (error || !report) {
    return (
      <div className="space-y-4">
        <Link
          to="/history"
          className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-200"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>Back to History</span>
        </Link>
        <div className="rounded-lg border border-red-500/40 bg-red-950/40 p-6 text-center text-xs text-red-300">
          {error || 'Report not found.'}
        </div>
      </div>
    );
  }

  const violations = report.sgm?.violations || [];
  const filteredViolations = violations.filter((v) => {
    const matchesSeverity =
      severityFilter === 'ALL' ||
      (severityFilter === 'BREAKING' && (v.severity === 'BREAKING' || v.severity === 'CRITICAL')) ||
      (severityFilter === 'WARNING' && (v.severity === 'WARNING' || v.severity === 'MEDIUM')) ||
      (severityFilter === 'INFO' && (v.severity === 'INFO' || v.severity === 'LOW'));

    const matchesQuery =
      searchQuery === '' ||
      v.ruleId.toLowerCase().includes(searchQuery.toLowerCase()) ||
      v.endpoint.toLowerCase().includes(searchQuery.toLowerCase()) ||
      v.message.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesSeverity && matchesQuery;
  });

  const samAlerts = report.sam?.alerts || [
    ...(report.sam?.corsViolations || []),
    ...(report.sam?.sensitiveDataAlerts || []),
    ...(report.sam?.performanceAlerts || []),
  ];

  const impactedEndpoints = report.blastRadius?.impactedEndpoints || [];
  const totalConsumers = report.blastRadius?.totalImpactedConsumers || 0;

  const riskLevel = report.impactScore?.riskLevel || 'LOW';

  return (
    <div className="space-y-6">
      {/* Top Breadcrumb & Actions Bar */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <Link
            to="/history"
            className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-800 bg-[#0c1220] text-slate-400 hover:bg-slate-800 hover:text-slate-200 transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
          </Link>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-mono text-slate-400">
                {report.reportId ? `Report #${report.reportId}` : 'Preview Analysis'}
              </span>
              <RiskBadge level={riskLevel} size="sm" />
            </div>
            <h1 className="text-lg font-bold text-slate-100 font-mono flex items-center gap-2">
              <span>{report.oldVersion || 'v1'}</span>
              <span className="text-slate-500 font-normal">→</span>
              <span className="text-blue-400">{report.newVersion || 'v2'}</span>
            </h1>
          </div>
        </div>

        {/* Export Actions */}
        <div className="flex items-center gap-2">
          {report.newSpecId && (
            <>
              <button
                onClick={() => handleDownloadSdk(report.newSpecId)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800/90 px-3 py-2 text-xs font-medium text-slate-200 hover:bg-slate-700 transition-all"
              >
                <Code2 className="h-3.5 w-3.5 text-blue-400" />
                <span>Download SDK</span>
              </button>
              <button
                onClick={() => handleDownloadPostman(report.newSpecId)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800/90 px-3 py-2 text-xs font-medium text-slate-200 hover:bg-slate-700 transition-all"
              >
                <Send className="h-3.5 w-3.5 text-orange-400" />
                <span>Export Postman</span>
              </button>
            </>
          )}
        </div>
      </div>

      {/* Composite Risk Score Breakdown Card */}
      <FormulaCard
        score={report.impactScore}
        dStruct={report.sgm?.dStruct}
        dBlast={report.dBlast || report.impactScore?.dBlast}
      />

      {/* Navigation Tabs */}
      <div className="flex border-b border-slate-800 text-xs font-semibold">
        <button
          onClick={() => setActiveTab('sgm')}
          className={`flex items-center gap-2 px-5 py-3 border-b-2 transition-all ${
            activeTab === 'sgm'
              ? 'border-blue-500 text-blue-400 bg-blue-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <GitCompare className="h-4 w-4" />
          <span>What Changed? (SGM Governance)</span>
          <span className="rounded-full bg-slate-800 px-2 py-0.5 text-[10px] text-slate-300">
            {violations.length}
          </span>
        </button>

        <button
          onClick={() => setActiveTab('blast')}
          className={`flex items-center gap-2 px-5 py-3 border-b-2 transition-all ${
            activeTab === 'blast'
              ? 'border-blue-500 text-blue-400 bg-blue-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Target className="h-4 w-4" />
          <span>Blast Radius & Consumers</span>
          <span className="rounded-full bg-slate-800 px-2 py-0.5 text-[10px] text-slate-300">
            {totalConsumers}
          </span>
        </button>

        <button
          onClick={() => setActiveTab('sam')}
          className={`flex items-center gap-2 px-5 py-3 border-b-2 transition-all ${
            activeTab === 'sam'
              ? 'border-blue-500 text-blue-400 bg-blue-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <ShieldAlert className="h-4 w-4" />
          <span>Security & Compliance (SAM)</span>
          <span className="rounded-full bg-slate-800 px-2 py-0.5 text-[10px] text-slate-300">
            {samAlerts.length}
          </span>
        </button>
      </div>

      {/* TAB 1: SGM Governance Violations ("What Changed?") */}
      {activeTab === 'sgm' && (
        <div className="space-y-4">
          {/* Filters Bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-800 bg-[#0c1220]/80 p-3">
            <div className="flex items-center gap-2">
              <Filter className="h-3.5 w-3.5 text-slate-500" />
              <span className="text-xs text-slate-400 font-medium">Severity:</span>
              <div className="flex gap-1">
                {['ALL', 'BREAKING', 'WARNING', 'INFO'].map((sev) => (
                  <button
                    key={sev}
                    onClick={() => setSeverityFilter(sev)}
                    className={`rounded px-2.5 py-1 text-[11px] font-mono transition-all ${
                      severityFilter === sev
                        ? 'bg-blue-600 text-white font-semibold'
                        : 'bg-slate-800/80 text-slate-400 hover:bg-slate-700'
                    }`}
                  >
                    {sev}
                  </button>
                ))}
              </div>
            </div>

            <div className="relative w-full sm:w-64">
              <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-500" />
              <input
                type="text"
                placeholder="Search rule ID or endpoint..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full rounded-md border border-slate-700 bg-slate-900/90 pl-8 pr-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:border-blue-500 focus:outline-none"
              />
            </div>
          </div>

          {filteredViolations.length === 0 ? (
            <div className="rounded-lg border border-slate-800 bg-[#0c1220] p-8 text-center text-xs text-slate-500">
              No governance violations found matching filter.
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3">
              {filteredViolations.map((v, idx) => (
                <div
                  key={idx}
                  onClick={() => setSelectedSgmFinding(v)}
                  className="rounded-lg border border-slate-800/90 bg-[#0c1220]/90 p-4 hover:border-slate-700 hover:bg-slate-800/30 transition-all cursor-pointer group"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-3">
                      <span className="font-mono text-xs font-bold text-slate-300 bg-slate-800/80 px-2 py-0.5 rounded border border-slate-700/60">
                        {v.ruleId}
                      </span>
                      <SeverityBadge severity={v.severity} />
                      <div className="flex items-center gap-1.5 font-mono text-xs text-slate-200">
                        <MethodBadge
                          method={v.endpoint?.split(' ')[0] || v.method || 'GET'}
                        />
                        <span>
                          {v.endpoint?.includes(' ')
                            ? v.endpoint.split(' ').slice(1).join(' ')
                            : v.endpoint}
                        </span>
                      </div>
                    </div>
                    <span className="text-[11px] text-blue-400 group-hover:underline flex items-center gap-1">
                      Inspect Details <ExternalLink className="h-3 w-3" />
                    </span>
                  </div>

                  <div className="mt-2.5 text-xs text-slate-300">{v.message}</div>

                  {(v.oldValue || v.newValue) && (
                    <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs font-mono">
                      <div className="rounded bg-red-950/20 border border-red-900/30 p-2 text-slate-300">
                        <span className="text-[10px] text-red-400 uppercase block">Previous:</span>
                        <span className="text-slate-300 truncate block">{v.oldValue || 'None'}</span>
                      </div>
                      <div className="rounded bg-emerald-950/20 border border-emerald-900/30 p-2 text-slate-300">
                        <span className="text-[10px] text-emerald-400 uppercase block">Current:</span>
                        <span className="text-slate-300 truncate block">{v.newValue || 'None'}</span>
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 2: Blast Radius Explorer */}
      {activeTab === 'blast' && (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-slate-800 bg-[#0c1220]/80 p-4">
            <div>
              <h3 className="text-sm font-semibold text-slate-200">
                Downstream Microservice Breakage Map
              </h3>
              <p className="text-xs text-slate-400 mt-0.5">
                AST scanner analyzed client codebases to discover consumer call sites invoking modified endpoints.
              </p>
            </div>
            <div className="flex items-center gap-3 font-mono text-xs">
              <div className="rounded bg-slate-800 px-3 py-1.5 border border-slate-700">
                <span className="text-slate-400">Total Consumers: </span>
                <span className="font-bold text-amber-400">{totalConsumers}</span>
              </div>
              <div className="rounded bg-slate-800 px-3 py-1.5 border border-slate-700">
                <span className="text-slate-400">Broken Endpoints: </span>
                <span className="font-bold text-red-400">{impactedEndpoints.length}</span>
              </div>
            </div>
          </div>

          <BlastRadiusGraph
            impactedEndpoints={impactedEndpoints}
            serviceName={report.newVersion ? `Provider Service (${report.newVersion})` : 'Provider Service'}
          />
        </div>
      )}

      {/* TAB 3: SAM Security Findings */}
      {activeTab === 'sam' && (
        <div className="space-y-4">
          <div className="rounded-lg border border-slate-800 bg-[#0c1220]/80 p-4">
            <h3 className="text-sm font-semibold text-slate-200">
              Security Analysis Module (SAM) Compliance
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">
              Audits CORS configurations, sensitive data exposures in DTOs, unauthenticated routes, and schema circularities.
            </p>
          </div>

          {samAlerts.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-8 rounded-lg border border-slate-800 bg-[#0c1220] text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-950/50 text-emerald-400 border border-emerald-500/20 mb-3">
                <CheckCircle2 className="h-6 w-6" />
              </div>
              <h4 className="text-sm font-semibold text-slate-300">No Security Alerts Detected</h4>
              <p className="text-xs text-slate-500 max-w-md mt-1">
                The specification conforms to security baselines (no wildcard CORS, unauthenticated routes, or sensitive data leaks).
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3">
              {samAlerts.map((alert, idx) => (
                <div
                  key={idx}
                  onClick={() => setSelectedSamFinding(alert)}
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
                      Remediation <ExternalLink className="h-3 w-3" />
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

      {/* Slide-over Inspection Modal */}
      <FindingModal
        sgmFinding={selectedSgmFinding}
        samFinding={selectedSamFinding}
        onClose={() => {
          setSelectedSgmFinding(null);
          setSelectedSamFinding(null);
        }}
      />
    </div>
  );
};

export default AnalysisDetails;
