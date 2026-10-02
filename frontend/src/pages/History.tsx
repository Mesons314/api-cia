import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { History as HistoryIcon, Search, RefreshCw, ArrowRight, GitCompare, Filter } from 'lucide-react';
import { analysisApi } from '../api/analysisApi';
import RiskBadge from '../components/Common/RiskBadge';
import { ReportSummaryDTO } from '../types/api';

export const History: React.FC = () => {
  const navigate = useNavigate();
  const [reports, setReports] = useState<ReportSummaryDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [riskFilter, setRiskFilter] = useState('ALL');

  useEffect(() => {
    loadReports();
  }, []);

  const loadReports = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await analysisApi.getAllReports();
      setReports(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to fetch analysis history');
    } finally {
      setLoading(false);
    }
  };

  const filteredReports = reports.filter((r) => {
    const matchesRisk = riskFilter === 'ALL' || r.riskLevel === riskFilter;
    const matchesSearch =
      searchQuery === '' ||
      r.id.toString().includes(searchQuery) ||
      (r.oldVersion && r.oldVersion.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (r.newVersion && r.newVersion.toLowerCase().includes(searchQuery.toLowerCase()));

    return matchesRisk && matchesSearch;
  });

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs uppercase tracking-wider text-blue-400 bg-blue-500/10 border border-blue-500/20 px-2 py-0.5 rounded">
              Audit Trail
            </span>
          </div>
          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-100 font-mono">
            Analysis Reports History
          </h1>
          <p className="mt-1 text-xs text-slate-400">
            Historical record of all executed API contract comparisons, governance evaluations, and impact scores.
          </p>
        </div>

        <button
          onClick={loadReports}
          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-2 text-xs font-medium text-slate-300 hover:bg-slate-700 transition-all"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>Refresh</span>
        </button>
      </div>

      {/* Filters Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-800 bg-[#0c1220]/80 p-3 text-xs">
        <div className="flex items-center gap-2">
          <Filter className="h-3.5 w-3.5 text-slate-500" />
          <span className="text-slate-400 font-medium">Risk Level:</span>
          <div className="flex gap-1">
            {['ALL', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((lvl) => (
              <button
                key={lvl}
                onClick={() => setRiskFilter(lvl)}
                className={`rounded px-2.5 py-1 text-[11px] font-mono transition-all ${
                  riskFilter === lvl
                    ? 'bg-blue-600 text-white font-semibold'
                    : 'bg-slate-800/80 text-slate-400 hover:bg-slate-700'
                }`}
              >
                {lvl}
              </button>
            ))}
          </div>
        </div>

        <div className="relative w-full sm:w-64">
          <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-500" />
          <input
            type="text"
            placeholder="Search report ID or version..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-md border border-slate-700 bg-slate-900/90 pl-8 pr-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:border-blue-500 focus:outline-none"
          />
        </div>
      </div>

      {/* Reports Table */}
      <div className="rounded-xl border border-slate-800 bg-[#0c1220]/90 shadow-xl overflow-hidden">
        {loading ? (
          <div className="p-12 text-center text-xs text-slate-500">
            <RefreshCw className="h-5 w-5 animate-spin mx-auto mb-2 text-blue-500" />
            Loading historical reports...
          </div>
        ) : error ? (
          <div className="p-8 text-center text-xs text-red-400">{error}</div>
        ) : filteredReports.length === 0 ? (
          <div className="p-12 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-800/80 text-slate-400 mb-3 border border-slate-700">
              <HistoryIcon className="h-6 w-6" />
            </div>
            <h4 className="text-sm font-semibold text-slate-300">No Reports Found</h4>
            <p className="text-xs text-slate-500 max-w-md mx-auto mt-1">
              Run an analysis comparison from the Analyze page to generate persistent reports.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-mono">
              <thead className="border-b border-slate-800 bg-[#111827]/60 text-[11px] uppercase tracking-wider text-slate-400">
                <tr>
                  <th className="px-6 py-3">Report ID</th>
                  <th className="px-6 py-3">Base Contract</th>
                  <th className="px-6 py-3">Target Contract</th>
                  <th className="px-6 py-3">Composite Risk (S_total)</th>
                  <th className="px-6 py-3">Risk Level</th>
                  <th className="px-6 py-3">Analysis Date</th>
                  <th className="px-6 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {filteredReports.map((r) => (
                  <tr
                    key={r.id}
                    onClick={() => navigate(`/analysis/${r.id}`)}
                    className="hover:bg-slate-800/40 cursor-pointer transition-colors"
                  >
                    <td className="px-6 py-3.5 text-slate-400 font-bold">#{r.id}</td>
                    <td className="px-6 py-3.5 text-slate-300">{r.oldVersion || 'v1.0.0'}</td>
                    <td className="px-6 py-3.5 text-blue-400 font-semibold">
                      {r.newVersion || 'v2.0.0'}
                    </td>
                    <td className="px-6 py-3.5 text-slate-200">
                      {r.sTotal !== undefined ? (r.sTotal * 100).toFixed(0) + '%' : '—'}
                      <span className="text-slate-500 text-[10px] ml-1.5">
                        ({r.sTotal?.toFixed(2)})
                      </span>
                    </td>
                    <td className="px-6 py-3.5">
                      <RiskBadge level={r.riskLevel} size="sm" />
                    </td>
                    <td className="px-6 py-3.5 text-slate-400 font-sans text-xs">
                      {r.createdAt ? new Date(r.createdAt).toLocaleString() : '—'}
                    </td>
                    <td className="px-6 py-3.5 text-right">
                      <span className="inline-flex items-center text-blue-400 font-sans hover:text-blue-300">
                        View Report <ArrowRight className="ml-1 h-3 w-3" />
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

export default History;
