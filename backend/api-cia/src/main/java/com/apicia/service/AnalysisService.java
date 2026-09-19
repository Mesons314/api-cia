package com.apicia.service;

import com.apicia.exception.InvalidSpecException;
import com.apicia.exception.ResourceNotFoundException;
import com.apicia.model.dto.*;
import com.apicia.model.entity.*;
import com.apicia.repository.AnalysisReportRepository;
import com.apicia.repository.ClientDependencyRepository;
import com.apicia.repository.SpecVersionRepository;
import com.apicia.repository.ViolationRepository;
import com.apicia.service.extraction.DependencyScannerService;
import com.apicia.service.scoring.ImpactScoringService;
import com.apicia.service.sgm.SGMService;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AnalysisService {

    private final SpecVersionRepository specVersionRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final ViolationRepository violationRepository;
    private final SGMService sgmService;
    private final ImpactScoringService impactScoringService;
    private final ClientDependencyRepository clientDependencyRepository;
    private final DependencyScannerService dependencyScannerService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.apicia.service.security.SecurityComplianceScannerService securityComplianceScannerService;

    public AnalysisService(
            SpecVersionRepository specVersionRepository,
            AnalysisReportRepository analysisReportRepository,
            ViolationRepository violationRepository,
            SGMService sgmService,
            ImpactScoringService impactScoringService,
            ClientDependencyRepository clientDependencyRepository,
            DependencyScannerService dependencyScannerService) {
        this.specVersionRepository = specVersionRepository;
        this.analysisReportRepository = analysisReportRepository;
        this.violationRepository = violationRepository;
        this.sgmService = sgmService;
        this.impactScoringService = impactScoringService;
        this.clientDependencyRepository = clientDependencyRepository;
        this.dependencyScannerService = dependencyScannerService;
    }

    public void setSecurityComplianceScannerService(com.apicia.service.security.SecurityComplianceScannerService securityComplianceScannerService) {
        this.securityComplianceScannerService = securityComplianceScannerService;
    }

    public AnalysisResponseDTO compare(AnalysisRequestDTO request) {
        SpecVersion oldSpec = specVersionRepository.findById(request.getOldSpecId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SpecVersion not found with id: " + request.getOldSpecId()));

        SpecVersion newSpec = specVersionRepository.findById(request.getNewSpecId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SpecVersion not found with id: " + request.getNewSpecId()));

        SwaggerParseResult r1 = new OpenAPIParser().readContents(oldSpec.getRawContent(), null, null);
        OpenAPI oldAPI = r1.getOpenAPI();
        if (oldAPI == null) {
            throw new InvalidSpecException("Not valid OpenAPI content for old spec id: " + oldSpec.getId());
        }

        SwaggerParseResult r2 = new OpenAPIParser().readContents(newSpec.getRawContent(), null, null);
        OpenAPI newAPI = r2.getOpenAPI();
        if (newAPI == null) {
            throw new InvalidSpecException("Not valid OpenAPI content for new spec id: " + newSpec.getId());
        }

        SGMResultDTO sgmResult = sgmService.analyze(oldAPI, newAPI);
        BlastRadiusDTO blastRadius = calculateBlastRadius(sgmResult.getViolations());
        int consumerCount = blastRadius != null ? blastRadius.getTotalImpactedConsumers() : 0;
        ImpactScoreDTO scoreResult = impactScoringService.calculate(sgmResult.getDStruct(), consumerCount);

        AnalysisReport report = AnalysisReport.builder()
                .oldSpec(oldSpec)
                .newSpec(newSpec)
                .dStruct(sgmResult.getDStruct())
                .sTotal(scoreResult.getSTotal())
                .riskLevel(RiskLevel.valueOf(scoreResult.getRiskLevel()))
                .build();

        report = analysisReportRepository.save(report);

        if (sgmResult.getViolations() != null) {
            for (ViolationDTO dto : sgmResult.getViolations()) {
                Violation violation = Violation.builder()
                        .report(report)
                        .ruleId(dto.getRuleId())
                        .severity(ViolationSeverity.valueOf(dto.getSeverity()))
                        .endpoint(dto.getEndpoint())
                        .message(dto.getMessage())
                        .oldValue(dto.getOldValue())
                        .newValue(dto.getNewValue())
                        .changeType(dto.getChangeType())
                        .oldPath(dto.getOldPath())
                        .newPath(dto.getNewPath())
                        .controller(dto.getController())
                        .method(dto.getMethod())
                        .build();
                violationRepository.save(violation);
            }
        }

        SAMResultDTO samResult = securityComplianceScannerService != null ? securityComplianceScannerService.auditOpenApi(newAPI) : null;

        return AnalysisResponseDTO.builder()
                .reportId(report.getId())
                .oldVersion(oldSpec.getVersionLabel())
                .newVersion(newSpec.getVersionLabel())
                .sgm(sgmResult)
                .sam(samResult)
                .impactScore(scoreResult)
                .blastRadius(blastRadius)
                .dBlast(scoreResult != null ? scoreResult.getDBlast() : null)
                .oldSpecId(oldSpec.getId())
                .oldSpecTimestamp(oldSpec.getUploadedAt() != null ? oldSpec.getUploadedAt().toString() : null)
                .newSpecId(newSpec.getId())
                .newSpecTimestamp(newSpec.getUploadedAt() != null ? newSpec.getUploadedAt().toString() : null)
                .build();
    }

    public AnalysisResponseDTO compareInMemory(SpecVersion oldSpec, SpecVersion newSpec) {
        SwaggerParseResult r1 = new OpenAPIParser().readContents(oldSpec.getRawContent(), null, null);
        OpenAPI oldAPI = r1.getOpenAPI();
        if (oldAPI == null) {
            throw new InvalidSpecException("Not valid OpenAPI content for old spec: " + oldSpec.getVersionLabel());
        }

        SwaggerParseResult r2 = new OpenAPIParser().readContents(newSpec.getRawContent(), null, null);
        OpenAPI newAPI = r2.getOpenAPI();
        if (newAPI == null) {
            throw new InvalidSpecException("Not valid OpenAPI content for new spec: " + newSpec.getVersionLabel());
        }

        SGMResultDTO sgmResult = sgmService.analyze(oldAPI, newAPI);
        BlastRadiusDTO blastRadius = calculateBlastRadius(sgmResult.getViolations());
        int consumerCount = blastRadius != null ? blastRadius.getTotalImpactedConsumers() : 0;
        ImpactScoreDTO scoreResult = impactScoringService.calculate(sgmResult.getDStruct(), consumerCount);
        SAMResultDTO samResult = securityComplianceScannerService != null ? securityComplianceScannerService.auditOpenApi(newAPI) : null;

        return AnalysisResponseDTO.builder()
                .reportId(null)
                .oldVersion(oldSpec.getVersionLabel())
                .newVersion(newSpec.getVersionLabel())
                .sgm(sgmResult)
                .sam(samResult)
                .impactScore(scoreResult)
                .blastRadius(blastRadius)
                .dBlast(scoreResult != null ? scoreResult.getDBlast() : null)
                .oldSpecId(oldSpec.getId())
                .oldSpecTimestamp(oldSpec.getUploadedAt() != null ? oldSpec.getUploadedAt().toString() : null)
                .newSpecId(newSpec.getId())
                .newSpecTimestamp(newSpec.getUploadedAt() != null ? newSpec.getUploadedAt().toString() : null)
                .build();
    }

    public List<ReportSummaryDTO> getAllReports() {
        List<AnalysisReport> reports = analysisReportRepository.findAll();
        List<ReportSummaryDTO> summaries = new ArrayList<>();
        for (AnalysisReport report : reports) {
            summaries.add(ReportSummaryDTO.builder()
                    .id(report.getId())
                    .oldVersion(report.getOldSpec() != null ? report.getOldSpec().getVersionLabel() : null)
                    .newVersion(report.getNewSpec() != null ? report.getNewSpec().getVersionLabel() : null)
                    .sTotal(report.getSTotal())
                    .riskLevel(report.getRiskLevel() != null ? report.getRiskLevel().name() : null)
                    .createdAt(report.getCreatedAt())
                    .build());
        }
        return summaries;
    }

    public AnalysisResponseDTO getReport(Long id) {
        AnalysisReport report = analysisReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));

        List<Violation> violations = violationRepository.findByReportId(id);

        List<ViolationDTO> violationDTOs = new ArrayList<>();
        int breakingCount = 0;
        int warningCount = 0;
        int infoCount = 0;
        for (Violation v : violations) {
            ViolationDTO dto = ViolationDTO.builder()
                    .ruleId(v.getRuleId())
                    .severity(v.getSeverity() != null ? v.getSeverity().name() : null)
                    .endpoint(v.getEndpoint())
                    .message(v.getMessage())
                    .oldValue(v.getOldValue())
                    .newValue(v.getNewValue())
                    .changeType(v.getChangeType())
                    .oldPath(v.getOldPath())
                    .newPath(v.getNewPath())
                    .controller(v.getController())
                    .method(v.getMethod())
                    .build();
            violationDTOs.add(dto);

            if (v.getSeverity() != null) {
                switch (v.getSeverity()) {
                    case BREAKING:
                    case CRITICAL:
                        breakingCount++;
                        break;
                    case WARNING:
                        warningCount++;
                        break;
                    case INFO:
                        infoCount++;
                        break;
                }
            }
        }

        SGMResultDTO sgm = SGMResultDTO.builder()
                .totalViolations(violationDTOs.size())
                .breakingCount(breakingCount)
                .warningCount(warningCount)
                .infoCount(infoCount)
                .dStruct(report.getDStruct())
                .violations(violationDTOs)
                .build();

        BlastRadiusDTO blastRadius = calculateBlastRadius(violationDTOs);
        int consumerCount = blastRadius != null ? blastRadius.getTotalImpactedConsumers() : 0;
        double dStructVal = report.getDStruct() != null ? report.getDStruct() : 0.0;
        ImpactScoreDTO impactScore = impactScoringService != null
                ? impactScoringService.calculate(dStructVal, consumerCount)
                : null;

        return AnalysisResponseDTO.builder()
                .reportId(report.getId())
                .oldVersion(report.getOldSpec() != null ? report.getOldSpec().getVersionLabel() : null)
                .newVersion(report.getNewSpec() != null ? report.getNewSpec().getVersionLabel() : null)
                .sgm(sgm)
                .impactScore(impactScore)
                .blastRadius(blastRadius)
                .dBlast(impactScore != null ? impactScore.getDBlast() : null)
                .oldSpecId(report.getOldSpec() != null ? report.getOldSpec().getId() : null)
                .oldSpecTimestamp(report.getOldSpec() != null && report.getOldSpec().getUploadedAt() != null ? report.getOldSpec().getUploadedAt().toString() : null)
                .newSpecId(report.getNewSpec() != null ? report.getNewSpec().getId() : null)
                .newSpecTimestamp(report.getNewSpec() != null && report.getNewSpec().getUploadedAt() != null ? report.getNewSpec().getUploadedAt().toString() : null)
                .build();
    }

    private BlastRadiusDTO calculateBlastRadius(List<ViolationDTO> violations) {
        if (violations == null || violations.isEmpty()) {
            return BlastRadiusDTO.builder()
                    .totalImpactedConsumers(0)
                    .impactedEndpoints(new ArrayList<>())
                    .build();
        }

        Map<String, ImpactedEndpointDTO> impactedMap = new LinkedHashMap<>();
        Set<String> uniqueConsumerProjects = new HashSet<>();

        for (ViolationDTO violation : violations) {
            String severity = violation.getSeverity();
            if ("BREAKING".equals(severity) || "CRITICAL".equals(severity)) {
                String httpMethod = resolveHttpMethod(violation);
                String rawPath = resolvePath(violation);
                String normalizedPath = dependencyScannerService.normalizePath(rawPath);

                String key = httpMethod + ":" + normalizedPath;

                List<com.apicia.model.entity.ClientDependency> deps = clientDependencyRepository.findByHttpMethodIgnoreCaseAndNormalizedPath(httpMethod, normalizedPath);
                if (deps.isEmpty()) {
                    continue;
                }

                ImpactedEndpointDTO endpointDTO = impactedMap.computeIfAbsent(key, k -> ImpactedEndpointDTO.builder()
                        .endpoint(rawPath)
                        .method(httpMethod)
                        .violations(new ArrayList<>())
                        .consumers(new ArrayList<>())
                        .build());

                if (!endpointDTO.getViolations().contains(violation.getMessage())) {
                    endpointDTO.getViolations().add(violation.getMessage());
                }

                for (com.apicia.model.entity.ClientDependency dep : deps) {
                    uniqueConsumerProjects.add(dep.getClientProject().getProjectName());

                    boolean alreadyExists = endpointDTO.getConsumers().stream()
                            .anyMatch(c -> c.getProjectName().equals(dep.getClientProject().getProjectName())
                                    && c.getFile().equals(dep.getFilePath())
                                    && c.getLine() == dep.getLineNumber());

                    if (!alreadyExists) {
                        endpointDTO.getConsumers().add(ImpactedConsumerDTO.builder()
                                .projectName(dep.getClientProject().getProjectName())
                                .file(dep.getFilePath())
                                .line(dep.getLineNumber())
                                .build());
                    }
                }
            }
        }

        return BlastRadiusDTO.builder()
                .totalImpactedConsumers(uniqueConsumerProjects.size())
                .impactedEndpoints(new ArrayList<>(impactedMap.values()))
                .build();
    }

    private String resolveHttpMethod(ViolationDTO dto) {
        String ep = dto.getEndpoint();
        if (ep != null) {
            ep = ep.trim();
            if (ep.startsWith("GET ") || ep.startsWith("POST ") || ep.startsWith("PUT ") || ep.startsWith("DELETE ") || ep.startsWith("PATCH ")) {
                return ep.split(" ")[0].toUpperCase();
            }
        }

        String msg = dto.getMessage();
        if (msg != null) {
            String upper = msg.toUpperCase();
            for (String m : List.of("GET", "POST", "PUT", "DELETE", "PATCH")) {
                if (upper.contains(" " + m + " ") || upper.contains(" " + m + "/") || upper.startsWith(m + " ")) {
                    return m;
                }
            }
        }

        if ("SGM-006".equals(dto.getRuleId())) {
            if (dto.getOldValue() != null && List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(dto.getOldValue().toUpperCase())) {
                return dto.getOldValue().toUpperCase();
            }
        }

        return "GET";
    }

    private String resolvePath(ViolationDTO dto) {
        if (dto.getOldPath() != null && dto.getOldPath().startsWith("/")) {
            return dto.getOldPath();
        }
        String ep = dto.getEndpoint();
        if (ep != null) {
            ep = ep.trim();
            if (ep.startsWith("GET ") || ep.startsWith("POST ") || ep.startsWith("PUT ") || ep.startsWith("DELETE ") || ep.startsWith("PATCH ")) {
                return ep.substring(ep.indexOf(' ') + 1);
            }
            if (ep.startsWith("/")) {
                return ep;
            }
        }
        if (dto.getNewPath() != null && dto.getNewPath().startsWith("/")) {
            return dto.getNewPath();
        }
        return ep != null ? ep : "/";
    }
}

