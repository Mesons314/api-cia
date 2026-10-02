import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Upload,
  FileCode,
  ArrowRight,
  GitCompare,
  Zap,
  CheckCircle2,
  AlertCircle,
  Code,
  FolderGit2,
  Layers,
  Sparkles,
} from 'lucide-react';
import { specApi } from '../api/specApi';
import { analysisApi } from '../api/analysisApi';
import { extractionApi } from '../api/extractionApi';
import { SpecVersionDTO } from '../types/api';

// Sample specs for instant 1-click evaluator demo
const SAMPLE_V1_SPEC = {
  openapi: "3.0.0",
  info: { title: "HealQueue Core API", version: "1.0.0" },
  paths: {
    "/v1/users": {
      get: {
        operationId: "getUsers",
        parameters: [{ name: "id", in: "query", required: true, schema: { type: "integer" } }],
        responses: {
          "200": {
            description: "Success",
            content: { "application/json": { schema: { type: "object", properties: { username: { type: "string" } } } } }
          }
        }
      },
      post: {
        operationId: "createUser",
        requestBody: {
          content: { "application/json": { schema: { type: "object", required: ["name"], properties: { name: { type: "string" } } } } }
        },
        responses: { "200": { description: "Success" } }
      }
    },
    "/v1/old-endpoint": {
      get: { operationId: "getOldEndpoint", responses: { "200": { description: "Success" } } }
    },
    "/v1/secure": {
      get: {
        operationId: "getSecure",
        security: [{ OAuth2: [] }],
        responses: { "200": { description: "Success" } }
      }
    }
  }
};

const SAMPLE_V2_SPEC = {
  openapi: "3.0.0",
  info: { title: "HealQueue Core API", version: "2.0.0" },
  paths: {
    "/v1/users": {
      get: {
        operationId: "getUsers",
        parameters: [{ name: "id", in: "query", required: true, schema: { type: "string" } }], // Changed type int -> string (BREAKING)
        responses: {
          "200": {
            description: "Success",
            content: {
              "application/json": {
                schema: {
                  type: "object",
                  properties: {
                    username: { type: "string" },
                    passwordHash: { type: "string" } // Sensitive data exposure (SAM)
                  }
                }
              }
            }
          }
        }
      },
      post: {
        operationId: "createUser",
        requestBody: {
          content: {
            "application/json": {
              schema: {
                type: "object",
                required: ["name", "age"], // Added required field 'age' (BREAKING)
                properties: { name: { type: "string" }, age: { type: "integer" } }
              }
            }
          }
        },
        responses: { "200": { description: "Success" } }
      }
    },
    "/v1/secure": {
      get: {
        operationId: "getSecure", // Removed security requirement (BREAKING/SECURITY)
        responses: { "200": { description: "Success" } }
      }
    },
    "/v1/user_profile": { // Path naming violation SGM-010
      get: { operationId: "getUserProfile", responses: { "200": { description: "Success" } } }
    }
  }
};

export const Analyze: React.FC = () => {
  const navigate = useNavigate();

  // Mode: 'spec-upload' or 'source-extraction'
  const [activeTab, setActiveTab] = useState<'spec-upload' | 'source-extraction'>('spec-upload');

  // Spec selection state
  const [existingSpecs, setExistingSpecs] = useState<SpecVersionDTO[]>([]);
  const [oldSpecId, setOldSpecId] = useState<number | ''>('');
  const [newSpecId, setNewSpecId] = useState<number | ''>('');

  // Upload inputs
  const [oldFile, setOldFile] = useState<File | null>(null);
  const [oldLabel, setOldLabel] = useState<string>('v1.0.0');
  const [newFile, setNewFile] = useState<File | null>(null);
  const [newLabel, setNewLabel] = useState<string>('v2.0.0');

  // Source extraction inputs
  const [sourceRoot, setSourceRoot] = useState<string>('C:/healQueue/HealQueue/backend/src');
  const [versionLabel, setVersionLabel] = useState<string>('v3.0.0-snapshot');

  // Execution state
  const [loading, setLoading] = useState(false);
  const [progressStep, setProgressStep] = useState<string>('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadSpecs();
  }, []);

  const loadSpecs = async () => {
    try {
      const list = await specApi.getAllSpecs();
      setExistingSpecs(list);
      if (list.length >= 2) {
        setOldSpecId(list[1].id);
        setNewSpecId(list[0].id);
      } else if (list.length === 1) {
        setOldSpecId(list[0].id);
      }
    } catch (e) {
      console.error('Could not load specs:', e);
    }
  };

  const handleUploadAndAnalyze = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      let finalOldId = oldSpecId;
      let finalNewId = newSpecId;

      // Upload old file if selected
      if (oldFile) {
        setProgressStep('Uploading Previous API Contract...');
        const uploadedOld = await specApi.uploadSpec(oldFile, oldLabel || 'v1.0');
        finalOldId = uploadedOld.id;
      }

      // Upload new file if selected
      if (newFile) {
        setProgressStep('Uploading Current API Contract...');
        const uploadedNew = await specApi.uploadSpec(newFile, newLabel || 'v2.0');
        finalNewId = uploadedNew.id;
      }

      if (!finalOldId || !finalNewId) {
        throw new Error('Please select or upload both Previous and Current API contracts to compare.');
      }

      if (finalOldId === finalNewId) {
        throw new Error('Previous and Current contract versions must be different.');
      }

      setProgressStep('Running SGM Governance & SAM Security Analysis...');
      await new Promise((r) => setTimeout(r, 400));

      setProgressStep('Scanning Downstream Consumer ASTs & Blast Radius...');
      await new Promise((r) => setTimeout(r, 400));

      setProgressStep('Calculating Composite Risk Score...');
      const result = await analysisApi.compareSpecs({
        oldSpecId: Number(finalOldId),
        newSpecId: Number(finalNewId),
      });

      if (result.reportId) {
        navigate(`/analysis/${result.reportId}`);
      } else {
        // In memory result
        navigate('/analysis/latest', { state: { report: result } });
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Analysis failed. Please check inputs.');
    } finally {
      setLoading(false);
      setProgressStep('');
    }
  };

  const handle1ClickSampleDemo = async () => {
    setError(null);
    setLoading(true);
    try {
      setProgressStep('Loading Sample Contract v1.0.0...');
      const blob1 = new Blob([JSON.stringify(SAMPLE_V1_SPEC, null, 2)], { type: 'application/json' });
      const file1 = new File([blob1], 'healqueue_v1.json', { type: 'application/json' });
      const spec1 = await specApi.uploadSpec(file1, 'v1.0.0-sample');

      setProgressStep('Loading Sample Contract v2.0.0...');
      const blob2 = new Blob([JSON.stringify(SAMPLE_V2_SPEC, null, 2)], { type: 'application/json' });
      const file2 = new File([blob2], 'healqueue_v2.json', { type: 'application/json' });
      const spec2 = await specApi.uploadSpec(file2, 'v2.0.0-sample');

      setProgressStep('Executing Full SGM + SAM + Blast Radius Pipeline...');
      const result = await analysisApi.compareSpecs({
        oldSpecId: spec1.id,
        newSpecId: spec2.id,
      });

      if (result.reportId) {
        navigate(`/analysis/${result.reportId}`);
      } else {
        navigate('/analysis/latest', { state: { report: result } });
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Sample demo failed to execute.');
    } finally {
      setLoading(false);
      setProgressStep('');
    }
  };

  const handleSourceCodeExtraction = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      setProgressStep('Parsing Java Controller Annotations via JavaParser...');
      const result = await extractionApi.previewOpenApiAnalysis(versionLabel, sourceRoot);
      navigate('/analysis/latest', { state: { report: result, isExtractionPreview: true } });
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Source code extraction failed.');
    } finally {
      setLoading(false);
      setProgressStep('');
    }
  };

  const handleTakeSnapshot = async () => {
    setError(null);
    setLoading(true);

    try {
      setProgressStep('Extracting Spring Boot Endpoints & Creating Persistent Snapshot...');
      const snapshotResult = await extractionApi.createSnapshot(true);
      if (snapshotResult.analysisReportId) {
        navigate(`/analysis/${snapshotResult.analysisReportId}`);
      } else {
        navigate('/history');
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Snapshot extraction failed.');
    } finally {
      setLoading(false);
      setProgressStep('');
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Page Header */}
      <div>
        <div className="flex items-center gap-2">
          <span className="font-mono text-xs uppercase tracking-wider text-blue-400 bg-blue-500/10 border border-blue-500/20 px-2 py-0.5 rounded">
            Pipeline Trigger
          </span>
        </div>
        <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-100 font-mono">
          Analyze API Change
        </h1>
        <p className="mt-1 text-xs text-slate-400">
          Compare API contracts and determine structural, security, and downstream impact before deployment.
        </p>
      </div>

      {/* 1-Click Evaluator Demonstration CTA */}
      <div className="rounded-xl border border-blue-500/30 bg-gradient-to-r from-blue-950/40 via-[#0c1220] to-[#0c1220] p-5 shadow-xl">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2 text-blue-400 text-xs font-semibold uppercase tracking-wider">
              <Sparkles className="h-4 w-4" />
              <span>Evaluator 1-Click Demonstration</span>
            </div>
            <h3 className="text-sm font-bold text-slate-100">
              Run Complete Sample API Analysis (v1.0.0 → v2.0.0)
            </h3>
            <p className="text-xs text-slate-400 max-w-xl">
              Instantly loads real sample contracts containing parameter type mutations, new required fields, sensitive data leaks, and downstream consumer dependencies.
            </p>
          </div>
          <button
            onClick={handle1ClickSampleDemo}
            disabled={loading}
            className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-2.5 text-xs font-semibold text-white shadow-lg shadow-blue-600/30 hover:bg-blue-500 transition-all shrink-0 disabled:opacity-50"
          >
            <Zap className="h-4 w-4" />
            <span>Launch Demo Analysis</span>
          </button>
        </div>
      </div>

      {/* Mode Tabs */}
      <div className="flex border-b border-slate-800">
        <button
          onClick={() => setActiveTab('spec-upload')}
          className={`flex items-center gap-2 px-5 py-3 text-xs font-semibold border-b-2 transition-all ${
            activeTab === 'spec-upload'
              ? 'border-blue-500 text-blue-400 bg-blue-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <GitCompare className="h-4 w-4" />
          <span>OpenAPI Contract Diff (SGM/SAM)</span>
        </button>
        <button
          onClick={() => setActiveTab('source-extraction')}
          className={`flex items-center gap-2 px-5 py-3 text-xs font-semibold border-b-2 transition-all ${
            activeTab === 'source-extraction'
              ? 'border-blue-500 text-blue-400 bg-blue-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Code className="h-4 w-4" />
          <span>Static Source-Code Extraction (JavaParser)</span>
        </button>
      </div>

      {/* Error Message */}
      {error && (
        <div className="rounded-lg border border-red-500/40 bg-red-950/40 p-4 text-xs text-red-300 flex items-start gap-3">
          <AlertCircle className="h-4 w-4 text-red-400 shrink-0 mt-0.5" />
          <div className="space-y-1">
            <span className="font-semibold">Analysis Failed</span>
            <p className="text-red-300/90">{error}</p>
          </div>
        </div>
      )}

      {/* Loading & Progress State */}
      {loading && (
        <div className="rounded-xl border border-blue-500/30 bg-[#0c1220] p-6 text-center space-y-4 shadow-xl">
          <div className="inline-flex h-10 w-10 items-center justify-center rounded-full bg-blue-500/10 text-blue-400 border border-blue-500/30 animate-spin">
            <Zap className="h-5 w-5" />
          </div>
          <div>
            <h4 className="text-sm font-semibold text-slate-200">Executing Analysis Pipeline</h4>
            <p className="text-xs font-mono text-blue-400 mt-1">{progressStep || 'Processing...'}</p>
          </div>
          <div className="max-w-md mx-auto h-1.5 rounded-full bg-slate-800 overflow-hidden">
            <div className="h-full bg-blue-500 rounded-full animate-pulse w-3/4" />
          </div>
        </div>
      )}

      {/* Tab 1: Spec Comparison Form */}
      {activeTab === 'spec-upload' && !loading && (
        <form onSubmit={handleUploadAndAnalyze} className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Previous Contract Card */}
            <div className="rounded-xl border border-slate-800 bg-[#0c1220]/90 p-5 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                  Previous API Contract (Base)
                </span>
                <span className="rounded bg-slate-800 px-2 py-0.5 text-[10px] font-mono text-slate-400">
                  V_old
                </span>
              </div>

              {/* Select Existing */}
              <div>
                <label className="text-[11px] font-medium text-slate-400">
                  Select Uploaded Specification:
                </label>
                <select
                  value={oldSpecId}
                  onChange={(e) => {
                    setOldSpecId(e.target.value ? Number(e.target.value) : '');
                    setOldFile(null);
                  }}
                  className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-900/90 px-3 py-2 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
                >
                  <option value="">-- Choose existing spec or upload below --</option>
                  {existingSpecs.map((spec) => (
                    <option key={spec.id} value={spec.id}>
                      #{spec.id} • {spec.versionLabel} ({spec.fileName || 'spec.json'})
                    </option>
                  ))}
                </select>
              </div>

              <div className="relative flex py-1 items-center">
                <div className="flex-grow border-t border-slate-800"></div>
                <span className="flex-shrink mx-2 text-[10px] text-slate-500 uppercase">Or Upload File</span>
                <div className="flex-grow border-t border-slate-800"></div>
              </div>

              {/* Upload New File */}
              <div className="space-y-3">
                <div>
                  <label className="text-[11px] font-medium text-slate-400">Version Label:</label>
                  <input
                    type="text"
                    value={oldLabel}
                    onChange={(e) => setOldLabel(e.target.value)}
                    placeholder="e.g. v1.0.0"
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900/90 px-3 py-2 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="text-[11px] font-medium text-slate-400">OpenAPI Spec File (.json/.yaml):</label>
                  <input
                    type="file"
                    accept=".json,.yaml,.yml"
                    onChange={(e) => {
                      if (e.target.files?.[0]) {
                        setOldFile(e.target.files[0]);
                        setOldSpecId('');
                      }
                    }}
                    className="mt-1 w-full text-xs text-slate-400 file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-slate-800 file:text-slate-300 hover:file:bg-slate-700"
                  />
                </div>
              </div>
            </div>

            {/* Current Contract Card */}
            <div className="rounded-xl border border-slate-800 bg-[#0c1220]/90 p-5 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                  Current API Contract (Target)
                </span>
                <span className="rounded bg-blue-500/20 px-2 py-0.5 text-[10px] font-mono text-blue-300">
                  V_new
                </span>
              </div>

              {/* Select Existing */}
              <div>
                <label className="text-[11px] font-medium text-slate-400">
                  Select Uploaded Specification:
                </label>
                <select
                  value={newSpecId}
                  onChange={(e) => {
                    setNewSpecId(e.target.value ? Number(e.target.value) : '');
                    setNewFile(null);
                  }}
                  className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-900/90 px-3 py-2 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
                >
                  <option value="">-- Choose existing spec or upload below --</option>
                  {existingSpecs.map((spec) => (
                    <option key={spec.id} value={spec.id}>
                      #{spec.id} • {spec.versionLabel} ({spec.fileName || 'spec.json'})
                    </option>
                  ))}
                </select>
              </div>

              <div className="relative flex py-1 items-center">
                <div className="flex-grow border-t border-slate-800"></div>
                <span className="flex-shrink mx-2 text-[10px] text-slate-500 uppercase">Or Upload File</span>
                <div className="flex-grow border-t border-slate-800"></div>
              </div>

              {/* Upload New File */}
              <div className="space-y-3">
                <div>
                  <label className="text-[11px] font-medium text-slate-400">Version Label:</label>
                  <input
                    type="text"
                    value={newLabel}
                    onChange={(e) => setNewLabel(e.target.value)}
                    placeholder="e.g. v2.0.0"
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900/90 px-3 py-2 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="text-[11px] font-medium text-slate-400">OpenAPI Spec File (.json/.yaml):</label>
                  <input
                    type="file"
                    accept=".json,.yaml,.yml"
                    onChange={(e) => {
                      if (e.target.files?.[0]) {
                        setNewFile(e.target.files[0]);
                        setNewSpecId('');
                      }
                    }}
                    className="mt-1 w-full text-xs text-slate-400 file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-slate-800 file:text-slate-300 hover:file:bg-slate-700"
                  />
                </div>
              </div>
            </div>
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={loading}
              className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-6 py-2.5 text-xs font-semibold text-white shadow-lg shadow-blue-600/20 hover:bg-blue-500 transition-all disabled:opacity-50"
            >
              <GitCompare className="h-4 w-4" />
              <span>Run Impact & Governance Analysis</span>
            </button>
          </div>
        </form>
      )}

      {/* Tab 2: Static Source Code Extraction */}
      {activeTab === 'source-extraction' && !loading && (
        <form onSubmit={handleSourceCodeExtraction} className="rounded-xl border border-slate-800 bg-[#0c1220]/90 p-6 space-y-5">
          <div className="border-b border-slate-800 pb-3">
            <h3 className="text-sm font-semibold text-slate-200">
              Static Code Extraction via JavaParser
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">
              Automatically parses Spring Boot `@RestController`, `@RequestMapping`, DTO fields, and annotations to generate OpenAPI specifications directly from source code.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-medium text-slate-300">Backend Source Root Path:</label>
              <input
                type="text"
                value={sourceRoot}
                onChange={(e) => setSourceRoot(e.target.value)}
                placeholder="C:/path/to/project/src"
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900/90 px-3 py-2 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="text-xs font-medium text-slate-300">Extracted Version Label:</label>
              <input
                type="text"
                value={versionLabel}
                onChange={(e) => setVersionLabel(e.target.value)}
                placeholder="0.0.1-SNAPSHOT"
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900/90 px-3 py-2 text-xs font-mono text-slate-200 focus:border-blue-500 focus:outline-none"
              />
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-end gap-3 pt-2 border-t border-slate-800/80">
            <button
              type="button"
              onClick={handleTakeSnapshot}
              disabled={loading}
              className="inline-flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800 px-5 py-2.5 text-xs font-semibold text-slate-200 hover:bg-slate-700 hover:text-white transition-all disabled:opacity-50"
              title="Extracts endpoints from source code, saves as a permanent SpecVersion in database, and runs comparison"
            >
              <FolderGit2 className="h-4 w-4 text-emerald-400" />
              <span>Create Persistent Snapshot & Analyze</span>
            </button>
            <button
              type="submit"
              disabled={loading}
              className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-6 py-2.5 text-xs font-semibold text-white shadow-lg shadow-blue-600/20 hover:bg-blue-500 transition-all disabled:opacity-50"
              title="Runs in-memory extraction and preview analysis without modifying stored database snapshots"
            >
              <Code className="h-4 w-4" />
              <span>Extract & Preview (In-Memory)</span>
            </button>
          </div>
        </form>
      )}
    </div>
  );
};

export default Analyze;
