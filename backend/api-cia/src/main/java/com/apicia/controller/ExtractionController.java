package com.apicia.controller;

import com.apicia.model.dto.AnalysisResponseDTO;
import com.apicia.model.dto.ExtractionSnapshotDTO;
import com.apicia.model.extraction.ExtractedEndpoint;
import com.apicia.service.extraction.EndpointExtractionWorkflowService;
import com.apicia.service.extraction.StaticEndpointExtractionService;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/extraction")
@CrossOrigin(origins = "*")
public class ExtractionController {

    private final StaticEndpointExtractionService extractionService;
    private final EndpointExtractionWorkflowService workflowService;

    public ExtractionController(
            StaticEndpointExtractionService extractionService,
            EndpointExtractionWorkflowService workflowService
    ) {
        this.extractionService = extractionService;
        this.workflowService = workflowService;
    }

    @GetMapping("/endpoints")
    public List<ExtractedEndpoint> endpoints() {
        return extractionService.extractEndpoints();
    }

    @GetMapping(value = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisResponseDTO openApiJson() {
        return workflowService.generateAndAnalyze();
    }

    @GetMapping(value = "/openapi-raw.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> openApiRawJson() {
        return workflowService.generateOpenApi();
    }

    @PostMapping("/snapshot")
    public ExtractionSnapshotDTO snapshot(@RequestParam(name = "analyze", defaultValue = "true") boolean analyze) {
        return workflowService.extractSaveAndAnalyze(analyze);
    }
}


