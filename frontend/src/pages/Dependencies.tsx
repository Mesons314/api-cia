import React, { useEffect, useState } from 'react';
import {
  Network,
  Search,
  Filter,
  RefreshCw,
  PlusCircle,
  FolderGit2,
  FileCode,
  Layers,
  Server,
  Radio,
} from 'lucide-react';
import { dependencyApi } from '../api/dependencyApi';
import MethodBadge from '../components/Common/MethodBadge';
import { ClientDependency, ClientProject } from '../types/api';

export const Dependencies: React.FC = () => {
  const [dependencies, setDependencies] = useState<ClientDependency[]>([]);
  const [projects, setProjects] = useState<ClientProject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Registration modal / form state
  const [showRegisterForm, setShowRegisterForm] = useState(false);
  const [projectName, setProjectName] = useState('');
  const [sourcePath, setSourcePath] = useState('');
  const [registerLoading, setRegisterLoading] = useState(false);

  // Filters
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedService, setSelectedService] = useState('ALL');
  const [selectedMethod, setSelectedMethod] = useState('ALL');
  const [selectedType, setSelectedType] = useState('ALL');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [deps, projs] = await Promise.all([
        dependencyApi.getDependencies(),
        dependencyApi.getProjects(),
      ]);
      setDependencies(deps);
      setProjects(projs);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to fetch dependencies');
    } finally {
      setLoading(false);
    }
  };

  const handleRegisterProject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!projectName || !sourcePath) return;

    setRegisterLoading(true);
    try {
      await dependencyApi.registerProject(projectName, sourcePath);
      setShowRegisterForm(false);
      setProjectName('');
      setSourcePath('');
      await loadData();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to register project');
    } finally {
      setRegisterLoading(false);
    }
  };

  const handleRescanProject = async (id: number) => {
    try {
      await dependencyApi.scanProject(id);
      await loadData();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Rescan failed');
    }
  };

  // Helper to infer dependency type from file path
  const getDependencyType = (filePath: string) => {
    const lower = filePath.toLowerCase();
    if (lower.includes('client') || lower.includes('feign')) return 'Feign';
    if (lower.includes('template') || lower.includes('service')) return 'RestTemplate';
    if (lower.includes('webclient')) return 'WebClient';
    return 'HTTP Client';
  };

  // Filtering
  const filteredDependencies = dependencies.filter((dep) => {
    const depType = getDependencyType(dep.filePath);
    const serviceName = dep.clientProject?.projectName || 'Unknown';

    const matchesSearch =
      searchQuery === '' ||
      serviceName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      dep.rawPath.toLowerCase().includes(searchQuery.toLowerCase()) ||
      dep.filePath.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesService = selectedService === 'ALL' || serviceName === selectedService;
    const matchesMethod = selectedMethod === 'ALL' || dep.httpMethod?.toUpperCase() === selectedMethod;
    const matchesType = selectedType === 'ALL' || depType === selectedType;

    return matchesSearch && matchesService && matchesMethod && matchesType;
  });

  const uniqueServices = Array.from(
    new Set(dependencies.map((d) => d.clientProject?.projectName).filter(Boolean))
  );

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs uppercase tracking-wider text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded">
              Static AST Scanner
            </span>
          </div>
          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-100 font-mono">
            Consumer Dependencies
          </h1>
          <p className="mt-1 text-xs text-slate-400">
            Discovered microservice consumer call sites invoking provider APIs via FeignClient, RestTemplate, and WebClient.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={loadData}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-2 text-xs font-medium text-slate-300 hover:bg-slate-700 transition-all"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
          <button
            onClick={() => setShowRegisterForm(!showRegisterForm)}
            className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-lg shadow-blue-600/20 hover:bg-blue-500 transition-all"
          >
            <PlusCircle className="h-4 w-4" />
            <span>Register Client Project</span>
          </button>
        </div>
      </div>

      {/* Project Registration Form Card */}
      {showRegisterForm && (
        <form
          onSubmit={handleRegisterProject}
          className="rounded-xl border border-blue-500/30 bg-[#0c1220] p-5 space-y-4 shadow-2xl"
        >
          <div className="border-b border-slate-800 pb-2">
            <h3 className="text-sm font-semibold text-slate-200">
              Register & Scan Microservice Project
            </h3>
            <p className="text-xs text-slate-400">
              Provide the local codebase directory to scan Java source files for Feign clients and HTTP templates.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-medium text-slate-300">Project / Service Name:</label>
              <input
                type="text"
                value={projectName}
                onChange={(e) => setProjectName(e.target.value)}
                placeholder="e.g. OrderService"
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900/90 px-3 py-2 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
                required
              />
            </div>
            <div>
              <label className="text-xs font-medium text-slate-300">Source Directory Path:</label>
              <input
                type="text"
                value={sourcePath}
                onChange={(e) => setSourcePath(e.target.value)}
                placeholder="C:/projects/order-service/src"
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900/90 px-3 py-2 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
                required
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setShowRegisterForm(false)}
              className="rounded-lg border border-slate-700 px-3 py-1.5 text-xs text-slate-300 hover:bg-slate-800"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={registerLoading}
              className="rounded-lg bg-blue-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-blue-500 disabled:opacity-50"
            >
              {registerLoading ? 'Scanning ASTs...' : 'Register & Run AST Scanner'}
            </button>
          </div>
        </form>
      )}

      {/* Registered Projects Summary */}
      {projects.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
          {projects.map((p) => (
            <div
              key={p.id}
              className="rounded-lg border border-slate-800 bg-[#0c1220]/80 p-3.5 flex items-center justify-between font-mono text-xs"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <Server className="h-3.5 w-3.5 text-blue-400 shrink-0" />
                  <span className="font-bold text-slate-200 truncate">{p.projectName}</span>
                </div>
                <div className="text-[11px] text-slate-500 truncate mt-0.5" title={p.sourcePath}>
                  {p.sourcePath}
                </div>
              </div>
              <button
                onClick={() => handleRescanProject(p.id)}
                className="rounded p-1.5 text-slate-400 hover:bg-slate-800 hover:text-slate-200 transition-colors shrink-0"
                title="Rescan project"
              >
                <RefreshCw className="h-3.5 w-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Filters Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-800 bg-[#0c1220]/80 p-3 text-xs">
        <div className="flex flex-wrap items-center gap-3">
          {/* Service Filter */}
          <div className="flex items-center gap-1.5">
            <span className="text-slate-400 font-medium">Service:</span>
            <select
              value={selectedService}
              onChange={(e) => setSelectedService(e.target.value)}
              className="rounded border border-slate-700 bg-slate-900 px-2.5 py-1 text-xs font-mono text-slate-200 focus:outline-none"
            >
              <option value="ALL">All Services</option>
              {uniqueServices.map((svc) => (
                <option key={svc} value={svc}>
                  {svc}
                </option>
              ))}
            </select>
          </div>

          {/* HTTP Method Filter */}
          <div className="flex items-center gap-1.5">
            <span className="text-slate-400 font-medium">Method:</span>
            <select
              value={selectedMethod}
              onChange={(e) => setSelectedMethod(e.target.value)}
              className="rounded border border-slate-700 bg-slate-900 px-2.5 py-1 text-xs font-mono text-slate-200 focus:outline-none"
            >
              <option value="ALL">All Methods</option>
              {['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].map((m) => (
                <option key={m} value={m}>
                  {m}
                </option>
              ))}
            </select>
          </div>

          {/* Type Filter */}
          <div className="flex items-center gap-1.5">
            <span className="text-slate-400 font-medium">Type:</span>
            <select
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value)}
              className="rounded border border-slate-700 bg-slate-900 px-2.5 py-1 text-xs font-mono text-slate-200 focus:outline-none"
            >
              <option value="ALL">All Types</option>
              <option value="Feign">Feign Client</option>
              <option value="RestTemplate">RestTemplate</option>
              <option value="WebClient">WebClient</option>
            </select>
          </div>
        </div>

        {/* Search */}
        <div className="relative w-full sm:w-64">
          <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-500" />
          <input
            type="text"
            placeholder="Search path, service, or file..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-md border border-slate-700 bg-slate-900/90 pl-8 pr-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:border-blue-500 focus:outline-none"
          />
        </div>
      </div>

      {/* Dependencies Table */}
      <div className="rounded-xl border border-slate-800 bg-[#0c1220]/90 shadow-xl overflow-hidden">
        {loading ? (
          <div className="p-12 text-center text-xs text-slate-500">
            <RefreshCw className="h-5 w-5 animate-spin mx-auto mb-2 text-blue-500" />
            Loading consumer dependencies...
          </div>
        ) : filteredDependencies.length === 0 ? (
          <div className="p-12 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-800/80 text-slate-400 mb-3 border border-slate-700">
              <Network className="h-6 w-6" />
            </div>
            <h4 className="text-sm font-semibold text-slate-300">No Consumer Dependencies Found</h4>
            <p className="text-xs text-slate-500 max-w-md mx-auto mt-1">
              Register a client microservice project above to scan its ASTs for Feign clients and RestTemplate calls.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-mono">
              <thead className="border-b border-slate-800 bg-[#111827]/60 text-[11px] uppercase tracking-wider text-slate-400">
                <tr>
                  <th className="px-6 py-3">Consumer Service</th>
                  <th className="px-6 py-3">HTTP Method</th>
                  <th className="px-6 py-3">Target API Path</th>
                  <th className="px-6 py-3">Client Type</th>
                  <th className="px-6 py-3">Source File & Line</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {filteredDependencies.map((dep) => {
                  const depType = getDependencyType(dep.filePath);
                  return (
                    <tr key={dep.id} className="hover:bg-slate-800/40 transition-colors">
                      <td className="px-6 py-3 font-semibold text-slate-200 flex items-center gap-2">
                        <Server className="h-3.5 w-3.5 text-blue-400" />
                        <span>{dep.clientProject?.projectName || 'Direct Client'}</span>
                      </td>
                      <td className="px-6 py-3">
                        <MethodBadge method={dep.httpMethod} />
                      </td>
                      <td className="px-6 py-3 text-slate-200">
                        <span>{dep.rawPath}</span>
                        {dep.normalizedPath !== dep.rawPath && (
                          <span className="block text-[10px] text-slate-500 font-normal">
                            normalized: {dep.normalizedPath}
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-3">
                        <span className="rounded bg-slate-800/80 border border-slate-700/60 px-2 py-0.5 text-[10px] text-amber-300">
                          {depType}
                        </span>
                      </td>
                      <td className="px-6 py-3 text-slate-400">
                        <div className="flex items-center gap-1.5">
                          <FileCode className="h-3.5 w-3.5 text-slate-500 shrink-0" />
                          <span className="truncate max-w-xs" title={dep.filePath}>
                            {dep.filePath}
                          </span>
                          <span className="text-slate-500">:{dep.lineNumber}</span>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default Dependencies;
