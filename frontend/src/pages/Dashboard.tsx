import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  GitCompare,
  AlertOctagon,
  Users,
  ShieldAlert,
  ArrowRight,
  RefreshCw,
  PlusCircle,
  FileCode,
  Layers,
} from 'lucide-react';
import StatCard from '../components/Common/StatCard';
import RiskBadge from '../components/Common/RiskBadge';
import PipelineBanner from '../components/Common/PipelineBanner';
import { analysisApi } from '../api/analysisApi';
import { dependencyApi } from '../api/dependencyApi';
import { specApi } from '../api/specApi';
import { ReportSummaryDTO, SpecVersionDTO, ClientDependency } from '../types/api';

export const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const [reports, setReports] = useState<ReportSummaryDTO[]>([]);
  const [specs, setSpecs] = useState<SpecVersionDTO[]>([]);
  const [dependencies, setDependencies] = useState<ClientDependency[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchDashboardData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [reportsData, specsData, depsData] = await Promise.all([
        analysisApi.getAllReports().catch(() => []),
        specApi.getAllSpecs().catch(() => []),
        dependencyApi.getDependencies().catch(() => []),
      ]);
      setReports(reportsData);
      setSpecs(specsData);
      setDependencies(depsData);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to fetch dashboard data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  // Compute live aggregates from backend data
  const totalSpecs = specs.length;
  const totalReports = reports.length;
  const totalConsumers = new Set(dependencies.map((d) => d.clientProject?.projectName || d.rawPath)).size;
  const highOrCriticalCount = reports.filter(
    (r) => r.riskLevel === 'HIGH' || r.riskLevel === 'CRITICAL'
  ).length;

  return (
    <div className="space-y-6">
      {/* Product Hero Banner */}
      <div className="rounded-xl border border-slate-800 bg-[#0c1220]/90 p-6 shadow-xl relative overflow-hidden">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div className="max-w-2xl">
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs font-semibold uppercase tracking-wider text-blue-400 bg-blue-500/10 border border-blue-500/20 px-2 py-0.5 rounded">
                Developer & Security Tooling
              </span>
              <span className="text-xs text-slate-500 font-mono">v1.0.0</span>
            </div>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-100 font-mono">
              API-CIA
            </h1>
            <h2 className="text-sm font-semibold text-slate-300">
              API Change Impact Analyzer
            </h2>
            <p className="mt-2 text-xs text-slate-400 leading-relaxed">
              Detect API contract changes, analyze security and governance violations (SGM & SAM),
              identify downstream consumer breakages, and estimate change risk before deployment.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={fetchDashboardData}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-2 text-xs font-medium text-slate-300 hover:bg-slate-700 hover:text-white transition-all"
              title="Refresh Data"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
              <span>Refresh</span>
            </button>
            <Link
              to="/analyze"
              className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-lg shadow-blue-600/20 hover:bg-blue-500 transition-all"
            >
              <PlusCircle className="h-4 w-4" />
              <span>Analyze API Change</span>
            </Link>
          </div>
        </div>
      </div>

      {/* 4 Pipeline Phases Banner */}
      <PipelineBanner />

      {/* 4 Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="API Specs & Snapshots"
          value={totalSpecs}
          subtext={`${totalReports} comparisons run`}
          icon={<FileCode className="h-4 w-4 text-blue-400" />}
          variant="info"
        />
        <StatCard
          label="Analysis Reports"
          value={totalReports}
          subtext="Executed impact evaluations"
          icon={<GitCompare className="h-4 w-4 text-purple-400" />}
          variant="default"
        />
        <StatCard
          label="Downstream Consumers"
          value={totalConsumers}
          subtext={`${dependencies.length} client call sites scanned`}
          icon={<Users className="h-4 w-4 text-amber-400" />}
          variant="warning"
        />
        <StatCard
          label="High / Critical Risk"
          value={highOrCriticalCount}
          subtext="Requires developer review"
          icon={<AlertOctagon className="h-4 w-4 text-red-400" />}
          variant={highOrCriticalCount > 0 ? 'danger' : 'success'}
        />
      </div>

      {/* Recent Analyses Section */}
      <div className="rounded-xl border border-slate-800 bg-[#0c1220]/90 shadow-xl overflow-hidden">
        <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
          <div>
            <h3 className="text-sm font-semibold text-slate-200">Recent API Change Analyses</h3>
            <p className="text-xs text-slate-500">Historical contract comparisons and composite risk levels</p>
          </div>
          {reports.length > 0 && (
            <Link
              to="/history"
              className="text-xs font-medium text-blue-400 hover:text-blue-300 flex items-center gap-1"
            >
              <span>View All</span>
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          )}
        </div>

        {loading ? (
          <div className="p-12 text-center text-xs text-slate-500">
            <RefreshCw className="h-5 w-5 animate-spin mx-auto mb-2 text-blue-500" />
            Loading recent analyses from backend...
          </div>
        ) : error ? (
          <div className="p-8 text-center text-xs text-red-400">
            Could not retrieve reports: {error}
          </div>
        ) : reports.length === 0 ? (
          <div className="p-12 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-800/80 text-slate-400 mb-3 border border-slate-700">
              <GitCompare className="h-6 w-6" />
            </div>
            <h4 className="text-sm font-semibold text-slate-300">No Analyses Executed Yet</h4>
            <p className="text-xs text-slate-500 max-w-md mx-auto mt-1 mb-5">
              Upload two OpenAPI contracts or run a static source code extraction to inspect governance violations, downstream blast radius, and composite risk.
            </p>
            <Link
              to="/analyze"
              className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white hover:bg-blue-500 transition-all"
            >
              <PlusCircle className="h-4 w-4" />
              <span>Start First Analysis</span>
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-mono">
              <thead className="border-b border-slate-800 bg-[#111827]/60 text-[11px] uppercase tracking-wider text-slate-400">
                <tr>
                  <th className="px-6 py-3">Report ID</th>
                  <th className="px-6 py-3">Version Diff</th>
                  <th className="px-6 py-3">Risk Score (S_total)</th>
                  <th className="px-6 py-3">Risk Level</th>
                  <th className="px-6 py-3">Analyzed Date</th>
                  <th className="px-6 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {reports.slice(0, 5).map((report) => (
                  <tr
                    key={report.id}
                    onClick={() => navigate(`/analysis/${report.id}`)}
                    className="hover:bg-slate-800/40 cursor-pointer transition-colors"
                  >
                    <td className="px-6 py-3.5 text-slate-400">#{report.id}</td>
                    <td className="px-6 py-3.5">
                      <span className="font-semibold text-slate-200">
                        {report.oldVersion || 'v1'}
                      </span>
                      <span className="text-slate-500 mx-2">→</span>
                      <span className="font-semibold text-blue-400">
                        {report.newVersion || 'v2'}
                      </span>
                    </td>
                    <td className="px-6 py-3.5 text-slate-200">
                      {report.sTotal !== undefined ? (report.sTotal * 100).toFixed(0) + '%' : '—'}
                      <span className="text-slate-500 text-[10px] ml-1.5">
                        ({report.sTotal?.toFixed(2)})
                      </span>
                    </td>
                    <td className="px-6 py-3.5">
                      <RiskBadge level={report.riskLevel} size="sm" />
                    </td>
                    <td className="px-6 py-3.5 text-slate-400 font-sans text-xs">
                      {report.createdAt ? new Date(report.createdAt).toLocaleString() : '—'}
                    </td>
                    <td className="px-6 py-3.5 text-right">
                      <span className="inline-flex items-center text-blue-400 font-sans hover:text-blue-300">
                        Details <ArrowRight className="ml-1 h-3 w-3" />
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default Dashboard;
