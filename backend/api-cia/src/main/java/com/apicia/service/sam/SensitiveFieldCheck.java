package com.apicia.service.sam;

import com.apicia.model.dto.SecurityAlertDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SensitiveFieldCheck implements SecurityCheck {

    @Override
    public String getCheckId() {
        return "SAM-002";
    }

    @Override
    public List<SecurityAlertDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec) {
        if (newSpec == null || newSpec.getPaths() == null) {
            return Collections.emptyList();
        }

        List<SecurityAlertDTO> alerts = new ArrayList<>();
        String[] keywords = {"password", "token", "secret", "key", "hash", "ssn", "credit", "cvv", "pin", "privatekey", "apikey", "accesskey"};
        String[] statusCodes = {"200", "201"};
        String[] methods = {"GET", "POST", "PUT", "DELETE", "PATCH"};

        for (String path : newSpec.getPaths().keySet()) {
            if (path == null) continue;
            for (String method : methods) {
                for (String statusCode : statusCodes) {
                    Set<String> newFields = getResponseFields(newSpec, path, method, statusCode);
                    if (newFields.isEmpty()) continue;

                    Set<String> oldFields = getResponseFields(oldSpec, path, method, statusCode);

                    for (String fieldName : newFields) {
                        if (fieldName == null) continue;
                        boolean isSensitive = false;
                        String lowerFieldName = fieldName.toLowerCase();
                        for (String keyword : keywords) {
                            if (lowerFieldName.contains(keyword)) {
                                isSensitive = true;
                                break;
                            }
                        }

                        if (isSensitive) {
                            if (!oldFields.contains(fieldName)) {
                                alerts.add(SecurityAlertDTO.builder()
                                        .checkId(getCheckId())
                                        .severity("HIGH")
                                        .endpoint(method + " " + path)
                                        .description("Sensitive field '" + fieldName + "' is newly exposed in response of " + path + ". Review if this is intentional")
                                        .build());
                            }
                        }
                    }
                }
            }
        }
        return alerts;
    }

    private Set<String> getResponseFields(OpenAPI spec, String path, String method, String statusCode) {
        Set<String> fields = new HashSet<>();
        if (spec == null || spec.getPaths() == null) {
            return fields;
        }
        PathItem pathItem = spec.getPaths().get(path);
        if (pathItem == null) return fields;

        Operation op = null;
        switch (method) {
            case "GET": op = pathItem.getGet(); break;
            case "POST": op = pathItem.getPost(); break;
            case "PUT": op = pathItem.getPut(); break;
            case "DELETE": op = pathItem.getDelete(); break;
            case "PATCH": op = pathItem.getPatch(); break;
        }
        if (op == null || op.getResponses() == null) return fields;

        ApiResponse response = op.getResponses().get(statusCode);
        if (response == null) return fields;

        if (response.getContent() != null) {
            for (MediaType mediaType : response.getContent().values()) {
                if (mediaType == null || mediaType.getSchema() == null) continue;
                Schema<?> schema = mediaType.getSchema();
                
                extractProperties(schema, fields);
                
                if ("array".equals(schema.getType()) && schema.getItems() != null) {
                    extractProperties(schema.getItems(), fields);
                }
            }
        }
        return fields;
    }

    private void extractProperties(Schema<?> schema, Set<String> fields) {
        if (schema == null) return;
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            fields.addAll(properties.keySet());
        }
    }
}
