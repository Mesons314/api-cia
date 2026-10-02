import React, { useEffect, useState } from 'react';
import { Send, Download, RefreshCw, CheckCircle2, AlertCircle, FileCode, Code2 } from 'lucide-react';
import { specApi } from '../api/specApi';
import { exportApi } from '../api/exportApi';
import { SpecVersionDTO } from '../types/api';

export const PostmanGenerator: React.FC = () => {
  const [specs, setSpecs] = useState<SpecVersionDTO[]>([]);
  const [selectedSpecId, setSelectedSpecId] = useState<number | ''>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [rawSpec, setRawSpec] = useState<string>('');
  const [mode, setMode] = useState<'select' | 'raw'>('select');

  useEffect(() => {
    loadSpecs();
  }, []);

  const loadSpecs = async () => {
    try {
      const data = await specApi.getAllSpecs();
      setSpecs(data);
      if (data.length > 0) {
        setSelectedSpecId(data[0].id);
      }
    } catch (e) {
      console.error('Could not load specs:', e);
    }
  };

  const handleExportPostman = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setSuccessMsg(null);

    try {
      let filename = 'apicia-postman-collection.json';

      if (mode === 'select') {
        if (!selectedSpecId) throw new Error('Please select an OpenAPI specification.');
        const blob = await exportApi.exportPostmanBySpecId(Number(selectedSpecId));
        filename = `spec-${selectedSpecId}-postman-collection.json`;

        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
      } else {
        if (!rawSpec.trim()) throw new Error('Please paste a valid OpenAPI specification JSON.');
        const collection = await exportApi.exportPostmanFromRawSpec(rawSpec);
        const blob = new Blob([JSON.stringify(collection, null, 2)], { type: 'application/json' });
        
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
      }

      setSuccessMsg('Postman Collection v2.1 generated and downloaded successfully!');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Postman collection export failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      {/* Page Header */}
      <div>
        <div className="flex items-center gap-2">
          <span className="font-mono text-xs uppercase tracking-wider text-orange-400 bg-orange-500/10 border border-orange-500/20 px-2 py-0.5 rounded">
            API Testing Export
          </span>
        </div>
        <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-100 font-mono">
          Postman Collection Exporter
        </h1>
        <p className="mt-1 text-xs text-slate-400">
          Convert OpenAPI contracts into Postman v2.1 Collections ready for automated integration testing and QA suites.
        </p>
      </div>

      {/* Mode Tabs */}
      <div className="flex border-b border-slate-800 text-xs font-semibold">
        <button
          onClick={() => setMode('select')}
          className={`flex items-center gap-2 px-5 py-3 border-b-2 transition-all ${
            mode === 'select'
              ? 'border-orange-500 text-orange-400 bg-orange-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <FileCode className="h-4 w-4" />
          <span>From Saved Specification</span>
        </button>
        <button
          onClick={() => setMode('raw')}
          className={`flex items-center gap-2 px-5 py-3 border-b-2 transition-all ${
            mode === 'raw'
              ? 'border-orange-500 text-orange-400 bg-orange-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Code2 className="h-4 w-4" />
          <span>From Raw OpenAPI JSON</span>
        </button>
      </div>

      {/* Notifications */}
      {successMsg && (
        <div className="rounded-lg border border-emerald-500/40 bg-emerald-950/40 p-4 text-xs text-emerald-300 flex items-center gap-2.5">
          <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-red-500/40 bg-red-950/40 p-4 text-xs text-red-300 flex items-center gap-2.5">
          <AlertCircle className="h-4 w-4 text-red-400 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <form onSubmit={handleExportPostman} className="rounded-xl border border-slate-800 bg-[#0c1220]/90 p-6 space-y-6 shadow-xl">
        {mode === 'select' ? (
          <div>
            <label className="text-xs font-semibold text-slate-300">
              Select OpenAPI Contract:
            </label>
            <select
              value={selectedSpecId}
              onChange={(e) => setSelectedSpecId(Number(e.target.value))}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-900/90 px-3.5 py-2.5 text-xs font-mono text-slate-200 focus:border-orange-500 focus:outline-none"
              required
            >
              {specs.length === 0 && <option value="">No specifications uploaded yet</option>}
              {specs.map((s) => (
                <option key={s.id} value={s.id}>
                  #{s.id} • {s.versionLabel} ({s.fileName || 'spec.json'}) — {s.totalEndpoints || 0} endpoints
                </option>
              ))}
            </select>
          </div>
        ) : (
          <div>
            <label className="text-xs font-semibold text-slate-300">
              Paste OpenAPI 3.0 Specification JSON:
            </label>
            <textarea
              rows={8}
              value={rawSpec}
              onChange={(e) => setRawSpec(e.target.value)}
              placeholder='{ "openapi": "3.0.0", "info": { ... }, "paths": { ... } }'
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-900/90 p-3 text-xs font-mono text-slate-200 focus:border-orange-500 focus:outline-none"
              required
            />
          </div>
        )}

        {/* Submit */}
        <div className="flex justify-end pt-2 border-t border-slate-800">
          <button
            type="submit"
            disabled={loading || (mode === 'select' && !selectedSpecId)}
            className="inline-flex items-center gap-2 rounded-lg bg-orange-600 px-6 py-2.5 text-xs font-semibold text-white shadow-lg shadow-orange-600/20 hover:bg-orange-500 transition-all disabled:opacity-50"
          >
            {loading ? (
              <>
                <RefreshCw className="h-4 w-4 animate-spin" />
                <span>Converting to Postman Collection...</span>
              </>
            ) : (
              <>
                <Send className="h-4 w-4" />
                <span>Generate Postman Collection (v2.1)</span>
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
};

export default PostmanGenerator;
