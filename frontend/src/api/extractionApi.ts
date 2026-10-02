import apiClient from './client';
import { AnalysisResponseDTO, ExtractionSnapshotDTO, ExtractedEndpoint } from '../types/api';

export const extractionApi = {
  getExtractedEndpoints: async (): Promise<ExtractedEndpoint[]> => {
    const response = await apiClient.get<ExtractedEndpoint[]>('/api/extraction/endpoints');
    return response.data;
  },

  previewOpenApiAnalysis: async (versionLabel?: string, sourceRoot?: string): Promise<AnalysisResponseDTO> => {
    const response = await apiClient.get<AnalysisResponseDTO>('/api/extraction/openapi.json', {
      params: {
        preview: true,
        versionLabel: versionLabel || undefined,
        sourceRoot: sourceRoot || undefined,
      },
    });
    return response.data;
  },

  generateAndAnalyze: async (): Promise<AnalysisResponseDTO> => {
    const response = await apiClient.get<AnalysisResponseDTO>('/api/extraction/openapi.json', {
      params: {
        preview: false,
      },
    });
    return response.data;
  },

  getRawOpenApi: async (): Promise<Record<string, unknown>> => {
    const response = await apiClient.get<Record<string, unknown>>('/api/extraction/openapi-raw.json');
    return response.data;
  },

  createSnapshot: async (analyze: boolean = true): Promise<ExtractionSnapshotDTO> => {
    const response = await apiClient.post<ExtractionSnapshotDTO>('/api/extraction/snapshot', null, {
      params: { analyze },
    });
    return response.data;
  },
};
