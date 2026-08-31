package com.apicia.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.apicia.model.dto.*;
import com.apicia.model.entity.*;
import com.apicia.repository.*;
import com.apicia.service.extraction.DependencyScannerService;
import com.apicia.service.scoring.ImpactScoringService;
import com.apicia.service.sgm.SGMService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AnalysisServiceTest {

    private SpecVersionRepository specVersionRepository;
    private AnalysisReportRepository analysisReportRepository;
    private ViolationRepository violationRepository;
    private SGMService sgmService;
    private ImpactScoringService impactScoringService;
    private ClientDependencyRepository clientDependencyRepository;
    private DependencyScannerService dependencyScannerService;

    private AnalysisService analysisService;

    @BeforeEach
    public void setUp() {
        specVersionRepository = mock(SpecVersionRepository.class);
        analysisReportRepository = mock(AnalysisReportRepository.class);
        violationRepository = mock(ViolationRepository.class);
        sgmService = mock(SGMService.class);
        impactScoringService = mock(ImpactScoringService.class);
        clientDependencyRepository = mock(ClientDependencyRepository.class);
        dependencyScannerService = new DependencyScannerService(mock(ClientProjectRepository.class), clientDependencyRepository);

        analysisService = new AnalysisService(
                specVersionRepository,
                analysisReportRepository,
                violationRepository,
                sgmService,
                impactScoringService,
                clientDependencyRepository,
                dependencyScannerService
        );
    }

    @Test
    public void testCalculateBlastRadius() {
        ClientProject clientProject = ClientProject.builder()
                .projectName("UserBff")
                .build();
        ClientDependency dep = ClientDependency.builder()
                .clientProject(clientProject)
                .filePath("src/main/java/UserClient.java")
                .lineNumber(42)
                .httpMethod("DELETE")
                .normalizedPath("/api/v1/users/{}")
                .build();

        when(clientDependencyRepository.findByHttpMethodIgnoreCaseAndNormalizedPath("DELETE", "/api/v1/users/{}"))
                .thenReturn(List.of(dep));

        AnalysisReport report = AnalysisReport.builder()
                .id(1L)
                .dStruct(0.5)
                .sTotal(50.0)
                .riskLevel(RiskLevel.MEDIUM)
                .build();
        when(analysisReportRepository.findById(1L)).thenReturn(java.util.Optional.of(report));

        Violation violation = Violation.builder()
                .ruleId("SGM-003")
                .severity(ViolationSeverity.BREAKING)
                .endpoint("/api/v1/users/{id}")
                .message("Endpoint DELETE /api/v1/users/{id} was removed")
                .build();
        when(violationRepository.findByReportId(1L)).thenReturn(List.of(violation));

        AnalysisResponseDTO response = analysisService.getReport(1L);

        assertNotNull(response);
        assertNotNull(response.getBlastRadius());
        assertEquals(1, response.getBlastRadius().getTotalImpactedConsumers());
        assertEquals(1, response.getBlastRadius().getImpactedEndpoints().size());

        ImpactedEndpointDTO ie = response.getBlastRadius().getImpactedEndpoints().get(0);
        assertEquals("/api/v1/users/{id}", ie.getEndpoint());
        assertEquals("DELETE", ie.getMethod());
        assertEquals(1, ie.getConsumers().size());
        assertEquals("UserBff", ie.getConsumers().get(0).getProjectName());
        assertEquals(42, ie.getConsumers().get(0).getLine());
    }

    @Test
    public void testCompareInMemory() {
        SpecVersion oldSpec = SpecVersion.builder()
                .id(1L)
                .versionLabel("v1")
                .rawContent("{\"openapi\": \"3.0.0\"}")
                .build();
        SpecVersion newSpec = SpecVersion.builder()
                .id(2L)
                .versionLabel("v2")
                .rawContent("{\"openapi\": \"3.0.0\"}")
                .build();

        SGMResultDTO sgmResult = SGMResultDTO.builder()
                .violations(new ArrayList<>())
                .dStruct(0.0)
                .build();
        when(sgmService.analyze(any(), any())).thenReturn(sgmResult);

        ImpactScoreDTO impactScore = ImpactScoreDTO.builder()
                .sTotal(0.0)
                .riskLevel("LOW")
                .build();
        when(impactScoringService.calculate(0.0)).thenReturn(impactScore);

        AnalysisResponseDTO response = analysisService.compareInMemory(oldSpec, newSpec);

        assertNotNull(response);
        assertNull(response.getReportId());
        assertEquals("v1", response.getOldVersion());
        assertEquals("v2", response.getNewVersion());
        assertEquals(1L, response.getOldSpecId());
        assertEquals(2L, response.getNewSpecId());

        verify(analysisReportRepository, never()).save(any());
        verify(violationRepository, never()).save(any());
    }
}
