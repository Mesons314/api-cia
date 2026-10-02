import apiClient from './client';

export const exportApi = {
  downloadSdkBySpecId: async (specId: number, language: string = 'typescript-axios'): Promise<Blob> => {
    const response = await apiClient.get(`/api/specs/${specId}/download-sdk`, {
      params: { language },
      responseType: 'blob',
    });
    return response.data;
  },

  generateSdkFromRawSpec: async (rawOpenApiSpec: string, language: string = 'typescript-axios'): Promise<Blob> => {
    const response = await apiClient.post('/api/specs/generate-sdk', rawOpenApiSpec, {
      params: { language },
      headers: {
        'Content-Type': 'application/json',
      },
      responseType: 'blob',
    });
    return response.data;
  },

  exportPostmanBySpecId: async (specId: number): Promise<Blob> => {
    const response = await apiClient.get(`/api/specs/${specId}/export-postman`, {
      responseType: 'blob',
    });
    return response.data;
  },

  exportPostmanFromRawSpec: async (rawOpenApiSpec: string): Promise<Record<string, unknown>> => {
    const response = await apiClient.post<Record<string, unknown>>('/api/specs/export-postman', rawOpenApiSpec, {
      headers: {
        'Content-Type': 'application/json',
      },
    });
    return response.data;
  },
};
