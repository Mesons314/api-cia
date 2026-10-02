import React from 'react';
import { ImpactedConsumerDTO } from '../../types/api';
import { Layers, FileCode, Hash, Server } from 'lucide-react';

interface ConsumerCardProps {
  consumer: ImpactedConsumerDTO;
  endpoint?: string;
  method?: string;
}

export const ConsumerCard: React.FC<ConsumerCardProps> = ({
  consumer,
  endpoint,
  method,
}) => {
  return (
    <div className="rounded-lg border border-slate-800 bg-[#0c1220]/90 p-4 hover:border-slate-700 transition-colors">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <Server className="h-4 w-4 text-blue-400" />
          <span className="font-semibold text-sm text-slate-200">{consumer.projectName}</span>
        </div>
        <span className="rounded bg-red-500/10 border border-red-500/20 px-2 py-0.5 text-xs font-mono font-medium text-red-400">
          Breaking Call Site
        </span>
      </div>

      <div className="space-y-2 text-xs font-mono">
        <div className="flex items-start gap-2 text-slate-400">
          <FileCode className="h-3.5 w-3.5 text-slate-500 shrink-0 mt-0.5" />
          <span className="text-slate-300 break-all">{consumer.file}</span>
        </div>
        <div className="flex items-center gap-2 text-slate-400">
          <Hash className="h-3.5 w-3.5 text-slate-500 shrink-0" />
          <span className="text-slate-300">Line {consumer.line}</span>
        </div>
        {endpoint && (
          <div className="mt-2 pt-2 border-t border-slate-800/80 flex items-center justify-between text-slate-400">
            <span className="text-[11px]">Target API:</span>
            <span className="text-slate-200 font-semibold">{method} {endpoint}</span>
          </div>
        )}
      </div>
    </div>
  );
};

export default ConsumerCard;
