package com.apicia.service.extraction;

import com.apicia.config.EndpointExtractionProperties;
import com.apicia.exception.InvalidSpecException;
import com.apicia.model.dto.AnalysisRequestDTO;
import com.apicia.model.dto.AnalysisResponseDTO;
import com.apicia.model.dto.ExtractionSnapshotDTO;
import com.apicia.model.entity.SpecVersion;
import com.apicia.repository.SpecVersionRepository;
import com.apicia.service.AnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        this.objectMapper = objectMapper;
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
        String rawContent = generateOpenApiJson();
        OpenAPI openAPI = parse(rawContent);
        int totalEndpoints = countEndpoints(openAPI);

        SpecVersion previous = specVersionRepository.findFirstByFileNameOrderByUploadedAtDesc(SNAPSHOT_FILE_NAME).orElse(null);
        if (previous != null && rawContent.equals(previous.getRawContent())) {
            return ExtractionSnapshotDTO.builder()
                    .specId(previous.getId())
                    .versionLabel(previous.getVersionLabel())
                    .totalEndpoints(previous.getTotalEndpoints())
                    .saved(false)
                    .analyzed(false)
                    .extractedAt(previous.getUploadedAt())
                    .build();
        }

        SpecVersion saved = specVersionRepository.save(SpecVersion.builder()
                .versionLabel(versionLabel())
                .fileName(SNAPSHOT_FILE_NAME)
                .rawContent(rawContent)
                .totalEndpoints(totalEndpoints)
                .build());

        boolean analyzed = false;
        Long reportId = null;
        if (analyze && previous != null) {
            AnalysisResponseDTO response = analysisService.compare(AnalysisRequestDTO.builder()
                    .oldSpecId(previous.getId())
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

    private OpenAPI parse(String rawContent) {
        SwaggerParseResult parseResult = new OpenAPIParser().readContents(rawContent, null, null);
        OpenAPI openAPI = parseResult.getOpenAPI();
        if (openAPI == null) {
            throw new InvalidSpecException("Static extractor generated invalid OpenAPI content");
        }
        return openAPI;
    }

    private String versionLabel() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return properties.getProjectId() + "-" + properties.getVersion() + "-" + timestamp;
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
