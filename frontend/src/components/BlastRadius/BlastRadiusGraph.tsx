import React from 'react';
import { ImpactedEndpointDTO, ImpactedConsumerDTO } from '../../types/api';
import MethodBadge from '../Common/MethodBadge';
import { Server, ArrowDown, FileCode, Radio, Layers, Network } from 'lucide-react';

interface BlastRadiusGraphProps {
  impactedEndpoints: ImpactedEndpointDTO[];
  serviceName?: string;
  onSelectEndpoint?: (endpoint: ImpactedEndpointDTO) => void;
  selectedEndpoint?: ImpactedEndpointDTO | null;
}

export const BlastRadiusGraph: React.FC<BlastRadiusGraphProps> = ({
  impactedEndpoints,
  serviceName = 'Provider Service',
  onSelectEndpoint,
  selectedEndpoint,
}) => {
  if (!impactedEndpoints || impactedEndpoints.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center p-8 rounded-lg border border-slate-800 bg-[#0c1220] text-center">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-950/50 text-emerald-400 border border-emerald-500/20 mb-3">
          <Network className="h-6 w-6" />
        </div>
        <h4 className="text-sm font-semibold text-slate-300">No Downstream Consumer Breakage Detected</h4>
        <p className="text-xs text-slate-500 max-w-md mt-1">
          None of the registered consumer services call the modified endpoints, or changes are non-breaking.
        </p>
      </div>
    );
  }

  const activeEndpoint = selectedEndpoint || impactedEndpoints[0];

  return (
    <div className="space-y-6">
      {/* Endpoint Selector if multiple */}
      {impactedEndpoints.length > 1 && (
        <div className="flex flex-wrap items-center gap-2 border-b border-slate-800 pb-3">
          <span className="text-xs font-semibold text-slate-400 mr-2">Impacted Endpoints:</span>
          {impactedEndpoints.map((ep, idx) => {
            const isSelected = activeEndpoint.endpoint === ep.endpoint && activeEndpoint.method === ep.method;
            return (
              <button
                key={idx}
                onClick={() => onSelectEndpoint && onSelectEndpoint(ep)}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-mono border transition-all ${
                  isSelected
                    ? 'bg-blue-950/70 border-blue-500/60 text-blue-300 shadow-md'
                    : 'bg-[#111827]/60 border-slate-800 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                }`}
              >
                <MethodBadge method={ep.method} />
                <span>{ep.endpoint}</span>
                <span className="ml-1 rounded-full bg-red-500/20 px-1.5 py-0.2 text-[10px] text-red-400 font-sans">
                  {ep.consumers.length}
                </span>
              </button>
            );
          })}
        </div>
      )}

      {/* Visual Architectural Tree Graph */}
      <div className="rounded-xl border border-slate-800 bg-[#0c1220]/95 p-6 shadow-2xl relative overflow-hidden">
        {/* Background Grid Accent */}
        <div className="absolute inset-0 bg-[radial-gradient(#1e293b_1px,transparent_1px)] [background-size:16px_16px] opacity-20 pointer-events-none" />

        <div className="relative flex flex-col items-center">
          {/* Level 1: Provider Service */}
          <div className="flex flex-col items-center">
            <div className="flex items-center gap-2.5 rounded-lg border border-blue-500/40 bg-blue-950/60 px-5 py-2.5 shadow-lg shadow-blue-950/30">
              <Server className="h-4 w-4 text-blue-400" />
              <span className="font-mono text-xs font-bold uppercase tracking-wider text-blue-200">
                {serviceName}
              </span>
            </div>
            <div className="h-6 w-0.5 bg-gradient-to-b from-blue-500/60 to-red-500/60" />
          </div>

          {/* Level 2: Changed API Endpoint */}
          <div className="flex flex-col items-center">
            <div className="flex items-center gap-3 rounded-lg border border-red-500/50 bg-red-950/70 px-6 py-3 shadow-xl shadow-red-950/40">
              <MethodBadge method={activeEndpoint.method} />
              <span className="font-mono text-sm font-bold text-red-100">{activeEndpoint.endpoint}</span>
              <span className="rounded bg-red-500/20 px-2 py-0.5 text-[11px] font-mono font-semibold text-red-300">
                CHANGED
              </span>
            </div>
            
            {/* Connector Line & Splitter */}
            <div className="h-6 w-0.5 bg-slate-700" />
            <div className="flex items-center justify-center text-xs text-slate-500 font-mono mb-2">
              <ArrowDown className="h-4 w-4 text-slate-500" />
              <span className="ml-1">propagate breaking changes to {activeEndpoint.consumers.length} consumer site(s)</span>
            </div>
          </div>

          {/* Level 3: Downstream Consumers Grid */}
          <div className="w-full pt-4">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {activeEndpoint.consumers.map((consumer, cIdx) => {
                const isFeign = consumer.file.toLowerCase().includes('client') || consumer.file.toLowerCase().includes('feign');
                const isRestTemplate = consumer.file.toLowerCase().includes('template') || consumer.file.toLowerCase().includes('service');
                const clientType = isFeign ? 'Feign Client' : isRestTemplate ? 'RestTemplate' : 'WebClient';

                return (
                  <div
                    key={cIdx}
                    className="relative rounded-lg border border-red-900/40 bg-[#111827]/90 p-4 shadow-md hover:border-red-500/50 transition-all group"
                  >
                    {/* Top connection indicator */}
                    <div className="flex items-center justify-between border-b border-slate-800 pb-2.5 mb-2.5">
                      <div className="flex items-center gap-2">
                        <div className="flex h-6 w-6 items-center justify-center rounded bg-amber-500/10 text-amber-400 border border-amber-500/20">
                          <Radio className="h-3 w-3" />
                        </div>
                        <span className="font-bold text-xs text-slate-200">{consumer.projectName}</span>
                      </div>
                      <span className="rounded bg-slate-800/80 px-2 py-0.5 text-[10px] font-mono text-slate-400">
                        {clientType}
                      </span>
                    </div>

                    {/* File & Line Call Site */}
                    <div className="space-y-1.5 font-mono text-xs">
                      <div className="flex items-center gap-1.5 text-slate-300">
                        <FileCode className="h-3.5 w-3.5 text-slate-500 shrink-0" />
                        <span className="truncate text-[11px] text-slate-300" title={consumer.file}>
                          {consumer.file}
                        </span>
                      </div>
                      <div className="flex items-center justify-between pt-1">
                        <span className="text-[11px] text-slate-500">Call Site:</span>
                        <span className="rounded bg-red-500/10 border border-red-500/20 px-2 py-0.5 text-[11px] font-bold text-red-400">
                          Line {consumer.line}
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BlastRadiusGraph;
