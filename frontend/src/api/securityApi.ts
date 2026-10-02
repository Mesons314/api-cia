import apiClient from './client';
import { SAMResultDTO, SecurityAlertDTO, PublicEndpointDTO } from '../types/api';

export const securityApi = {
  getFullAudit: async (sourceRoot?: string): Promise<SAMResultDTO> => {
    const response = await apiClient.get<SAMResultDTO>('/api/security/audit', {
      params: { sourceRoot: sourceRoot || undefined },
    });
    return response.data;
  },

  getNoAuthEndpoints: async (sourceRoot?: string): Promise<PublicEndpointDTO[]> => {
    const response = await apiClient.get<PublicEndpointDTO[]>('/api/security/no-auth-endpoints', {
      params: { sourceRoot: sourceRoot || undefined },
    });
    return response.data;
  },

  getCorsViolations: async (sourceRoot?: string): Promise<SecurityAlertDTO[]> => {
    const response = await apiClient.get<SecurityAlertDTO[]>('/api/security/cors-violations', {
      params: { sourceRoot: sourceRoot || undefined },
    });
    return response.data;
  },

  getSensitiveDataAlerts: async (sourceRoot?: string): Promise<SecurityAlertDTO[]> => {
    const response = await apiClient.get<SecurityAlertDTO[]>('/api/security/sensitive-data', {
      params: { sourceRoot: sourceRoot || undefined },
    });
    return response.data;
  },

  auditRawSpec: async (rawOpenApiJson: string): Promise<SAMResultDTO> => {
    const response = await apiClient.post<SAMResultDTO>('/api/security/audit-spec', rawOpenApiJson, {
      headers: {
        'Content-Type': 'text/plain',
      },
    });
    return response.data;
  },
};
