package com.apicia.service;

import com.apicia.exception.InvalidSpecException;
import com.apicia.exception.ResourceNotFoundException;
import com.apicia.model.dto.*;
import com.apicia.model.entity.*;
import com.apicia.repository.AnalysisReportRepository;
import com.apicia.repository.SecurityAlertRepository;
import com.apicia.repository.SpecVersionRepository;
import com.apicia.repository.ViolationRepository;
import com.apicia.service.sam.SAMService;
import com.apicia.service.scoring.ImpactScoringService;
import com.apicia.service.sgm.SGMService;
import com.apicia.service.spm.SPMService;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AnalysisService {

    private final SpecVersionRepository specVersionRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final ViolationRepository violationRepository;
    private final SecurityAlertRepository securityAlertRepository;
    private final SGMService sgmService;
    private final SPMService spmService;
    private final SAMService samService;
    private final ImpactScoringService impactScoringService;

    @Value("${cia.weights.w1}")
    private double w1;

    @Value("${cia.weights.w2}")
    private double w2;

    @Value("${cia.weights.w3}")
    private double w3;

    public AnalysisService(
            SpecVersionRepository specVersionRepository,
            AnalysisReportRepository analysisReportRepository,
            ViolationRepository violationRepository,
            SecurityAlertRepository securityAlertRepository,
            SGMService sgmService,
            SPMService spmService,
            SAMService samService,
            ImpactScoringService impactScoringService) {
        this.specVersionRepository = specVersionRepository;
        this.analysisReportRepository = analysisReportRepository;
        this.violationRepository = violationRepository;
        this.securityAlertRepository = securityAlertRepository;
        this.sgmService = sgmService;
        this.spmService = spmService;
        this.samService = samService;
        this.impactScoringService = impactScoringService;
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
        SPMResultDTO spmResult = spmService.analyze(oldAPI, newAPI);
        SAMResultDTO samResult = samService.analyze(oldAPI, newAPI);
        ImpactScoreDTO scoreResult = impactScoringService.calculate(sgmResult.getDStruct(), spmResult.getDApi(),
                samResult.getASec());

        AnalysisReport report = AnalysisReport.builder()
                .oldSpec(oldSpec)
                .newSpec(newSpec)
                .dStruct(sgmResult.getDStruct())
                .dApi(spmResult.getDApi())
                .aSec(samResult.getASec())
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
                        .build();
                violationRepository.save(violation);
            }
        }

        if (samResult.getAlerts() != null) {
            for (SecurityAlertDTO dto : samResult.getAlerts()) {
                SecurityAlert alert = SecurityAlert.builder()
                        .report(report)
                        .checkId(dto.getCheckId())
                        .severity(AlertSeverity.valueOf(dto.getSeverity()))
                        .endpoint(dto.getEndpoint())
                        .description(dto.getDescription())
                        .build();
                securityAlertRepository.save(alert);
            }
        }

        return AnalysisResponseDTO.builder()
                .reportId(report.getId())
                .oldVersion(oldSpec.getVersionLabel())
                .newVersion(newSpec.getVersionLabel())
                .sgm(sgmResult)
                .spm(spmResult)
                .sam(samResult)
                .impactScore(scoreResult)
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
        List<SecurityAlert> alerts = securityAlertRepository.findByReportId(id);

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

        List<SecurityAlertDTO> alertDTOs = new ArrayList<>();
        int criticalAlerts = 0;
        int highAlerts = 0;
        for (SecurityAlert a : alerts) {
            SecurityAlertDTO dto = SecurityAlertDTO.builder()
                    .checkId(a.getCheckId())
                    .severity(a.getSeverity() != null ? a.getSeverity().name() : null)
                    .endpoint(a.getEndpoint())
                    .description(a.getDescription())
                    .build();
            alertDTOs.add(dto);

            if (a.getSeverity() != null) {
                switch (a.getSeverity()) {
                    case CRITICAL:
                        criticalAlerts++;
                        break;
                    case HIGH:
                        highAlerts++;
                        break;
                    default:
                        break;
                }
            }
        }

        SAMResultDTO sam = SAMResultDTO.builder()
                .totalAlerts(alertDTOs.size())
                .criticalCount(criticalAlerts)
                .highCount(highAlerts)
                .aSec(report.getASec())
                .alerts(alertDTOs)
                .build();

        SPMResultDTO spm;
        try {
            SwaggerParseResult r1 = new OpenAPIParser().readContents(report.getOldSpec().getRawContent(), null, null);
            SwaggerParseResult r2 = new OpenAPIParser().readContents(report.getNewSpec().getRawContent(), null, null);
            if (r1.getOpenAPI() != null && r2.getOpenAPI() != null) {
                spm = spmService.analyze(r1.getOpenAPI(), r2.getOpenAPI());
            } else {
                spm = SPMResultDTO.builder()
                        .addedEndpoints(new ArrayList<>())
                        .removedEndpoints(new ArrayList<>())
                        .changedEndpoints(new ArrayList<>())
                        .flowChanged(false)
                        .dApi(report.getDApi())
                        .build();
            }
        } catch (Exception e) {
            spm = SPMResultDTO.builder()
                    .addedEndpoints(new ArrayList<>())
                    .removedEndpoints(new ArrayList<>())
                    .changedEndpoints(new ArrayList<>())
                    .flowChanged(false)
                    .dApi(report.getDApi())
                    .build();
        }

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("w1_dStruct", w1 * report.getDStruct());
        breakdown.put("w2_dApi", w2 * report.getDApi());
        breakdown.put("w3_aSec", w3 * report.getASec());

        ImpactScoreDTO impactScore = ImpactScoreDTO.builder()
                .sTotal(report.getSTotal())
                .riskLevel(report.getRiskLevel() != null ? report.getRiskLevel().name() : null)
                .breakdown(breakdown)
                .build();

        return AnalysisResponseDTO.builder()
                .reportId(report.getId())
                .oldVersion(report.getOldSpec() != null ? report.getOldSpec().getVersionLabel() : null)
                .newVersion(report.getNewSpec() != null ? report.getNewSpec().getVersionLabel() : null)
                .sgm(sgm)
                .spm(spm)
                .sam(sam)
                .impactScore(impactScore)
                .build();
    }
}
