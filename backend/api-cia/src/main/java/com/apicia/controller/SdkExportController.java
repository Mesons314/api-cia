package com.apicia.controller;

import com.apicia.model.entity.SpecVersion;
import com.apicia.repository.SpecVersionRepository;
import com.apicia.service.sdk.SdkGeneratorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/specs")
@CrossOrigin(origins = "*")
public class SdkExportController {

    private final SpecVersionRepository specVersionRepository;
    private final SdkGeneratorService sdkGeneratorService;

    public SdkExportController(SpecVersionRepository specVersionRepository, SdkGeneratorService sdkGeneratorService) {
        this.specVersionRepository = specVersionRepository;
        this.sdkGeneratorService = sdkGeneratorService;
    }

    /**
     * Download client SDK (TypeScript, Dart, Python, Java, etc.) as a ZIP package for a saved SpecVersion.
     */
    @GetMapping("/{id}/download-sdk")
    public ResponseEntity<byte[]> downloadSdkForSpec(
            @PathVariable Long id,
            @RequestParam(defaultValue = "typescript-axios") String language
    ) {
        SpecVersion spec = specVersionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SpecVersion not found: " + id));

        try {
            byte[] zipBytes = sdkGeneratorService.generateSdk(spec.getRawContent(), language);
            String cleanVersion = spec.getVersion() != null ? spec.getVersion().replaceAll("[^a-zA-Z0-9.-]", "_") : "spec-" + id;
            String filename = cleanVersion + "-" + language + "-sdk.zip";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zipBytes);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate SDK: " + e.getMessage(), e);
        }
    }

    /**
     * Generate client SDK dynamically from a raw OpenAPI JSON payload (for CI/CD pipelines).
     */
    @PostMapping(value = "/generate-sdk", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<byte[]> generateSdkFromRawSpec(
            @RequestBody String rawOpenApiSpec,
            @RequestParam(defaultValue = "typescript-axios") String language
    ) {
        try {
            byte[] zipBytes = sdkGeneratorService.generateSdk(rawOpenApiSpec, language);
            String filename = "client-" + language + "-sdk.zip";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zipBytes);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to generate SDK: " + e.getMessage(), e);
        }
    }

    /**
     * Export Postman Collection v2.1 JSON file for a saved SpecVersion.
     */
    @GetMapping("/{id}/export-postman")
    public ResponseEntity<byte[]> exportPostmanForSpec(@PathVariable Long id) {
        SpecVersion spec = specVersionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SpecVersion not found: " + id));

        try {
            byte[] postmanBytes = sdkGeneratorService.exportPostmanCollectionBytes(spec.getRawContent());
            String cleanVersion = spec.getVersion() != null ? spec.getVersion().replaceAll("[^a-zA-Z0-9.-]", "_") : "spec-" + id;
            String filename = cleanVersion + "-postman-collection.json";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(postmanBytes);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to export Postman collection: " + e.getMessage(), e);
        }
    }

    /**
     * Convert raw OpenAPI specification JSON to Postman Collection v2.1 JSON directly.
     */
    @PostMapping(value = "/export-postman", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<Map<String, Object>> exportPostmanFromRawSpec(@RequestBody String rawOpenApiSpec) {
        try {
            Map<String, Object> collection = sdkGeneratorService.exportPostmanCollection(rawOpenApiSpec);
            return ResponseEntity.ok(collection);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to convert to Postman collection: " + e.getMessage(), e);
        }
    }
}
