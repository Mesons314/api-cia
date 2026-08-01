package com.apicia.service;

import com.apicia.exception.InvalidSpecException;
import com.apicia.exception.ResourceNotFoundException;
import com.apicia.model.dto.*;
import com.apicia.model.entity.*;
import com.apicia.repository.AnalysisReportRepository;
import com.apicia.repository.SpecVersionRepository;
import com.apicia.repository.ViolationRepository;
import com.apicia.service.scoring.ImpactScoringService;
import com.apicia.service.sgm.SGMService;
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
    private final SGMService sgmService;
    private final ImpactScoringService impactScoringService;

    @Value("${cia.weights.w1}")
    private double w1;

    public AnalysisService(
            SpecVersionRepository specVersionRepository,
            AnalysisReportRepository analysisReportRepository,
            ViolationRepository violationRepository,
            SGMService sgmService,
            ImpactScoringService impactScoringService) {
        this.specVersionRepository = specVersionRepository;
        this.analysisReportRepository = analysisReportRepository;
        this.violationRepository = violationRepository;
        this.sgmService = sgmService;
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
        ImpactScoreDTO scoreResult = impactScoringService.calculate(sgmResult.getDStruct());

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
                        .build();
                violationRepository.save(violation);
            }
        }

        return AnalysisResponseDTO.builder()
                .reportId(report.getId())
                .oldVersion(oldSpec.getVersionLabel())
                .newVersion(newSpec.getVersionLabel())
                .sgm(sgmResult)
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

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("w1_dStruct", w1 * report.getDStruct());

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
                .impactScore(impactScore)
                .build();
    }
}

