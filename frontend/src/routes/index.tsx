import React from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppLayout from '../components/Layout/AppLayout';
import Dashboard from '../pages/Dashboard';
import Analyze from '../pages/Analyze';
import AnalysisDetails from '../pages/AnalysisDetails';
import BlastRadiusPage from '../pages/BlastRadiusPage';
import Dependencies from '../pages/Dependencies';
import Security from '../pages/Security';
import History from '../pages/History';
import SdkGenerator from '../pages/SdkGenerator';
import PostmanGenerator from '../pages/PostmanGenerator';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      {
        index: true,
        element: <Dashboard />,
      },
      {
        path: 'analyze',
        element: <Analyze />,
      },
      {
        path: 'analysis/:id',
        element: <AnalysisDetails />,
      },
      {
        path: 'history',
        element: <History />,
      },
      {
        path: 'blast-radius',
        element: <BlastRadiusPage />,
      },
      {
        path: 'dependencies',
        element: <Dependencies />,
      },
      {
        path: 'security',
        element: <Security />,
      },
      {
        path: 'sdk-generator',
        element: <SdkGenerator />,
      },
      {
        path: 'postman-generator',
        element: <PostmanGenerator />,
      },
      {
        path: '*',
        element: <Navigate to="/" replace />,
      },
    ],
  },
]);

export default router;
