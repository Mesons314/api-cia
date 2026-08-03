package com.apicia.controller;

import com.apicia.exception.InvalidSpecException;
import com.apicia.exception.ResourceNotFoundException;
import com.apicia.model.dto.SpecVersionDTO;
import com.apicia.model.entity.SpecVersion;
import com.apicia.repository.SpecVersionRepository;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/specs")
@CrossOrigin(origins = "*")
public class SpecController {

    private final SpecVersionRepository specVersionRepository;

    public SpecController(SpecVersionRepository specVersionRepository) {
        this.specVersionRepository = specVersionRepository;
    }

    @PostMapping("/upload")
    public SpecVersionDTO upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("versionLabel") String versionLabel) {
        
        String content;
        try {
            content = new String(file.getBytes());
        } catch (IOException e) {
            throw new InvalidSpecException("Could not read uploaded file", e);
        }

        SwaggerParseResult parseResult = new OpenAPIParser().readContents(content, null, null);
        OpenAPI openAPI = parseResult.getOpenAPI();
        if (openAPI == null) {
            throw new InvalidSpecException("Not valid OpenAPI file");
        }

        String projectId = null;
        String version = null;
        if (openAPI.getExtensions() != null && openAPI.getExtensions().containsKey("x-project-id")) {
            projectId = String.valueOf(openAPI.getExtensions().get("x-project-id"));
        } else if (openAPI.getInfo() != null) {
            if (openAPI.getInfo().getExtensions() != null && openAPI.getInfo().getExtensions().containsKey("x-project-id")) {
                projectId = String.valueOf(openAPI.getInfo().getExtensions().get("x-project-id"));
            } else {
                projectId = openAPI.getInfo().getTitle();
            }
            version = openAPI.getInfo().getVersion();
        }

        if (projectId == null || projectId.trim().isEmpty()) {
            projectId = "default-project";
        }
        if (version == null || version.trim().isEmpty()) {
            version = versionLabel;
        }

        int totalEndpoints = countEndpoints(openAPI);

        SpecVersion specVersion = SpecVersion.builder()
                .versionLabel(versionLabel)
                .projectId(projectId)
                .version(version)
                .fileName(file.getOriginalFilename())
                .rawContent(content)
                .totalEndpoints(totalEndpoints)
                .build();

        SpecVersion saved = specVersionRepository.save(specVersion);

        return SpecVersionDTO.builder()
                .id(saved.getId())
                .versionLabel(saved.getVersionLabel())
                .fileName(saved.getFileName())
                .totalEndpoints(saved.getTotalEndpoints())
                .uploadedAt(saved.getUploadedAt())
                .build();
    }

    @GetMapping
    public List<SpecVersionDTO> getAll() {
        return specVersionRepository.findAllByOrderByUploadedAtDesc().stream()
                .map(spec -> SpecVersionDTO.builder()
                        .id(spec.getId())
                        .versionLabel(spec.getVersionLabel())
                        .fileName(spec.getFileName())
                        .totalEndpoints(spec.getTotalEndpoints())
                        .uploadedAt(spec.getUploadedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public SpecVersionDTO getById(@PathVariable("id") Long id) {
        SpecVersion spec = specVersionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SpecVersion not found with id: " + id));

        return SpecVersionDTO.builder()
                .id(spec.getId())
                .versionLabel(spec.getVersionLabel())
                .fileName(spec.getFileName())
                .totalEndpoints(spec.getTotalEndpoints())
                .uploadedAt(spec.getUploadedAt())
                .build();
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
