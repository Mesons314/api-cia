import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { PlusCircle, Shield, GitPullRequest } from 'lucide-react';

export const Header: React.FC = () => {
  const location = useLocation();

  const getPageTitle = (pathname: string) => {
    switch (pathname) {
      case '/':
        return 'Overview & Dashboard';
      case '/analyze':
        return 'Analyze API Change';
      case '/history':
        return 'Analysis Reports History';
      case '/blast-radius':
        return 'Downstream Blast Radius Analysis';
      case '/dependencies':
        return 'Discovered Consumer Dependencies';
      case '/security':
        return 'SAM Security & Compliance Audit';
      case '/sdk-generator':
        return 'Client SDK Generator';
      case '/postman-generator':
        return 'Postman Collection Exporter';
      default:
        if (pathname.startsWith('/analysis/')) return 'Detailed Analysis Report';
        return 'API Change Impact Analyzer';
    }
  };

  return (
    <header className="h-14 border-b border-slate-800/80 bg-[#0c1220]/80 backdrop-blur-md px-6 flex items-center justify-between sticky top-0 z-40">
      <div className="flex items-center gap-3">
        <h2 className="text-sm font-semibold text-slate-100 font-mono">
          {getPageTitle(location.pathname)}
        </h2>
      </div>

      <div className="flex items-center gap-3">
        <div className="hidden sm:flex items-center gap-2 text-xs font-mono text-slate-400 bg-slate-900/90 border border-slate-800 px-3 py-1.5 rounded-md">
          <GitPullRequest className="h-3.5 w-3.5 text-blue-400" />
          <span>CI/CD & Git Hook Ready</span>
        </div>

        {location.pathname !== '/analyze' && (
          <Link
            to="/analyze"
            className="inline-flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium px-3.5 py-1.5 rounded-md shadow-sm transition-all"
          >
            <PlusCircle className="h-3.5 w-3.5" />
            <span>Analyze Change</span>
          </Link>
        )}
      </div>
    </header>
  );
};

export default Header;
