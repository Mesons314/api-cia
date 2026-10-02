import React, { useEffect, useState } from 'react';
import { Code2, Download, RefreshCw, CheckCircle2, AlertCircle, FileCode, Layers } from 'lucide-react';
import { specApi } from '../api/specApi';
import { exportApi } from '../api/exportApi';
import { SpecVersionDTO } from '../types/api';

export const SdkGenerator: React.FC = () => {
  const [specs, setSpecs] = useState<SpecVersionDTO[]>([]);
  const [selectedSpecId, setSelectedSpecId] = useState<number | ''>('');
  const [language, setLanguage] = useState<string>('typescript-axios');
  const [loading, setLoading] = useState<boolean>(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Manual raw spec mode
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

  const handleGenerateSdk = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setSuccessMsg(null);

    try {
      let blob: Blob;
      let filename = `apicia-client-${language}-sdk.zip`;

      if (mode === 'select') {
        if (!selectedSpecId) throw new Error('Please select an OpenAPI specification.');
        blob = await exportApi.downloadSdkBySpecId(Number(selectedSpecId), language);
        filename = `spec-${selectedSpecId}-${language}-sdk.zip`;
      } else {
        if (!rawSpec.trim()) throw new Error('Please paste a valid OpenAPI specification JSON.');
        blob = await exportApi.generateSdkFromRawSpec(rawSpec, language);
      }

      // Trigger automatic browser download
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);

      setSuccessMsg(`SDK package (${language}) generated and downloaded successfully!`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'SDK generation failed.');
    } finally {
      setLoading(false);
    }
  };

  const supportedLanguages = [
    { id: 'typescript-axios', name: 'TypeScript (Axios)', icon: 'TS' },
    { id: 'java', name: 'Java (Spring / RestTemplate)', icon: 'JAVA' },
    { id: 'python', name: 'Python (Requests / Urllib3)', icon: 'PY' },
    { id: 'dart', name: 'Dart (Flutter / Dio)', icon: 'DART' },
    { id: 'go', name: 'Go (Standard HTTP)', icon: 'GO' },
    { id: 'javascript', name: 'JavaScript (Fetch / Node)', icon: 'JS' },
  ];

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      {/* Page Header */}
      <div>
        <div className="flex items-center gap-2">
          <span className="font-mono text-xs uppercase tracking-wider text-blue-400 bg-blue-500/10 border border-blue-500/20 px-2 py-0.5 rounded">
            Code Generation Tool
          </span>
        </div>
        <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-100 font-mono">
          Client SDK Generator
        </h1>
        <p className="mt-1 text-xs text-slate-400">
          Generate strongly-typed API client packages directly from validated OpenAPI contracts.
        </p>
      </div>

      {/* Mode Tabs */}
      <div className="flex border-b border-slate-800 text-xs font-semibold">
        <button
          onClick={() => setMode('select')}
          className={`flex items-center gap-2 px-5 py-3 border-b-2 transition-all ${
            mode === 'select'
              ? 'border-blue-500 text-blue-400 bg-blue-500/5'
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
              ? 'border-blue-500 text-blue-400 bg-blue-500/5'
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

      <form onSubmit={handleGenerateSdk} className="rounded-xl border border-slate-800 bg-[#0c1220]/90 p-6 space-y-6 shadow-xl">
        {mode === 'select' ? (
          <div>
            <label className="text-xs font-semibold text-slate-300">
              Select OpenAPI Contract:
            </label>
            <select
              value={selectedSpecId}
              onChange={(e) => setSelectedSpecId(Number(e.target.value))}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-900/90 px-3.5 py-2.5 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
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
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-900/90 p-3 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
              required
            />
          </div>
        )}

        {/* Target Language Selection */}
        <div>
          <label className="text-xs font-semibold text-slate-300 block mb-2">
            Target Client Language & Generator:
          </label>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
            {supportedLanguages.map((lang) => (
              <button
                type="button"
                key={lang.id}
                onClick={() => setLanguage(lang.id)}
                className={`p-3 rounded-lg border text-left transition-all font-mono ${
                  language === lang.id
                    ? 'border-blue-500 bg-blue-950/40 text-blue-300 shadow-md'
                    : 'border-slate-800 bg-[#111827]/60 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                }`}
              >
                <div className="text-[10px] text-slate-500 font-bold uppercase">{lang.icon}</div>
                <div className="text-xs font-semibold mt-1 text-slate-200">{lang.name}</div>
              </button>
            ))}
          </div>
        </div>

        {/* Submit */}
        <div className="flex justify-end pt-2 border-t border-slate-800">
          <button
            type="submit"
            disabled={loading || (mode === 'select' && !selectedSpecId)}
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-6 py-2.5 text-xs font-semibold text-white shadow-lg shadow-blue-600/20 hover:bg-blue-500 transition-all disabled:opacity-50"
          >
            {loading ? (
              <>
                <RefreshCw className="h-4 w-4 animate-spin" />
                <span>Compiling Client SDK...</span>
              </>
            ) : (
              <>
                <Download className="h-4 w-4" />
                <span>Generate & Download SDK ZIP</span>
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
};

export default SdkGenerator;
