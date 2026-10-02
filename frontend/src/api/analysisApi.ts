import apiClient from './client';
import { AnalysisRequestDTO, AnalysisResponseDTO, ReportSummaryDTO } from '../types/api';

export const analysisApi = {
  compareSpecs: async (request: AnalysisRequestDTO): Promise<AnalysisResponseDTO> => {
    const response = await apiClient.post<AnalysisResponseDTO>('/api/analysis/compare', request);
    return response.data;
  },

  getAllReports: async (): Promise<ReportSummaryDTO[]> => {
    const response = await apiClient.get<ReportSummaryDTO[]>('/api/analysis/reports');
    return response.data;
  },

  getReportById: async (id: number): Promise<AnalysisResponseDTO> => {
    const response = await apiClient.get<AnalysisResponseDTO>(`/api/analysis/reports/${id}`);
    return response.data;
  },
};
