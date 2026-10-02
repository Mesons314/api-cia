import React, { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  GitCompare,
  History,
  Target,
  Network,
  ShieldCheck,
  Code2,
  Send,
  Zap,
} from 'lucide-react';
import { specApi } from '../../api/specApi';

export const Sidebar: React.FC = () => {
  const [isConnected, setIsConnected] = useState<boolean | null>(null);

  const checkConnection = async () => {
    try {
      await specApi.getAllSpecs();
      setIsConnected(true);
    } catch {
      setIsConnected(false);
    }
  };

  useEffect(() => {
    checkConnection();
    const interval = setInterval(checkConnection, 15000);
    return () => clearInterval(interval);
  }, []);

  const navItemClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-medium transition-all ${
      isActive
        ? 'bg-blue-600/15 text-blue-400 font-semibold border border-blue-500/20'
        : 'text-slate-400 hover:bg-slate-800/60 hover:text-slate-200'
    }`;

  const subNavItemClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-2.5 px-3 py-1.5 rounded-md text-xs transition-all ${
      isActive
        ? 'text-blue-400 font-medium bg-blue-500/10'
        : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
    }`;

  return (
    <aside className="w-64 bg-[#0c1220] border-r border-slate-800/80 flex flex-col h-screen select-none shrink-0 sticky top-0">
      {/* Brand Header */}
      <div className="p-5 border-b border-slate-800/80">
        <div className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-white font-mono font-bold shadow-md shadow-blue-600/20">
            <Zap className="h-4 w-4" />
          </div>
          <div>
            <h1 className="font-bold text-sm tracking-tight text-slate-100 font-mono">
              API-CIA
            </h1>
            <p className="text-[11px] text-slate-400 font-medium">
              API Change Impact Analyzer
            </p>
          </div>
        </div>
      </div>

      {/* Navigation Groups */}
      <div className="flex-1 overflow-y-auto px-3 py-4 space-y-5">
        {/* Main Dashboard */}
        <div>
          <NavLink to="/" end className={navItemClass}>
            <LayoutDashboard className="h-4 w-4" />
            <span>Dashboard</span>
          </NavLink>
        </div>

        {/* API Analysis Group */}
        <div>
          <div className="px-3 text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-1.5">
            API Analysis
          </div>
          <div className="space-y-0.5">
            <NavLink to="/analyze" className={subNavItemClass}>
              <GitCompare className="h-3.5 w-3.5" />
              <span>Analyze Change</span>
            </NavLink>
            <NavLink to="/history" className={subNavItemClass}>
              <History className="h-3.5 w-3.5" />
              <span>Change History</span>
            </NavLink>
          </div>
        </div>

        {/* Impact Analysis Group */}
        <div>
          <div className="px-3 text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-1.5">
            Impact Analysis
          </div>
          <div className="space-y-0.5">
            <NavLink to="/blast-radius" className={subNavItemClass}>
              <Target className="h-3.5 w-3.5" />
              <span>Blast Radius</span>
            </NavLink>
            <NavLink to="/dependencies" className={subNavItemClass}>
              <Network className="h-3.5 w-3.5" />
              <span>Dependencies</span>
            </NavLink>
          </div>
        </div>

        {/* Security Group */}
        <div>
          <div className="px-3 text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-1.5">
            Security & Governance
          </div>
          <div className="space-y-0.5">
            <NavLink to="/security" className={subNavItemClass}>
              <ShieldCheck className="h-3.5 w-3.5" />
              <span>Security Findings</span>
            </NavLink>
          </div>
        </div>

        {/* Tools Group */}
        <div>
          <div className="px-3 text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-1.5">
            Developer Tools
          </div>
          <div className="space-y-0.5">
            <NavLink to="/sdk-generator" className={subNavItemClass}>
              <Code2 className="h-3.5 w-3.5" />
              <span>SDK Generator</span>
            </NavLink>
            <NavLink to="/postman-generator" className={subNavItemClass}>
              <Send className="h-3.5 w-3.5" />
              <span>Postman Generator</span>
            </NavLink>
          </div>
        </div>
      </div>

      {/* Backend Status Footer */}
      <div className="p-4 border-t border-slate-800/80 bg-[#090d16]/50">
        <div className="flex items-center justify-between text-xs">
          <span className="text-slate-400">Backend:</span>
          {isConnected === null ? (
            <span className="inline-flex items-center gap-1.5 text-slate-400 font-mono text-[11px]">
              <span className="h-2 w-2 rounded-full bg-slate-500 animate-pulse" />
              Connecting...
            </span>
          ) : isConnected ? (
            <span className="inline-flex items-center gap-1.5 text-emerald-400 font-mono text-[11px]">
              <span className="h-2 w-2 rounded-full bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)]" />
              Connected
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 text-red-400 font-mono text-[11px]">
              <span className="h-2 w-2 rounded-full bg-red-400 shadow-[0_0_8px_rgba(248,113,113,0.8)]" />
              Disconnected
            </span>
          )}
        </div>
        <div className="mt-1 text-[10px] text-slate-500 font-mono truncate">
          {import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;
