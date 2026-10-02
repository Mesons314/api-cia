import React, { useEffect, useState } from 'react';
import { Target, Users, Radio, AlertTriangle, Layers, RefreshCw, FileCode, Server } from 'lucide-react';
import { analysisApi } from '../api/analysisApi';
import { dependencyApi } from '../api/dependencyApi';
import BlastRadiusGraph from '../components/BlastRadius/BlastRadiusGraph';
import StatCard from '../components/Common/StatCard';
import MethodBadge from '../components/Common/MethodBadge';
import { AnalysisResponseDTO, ReportSummaryDTO, ImpactedEndpointDTO, ClientDependency } from '../types/api';

export const BlastRadiusPage: React.FC = () => {
  const [reports, setReports] = useState<ReportSummaryDTO[]>([]);
  const [selectedReportId, setSelectedReportId] = useState<number | null>(null);
  const [reportDetail, setReportDetail] = useState<AnalysisResponseDTO | null>(null);
  const [allDependencies, setAllDependencies] = useState<ClientDependency[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadInitialData();
  }, []);

  const loadInitialData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [reportsData, depsData] = await Promise.all([
        analysisApi.getAllReports().catch(() => []),
        dependencyApi.getDependencies().catch(() => []),
      ]);
      setReports(reportsData);
      setAllDependencies(depsData);

      if (reportsData.length > 0) {
        setSelectedReportId(reportsData[0].id);
        const detail = await analysisApi.getReportById(reportsData[0].id);
        setReportDetail(detail);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load blast radius data');
    } finally {
      setLoading(false);
    }
  };

  const handleSelectReport = async (id: number) => {
    setSelectedReportId(id);
    setLoading(true);
    try {
      const detail = await analysisApi.getReportById(id);
      setReportDetail(detail);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load report');
    } finally {
      setLoading(false);
    }
  };

  const impactedEndpoints = reportDetail?.blastRadius?.impactedEndpoints || [];
  const totalConsumers = reportDetail?.blastRadius?.totalImpactedConsumers || 0;
  const uniqueServices = new Set(
    impactedEndpoints.flatMap((ep) => ep.consumers.map((c) => c.projectName))
  ).size;

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs uppercase tracking-wider text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded">
              Impact Propagation
            </span>
          </div>
          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-100 font-mono">
            Blast Radius Analysis
          </h1>
          <p className="mt-1 text-xs text-slate-400">
            Traces breaking API modifications across microservice client ASTs to pinpoint exact downstream breakage locations.
          </p>
        </div>

        {/* Report Selector */}
        {reports.length > 0 && (
          <div className="flex items-center gap-2">
            <span className="text-xs font-medium text-slate-400">Select Report:</span>
            <select
              value={selectedReportId || ''}
              onChange={(e) => handleSelectReport(Number(e.target.value))}
              className="rounded-lg border border-slate-700 bg-slate-900/90 px-3 py-1.5 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
            >
              {reports.map((r) => (
                <option key={r.id} value={r.id}>
                  #{r.id} • {r.oldVersion} → {r.newVersion} ({r.riskLevel})
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Metrics Row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          label="Affected Consumers"
          value={totalConsumers}
          subtext="Direct downstream client projects"
          icon={<Users className="h-4 w-4 text-amber-400" />}
          variant="warning"
        />
        <StatCard
          label="Consumer Microservices"
          value={uniqueServices}
          subtext="Unique dependent services"
          icon={<Server className="h-4 w-4 text-blue-400" />}
          variant="info"
        />
        <StatCard
          label="Broken Endpoints"
          value={impactedEndpoints.length}
          subtext="Endpoints with client breakages"
          icon={<AlertTriangle className="h-4 w-4 text-red-400" />}
          variant={impactedEndpoints.length > 0 ? 'danger' : 'success'}
        />
      </div>

      {/* Visual Architectural Flow Tree */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-slate-200">
            Consumer Dependency Propagation Tree
          </h3>
          <span className="text-xs font-mono text-slate-500">
            AST Static Discovery
          </span>
        </div>

        {loading ? (
          <div className="p-12 text-center text-xs text-slate-500">
            <RefreshCw className="h-5 w-5 animate-spin mx-auto mb-2 text-blue-500" />
            Loading blast radius data...
          </div>
        ) : (
          <BlastRadiusGraph
            impactedEndpoints={impactedEndpoints}
            serviceName={
              reportDetail?.newVersion
                ? `Provider Service (${reportDetail.newVersion})`
                : 'Provider Service'
            }
          />
        )}
      </div>

      {/* All Discovered Consumer Call Sites List */}
      <div className="rounded-xl border border-slate-800 bg-[#0c1220]/90 shadow-xl overflow-hidden">
        <div className="border-b border-slate-800 px-6 py-4">
          <h3 className="text-sm font-semibold text-slate-200">
            Discovered Consumer Client Sites
          </h3>
          <p className="text-xs text-slate-500">
            Direct references found across Feign, RestTemplate, and WebClient invocations
          </p>
        </div>

        {impactedEndpoints.length === 0 ? (
          <div className="p-8 text-center text-xs text-slate-500">
            No downstream consumer breakages found in the selected report.
          </div>
        ) : (
          <div className="p-6 grid grid-cols-1 md:grid-cols-2 gap-4">
            {impactedEndpoints.flatMap((ep) =>
              ep.consumers.map((c, idx) => (
                <div
                  key={idx}
                  className="rounded-lg border border-slate-800/80 bg-[#111827]/70 p-4 space-y-2 font-mono text-xs"
                >
                  <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                    <span className="font-bold text-slate-200 flex items-center gap-1.5">
                      <Server className="h-3.5 w-3.5 text-blue-400" />
                      {c.projectName}
                    </span>
                    <span className="rounded bg-red-500/10 text-red-400 px-2 py-0.5 text-[10px]">
                      Line {c.line}
                    </span>
                  </div>
                  <div className="flex items-center gap-1.5 text-slate-400">
                    <FileCode className="h-3.5 w-3.5 text-slate-500 shrink-0" />
                    <span className="text-slate-300 truncate" title={c.file}>
                      {c.file}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 pt-1 text-[11px] text-slate-400">
                    <span>Calls:</span>
                    <MethodBadge method={ep.method} />
                    <span className="text-slate-200 font-semibold">{ep.endpoint}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default BlastRadiusPage;
