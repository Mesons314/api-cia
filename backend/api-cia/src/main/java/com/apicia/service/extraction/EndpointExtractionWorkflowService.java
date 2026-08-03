package com.apicia.service.extraction;

import com.apicia.config.EndpointExtractionProperties;
import com.apicia.exception.InvalidSpecException;
import com.apicia.model.dto.*;
import com.apicia.model.entity.SpecVersion;
import com.apicia.repository.SpecVersionRepository;
import com.apicia.service.AnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointExtractionWorkflowService {

    private static final String SNAPSHOT_FILE_NAME = "static-extracted-openapi.json";

    private final EndpointExtractionProperties properties;
    private final StaticEndpointExtractionService extractionService;
    private final SpecVersionRepository specVersionRepository;
    private final AnalysisService analysisService;
    private final ObjectMapper objectMapper;

    public EndpointExtractionWorkflowService(
            EndpointExtractionProperties properties,
            StaticEndpointExtractionService extractionService,
            SpecVersionRepository specVersionRepository,
            AnalysisService analysisService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.extractionService = extractionService;
        this.specVersionRepository = specVersionRepository;
        this.analysisService = analysisService;
        this.objectMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public Map<String, Object> generateOpenApi() {
        return extractionService.generateOpenApi();
    }

    public String generateOpenApiJson() {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(generateOpenApi());
        } catch (JsonProcessingException ex) {
            throw new InvalidSpecException("Unable to serialize extracted OpenAPI contract", ex);
        }
    }

    @Transactional
    public ExtractionSnapshotDTO extractSaveAndAnalyze(boolean analyze) {
        migrateNullProjectsAndVersions();
        String rawContent = generateOpenApiJson();
        OpenAPI openAPI = parse(rawContent);
        int totalEndpoints = countEndpoints(openAPI);

        String projectId = properties.getProjectId();
        String version = properties.getVersion();

        List<SpecVersion> projectSpecs = specVersionRepository.findByProjectId(projectId);
        SpecVersion latestSnapshot = projectSpecs.stream()
                .filter(s -> version.equals(s.getVersion()))
                .max((s1, s2) -> {
                    if (s1.getUploadedAt() != null && s2.getUploadedAt() != null) {
                        return s1.getUploadedAt().compareTo(s2.getUploadedAt());
                    }
                    return s1.getId().compareTo(s2.getId());
                })
                .orElse(null);

        if (latestSnapshot != null && rawContent.equals(latestSnapshot.getRawContent())) {
            SpecVersion baseSpec = findBaseSpec(latestSnapshot, projectSpecs);
            Long reportId = null;
            if (analyze && baseSpec != null) {
                printDebugContracts(baseSpec, latestSnapshot);
                AnalysisResponseDTO response = analysisService.compare(AnalysisRequestDTO.builder()
                        .oldSpecId(baseSpec.getId())
                        .newSpecId(latestSnapshot.getId())
                        .build());
                reportId = response.getReportId();
            }
            return ExtractionSnapshotDTO.builder()
                    .specId(latestSnapshot.getId())
                    .versionLabel(latestSnapshot.getVersionLabel())
                    .totalEndpoints(latestSnapshot.getTotalEndpoints())
                    .saved(false)
                    .analyzed(analyze && baseSpec != null)
                    .analysisReportId(reportId)
                    .extractedAt(latestSnapshot.getUploadedAt())
                    .build();
        }

        SpecVersion saved = specVersionRepository.save(SpecVersion.builder()
                .versionLabel(versionLabel(projectId, version))
                .projectId(projectId)
                .version(version)
                .fileName(SNAPSHOT_FILE_NAME)
                .rawContent(rawContent)
                .totalEndpoints(totalEndpoints)
                .build());
        projectSpecs.add(saved);

        boolean analyzed = false;
        Long reportId = null;
        SpecVersion baseSpec = findBaseSpec(saved, projectSpecs);
        if (analyze && baseSpec != null) {
            printDebugContracts(baseSpec, saved);
            AnalysisResponseDTO response = analysisService.compare(AnalysisRequestDTO.builder()
                    .oldSpecId(baseSpec.getId())
                    .newSpecId(saved.getId())
                    .build());
            analyzed = true;
            reportId = response.getReportId();
        }

        return ExtractionSnapshotDTO.builder()
                .specId(saved.getId())
                .versionLabel(saved.getVersionLabel())
                .totalEndpoints(saved.getTotalEndpoints())
                .saved(true)
                .analyzed(analyzed)
                .analysisReportId(reportId)
                .extractedAt(saved.getUploadedAt() != null ? saved.getUploadedAt() : LocalDateTime.now())
                .build();
    }

    @Transactional
    public AnalysisResponseDTO generateAndAnalyze() {
        migrateNullProjectsAndVersions();
        String rawContent = generateOpenApiJson();
        OpenAPI openAPI = parse(rawContent);
        int totalEndpoints = countEndpoints(openAPI);

        String projectId = properties.getProjectId();
        String version = properties.getVersion();

        List<SpecVersion> projectSpecs = specVersionRepository.findByProjectId(projectId);
        SpecVersion latestSnapshot = projectSpecs.stream()
                .filter(s -> version.equals(s.getVersion()))
                .max((s1, s2) -> {
                    if (s1.getUploadedAt() != null && s2.getUploadedAt() != null) {
                        return s1.getUploadedAt().compareTo(s2.getUploadedAt());
                    }
                    return s1.getId().compareTo(s2.getId());
                })
                .orElse(null);

        SpecVersion currentSpec;
        if (latestSnapshot != null && rawContent.equals(latestSnapshot.getRawContent())) {
            currentSpec = latestSnapshot;
        } else {
            currentSpec = SpecVersion.builder()
                    .versionLabel(versionLabel(projectId, version))
                    .projectId(projectId)
                    .version(version)
                    .fileName(SNAPSHOT_FILE_NAME)
                    .rawContent(rawContent)
                    .totalEndpoints(totalEndpoints)
                    .build();
            currentSpec = specVersionRepository.save(currentSpec);
            projectSpecs.add(currentSpec);
        }

        SpecVersion baseSpec = findBaseSpec(currentSpec, projectSpecs);

        if (baseSpec != null) {
            printDebugContracts(baseSpec, currentSpec);
            return analysisService.compare(AnalysisRequestDTO.builder()
                    .oldSpecId(baseSpec.getId())
                    .newSpecId(currentSpec.getId())
                    .build());
        } else {
            return AnalysisResponseDTO.builder()
                    .reportId(null)
                    .oldVersion(null)
                    .newVersion(currentSpec.getVersionLabel())
                    .sgm(SGMResultDTO.builder()
                            .totalViolations(0)
                            .breakingCount(0)
                            .warningCount(0)
                            .infoCount(0)
                            .violations(new ArrayList<>())
                            .build())
                    .impactScore(ImpactScoreDTO.builder()
                            .sTotal(0.0)
                            .riskLevel("LOW")
                            .breakdown(new HashMap<>())
                            .build())
                    .build();
        }
    }

    private void printDebugContracts(SpecVersion baseSpec, SpecVersion currentSpec) {
        System.out.println("===== Base API Contract =====");
        System.out.println("Snapshot/Version ID: " + (baseSpec != null ? baseSpec.getId() : "null"));
        System.out.println("Project ID: " + (baseSpec != null ? baseSpec.getProjectId() : "null"));
        System.out.println("Timestamp: " + (baseSpec != null ? baseSpec.getUploadedAt() : "null"));
        System.out.println("Content:\n" + (baseSpec != null ? baseSpec.getRawContent() : ""));
        System.out.println();
        System.out.println("===== Current API Contract =====");
        System.out.println("Content:\n" + (currentSpec != null ? currentSpec.getRawContent() : ""));
        System.out.println("=================================");
    }

    public SpecVersion findBaseSpec(SpecVersion currentSpec, List<SpecVersion> projectSpecs) {
        if (projectSpecs == null || projectSpecs.isEmpty()) {
            return null;
        }

        List<SpecVersion> candidates = projectSpecs.stream()
                .filter(s -> s.getId() != null && !s.getId().equals(currentSpec.getId()))
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        List<SpecVersion> sameVersionOlder = candidates.stream()
                .filter(s -> currentSpec.getVersion().equals(s.getVersion()))
                .filter(s -> s.getUploadedAt() != null && currentSpec.getUploadedAt() != null &&
                             (s.getUploadedAt().isBefore(currentSpec.getUploadedAt()) || 
                              (s.getUploadedAt().equals(currentSpec.getUploadedAt()) && s.getId() < currentSpec.getId())))
                .toList();

        if (!sameVersionOlder.isEmpty()) {
            return sameVersionOlder.stream()
                    .max((s1, s2) -> {
                        if (s1.getUploadedAt() != null && s2.getUploadedAt() != null) {
                            int c = s1.getUploadedAt().compareTo(s2.getUploadedAt());
                            if (c != 0) return c;
                        }
                        return s1.getId().compareTo(s2.getId());
                    })
                    .orElse(null);
        }

        List<SpecVersion> predecessors = candidates.stream()
                .filter(s -> com.apicia.util.VersionComparator.compareVersions(s.getVersion(), currentSpec.getVersion()) < 0)
                .toList();

        if (!predecessors.isEmpty()) {
            String highestVersion = predecessors.stream()
                    .map(SpecVersion::getVersion)
                    .max(com.apicia.util.VersionComparator::compareVersions)
                    .orElse(null);

            return predecessors.stream()
                    .filter(s -> s.getVersion().equals(highestVersion))
                    .max((s1, s2) -> {
                        if (s1.getUploadedAt() != null && s2.getUploadedAt() != null) {
                            int c = s1.getUploadedAt().compareTo(s2.getUploadedAt());
                            if (c != 0) return c;
                        }
                        return s1.getId().compareTo(s2.getId());
                    })
                    .orElse(null);
        }

        return candidates.stream()
                .filter(s -> s.getUploadedAt() != null && currentSpec.getUploadedAt() != null &&
                             (s.getUploadedAt().isBefore(currentSpec.getUploadedAt()) || 
                              (s.getUploadedAt().equals(currentSpec.getUploadedAt()) && s.getId() < currentSpec.getId())))
                .max((s1, s2) -> {
                    if (s1.getUploadedAt() != null && s2.getUploadedAt() != null) {
                        int c = s1.getUploadedAt().compareTo(s2.getUploadedAt());
                        if (c != 0) return c;
                    }
                    return s1.getId().compareTo(s2.getId());
                })
                .orElse(null);
    }

    private void migrateNullProjectsAndVersions() {
        List<SpecVersion> allSpecs = specVersionRepository.findAll();
        boolean changed = false;
        for (SpecVersion spec : allSpecs) {
            boolean rowChanged = false;
            if (spec.getProjectId() == null) {
                spec.setProjectId(resolveProjectId(spec));
                rowChanged = true;
            }
            if (spec.getVersion() == null) {
                spec.setVersion(resolveVersion(spec));
                rowChanged = true;
            }
            if (rowChanged) {
                specVersionRepository.save(spec);
                changed = true;
            }
        }
        if (changed) {
            System.out.println("Migrated historical SpecVersion records to include projectId and version.");
        }
    }

    private String resolveProjectId(SpecVersion spec) {
        try {
            SwaggerParseResult parseResult = new OpenAPIParser().readContents(spec.getRawContent(), null, null);
            OpenAPI openAPI = parseResult.getOpenAPI();
            if (openAPI != null) {
                if (openAPI.getExtensions() != null && openAPI.getExtensions().containsKey("x-project-id")) {
                    return String.valueOf(openAPI.getExtensions().get("x-project-id"));
                } else if (openAPI.getInfo() != null) {
                    if (openAPI.getInfo().getExtensions() != null && openAPI.getInfo().getExtensions().containsKey("x-project-id")) {
                        return String.valueOf(openAPI.getInfo().getExtensions().get("x-project-id"));
                    } else if (openAPI.getInfo().getTitle() != null) {
                        return openAPI.getInfo().getTitle();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        String label = spec.getVersionLabel();
        if (label != null && label.contains("-")) {
            String[] parts = label.split("-");
            if (parts.length >= 3) {
                return parts[0];
            }
        }
        return "default-project";
    }

    private String resolveVersion(SpecVersion spec) {
        try {
            SwaggerParseResult parseResult = new OpenAPIParser().readContents(spec.getRawContent(), null, null);
            OpenAPI openAPI = parseResult.getOpenAPI();
            if (openAPI != null && openAPI.getInfo() != null && openAPI.getInfo().getVersion() != null) {
                return openAPI.getInfo().getVersion();
            }
        } catch (Exception e) {
            // ignore
        }

        String label = spec.getVersionLabel();
        if (label != null && label.contains("-")) {
            String[] parts = label.split("-");
            if (parts.length >= 3) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length - 2; i++) {
                    if (sb.length() > 0) sb.append("-");
                    sb.append(parts[i]);
                }
                String ver = sb.toString();
                if (!ver.isEmpty()) {
                    return ver;
                }
                return parts[1];
            }
        }
        return "local";
    }

    private OpenAPI parse(String rawContent) {
        SwaggerParseResult parseResult = new OpenAPIParser().readContents(rawContent, null, null);
        OpenAPI openAPI = parseResult.getOpenAPI();
        if (openAPI == null) {
            throw new InvalidSpecException("Static extractor generated invalid OpenAPI content");
        }
        return openAPI;
    }

    private String versionLabel(String projectId, String version) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return projectId + "-" + version + "-" + timestamp;
    }

    private int countEndpoints(OpenAPI spec) {
        if (spec == null || spec.getPaths() == null) {
            return 0;
        }
        int count = 0;
        for (PathItem pathItem : spec.getPaths().values()) {
            if (pathItem == null) continue;
            if (pathItem.getGet() != null) count++;
            if (pathItem.getPost() != null) count++;
            if (pathItem.getPut() != null) count++;
            if (pathItem.getDelete() != null) count++;
            if (pathItem.getPatch() != null) count++;
        }
        return count;
    }
}

