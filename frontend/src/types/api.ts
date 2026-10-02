export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type ViolationSeverity = 'BREAKING' | 'CRITICAL' | 'WARNING' | 'INFO';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS';

export interface SpecVersionDTO {
  id: number;
  versionLabel: string;
  fileName: string;
  totalEndpoints?: number;
  uploadedAt: string;
}

export interface AnalysisRequestDTO {
  oldSpecId: number;
  newSpecId: number;
}

export interface ViolationDTO {
  ruleId: string;
  severity: ViolationSeverity | string;
  endpoint: string;
  message: string;
  oldValue?: string;
  newValue?: string;
  changeType?: string;
  oldPath?: string;
  newPath?: string;
  controller?: string;
  method?: string;
}

export interface SGMResultDTO {
  totalViolations: number;
  breakingCount: number;
  warningCount: number;
  infoCount: number;
  dStruct: number;
  violations: ViolationDTO[];
}

export interface ImpactedConsumerDTO {
  projectName: string;
  file: string;
  line: number;
}

export interface ImpactedEndpointDTO {
  endpoint: string;
  method: string;
  violations: string[];
  consumers: ImpactedConsumerDTO[];
}

export interface BlastRadiusDTO {
  totalImpactedConsumers: number;
  impactedEndpoints: ImpactedEndpointDTO[];
}

export interface ImpactScoreDTO {
  dStruct: number;
  dBlast: number;
  sTotal: number;
  riskLevel: RiskLevel | string;
  breakdown?: Record<string, number>;
}

export interface SecurityAlertDTO {
  checkId: string;
  category: string;
  severity: string;
  endpoint?: string;
  controller?: string;
  method?: string;
  location?: string;
  target?: string;
  description: string;
  remediation?: string;
}

export interface PublicEndpointDTO {
  path: string;
  httpMethod: string;
  controllerClass?: string;
  controllerMethod?: string;
  sourceFile?: string;
  lineNumber?: number;
  reason?: string;
}

export interface SAMResultDTO {
  totalAlerts: number;
  criticalCount: number;
  highCount: number;
  warningCount: number;
  infoCount: number;
  aSec?: number;
  alerts?: SecurityAlertDTO[];
  publicEndpoints?: PublicEndpointDTO[];
  corsViolations?: SecurityAlertDTO[];
  sensitiveDataAlerts?: SecurityAlertDTO[];
  performanceAlerts?: SecurityAlertDTO[];
}

export interface AnalysisResponseDTO {
  reportId?: number | null;
  oldVersion?: string;
  newVersion?: string;
  sgm?: SGMResultDTO;
  sam?: SAMResultDTO;
  impactScore?: ImpactScoreDTO;
  blastRadius?: BlastRadiusDTO;
  dBlast?: number;
  oldSpecId?: number;
  oldSpecTimestamp?: string;
  newSpecId?: number;
  newSpecTimestamp?: string;
}

export interface ReportSummaryDTO {
  id: number;
  oldVersion: string;
  newVersion: string;
  sTotal: number;
  riskLevel: RiskLevel | string;
  createdAt: string;
}

export interface ClientProject {
  id: number;
  projectName: string;
  sourcePath: string;
  scannedAt: string;
}

export interface ClientDependency {
  id: number;
  clientProject: ClientProject;
  filePath: string;
  lineNumber: number;
  httpMethod: string;
  rawPath: string;
  normalizedPath: string;
}

export interface ExtractedEndpoint {
  httpMethod: string;
  path: string;
  controllerClass: string;
  controllerMethod: string;
  sourceFile: string;
  lineNumber: number;
  description?: string;
  deprecated?: boolean;
}

export interface ExtractionSnapshotDTO {
  specId: number;
  versionLabel: string;
  totalEndpoints: number;
  saved: boolean;
  analyzed: boolean;
  analysisReportId?: number;
  extractedAt: string;
}
