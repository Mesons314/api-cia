import apiClient from './client';
import { SpecVersionDTO } from '../types/api';

export const specApi = {
  uploadSpec: async (file: File, versionLabel: string): Promise<SpecVersionDTO> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('versionLabel', versionLabel);

    const response = await apiClient.post<SpecVersionDTO>('/api/specs/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  getAllSpecs: async (): Promise<SpecVersionDTO[]> => {
    const response = await apiClient.get<SpecVersionDTO[]>('/api/specs');
    return response.data;
  },

  getSpecById: async (id: number): Promise<SpecVersionDTO> => {
    const response = await apiClient.get<SpecVersionDTO>(`/api/specs/${id}`);
    return response.data;
  },
};
