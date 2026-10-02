import apiClient from './client';
import { ClientDependency, ClientProject } from '../types/api';

export const dependencyApi = {
  getProjects: async (): Promise<ClientProject[]> => {
    const response = await apiClient.get<ClientProject[]>('/api/dependencies/projects');
    return response.data;
  },

  registerProject: async (projectName: string, sourcePath: string): Promise<ClientProject> => {
    const response = await apiClient.post<ClientProject>('/api/dependencies/register', null, {
      params: { projectName, sourcePath },
    });
    return response.data;
  },

  scanProject: async (id: number): Promise<ClientProject> => {
    const response = await apiClient.post<ClientProject>(`/api/dependencies/scan/${id}`);
    return response.data;
  },

  getDependencies: async (): Promise<ClientDependency[]> => {
    const response = await apiClient.get<ClientDependency[]>('/api/dependencies');
    return response.data;
  },
};
