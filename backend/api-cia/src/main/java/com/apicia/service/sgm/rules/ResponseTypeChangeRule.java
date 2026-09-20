package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ResponseTypeChangeRule implements DesignRule {

    @Override
    public String getRuleId() {
        return "SGM-013";
    }

    @Override
    public String getRuleName() {
        return "Response Type / Schema Mutation Check";
    }

    @Override
    public List<ViolationDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec) {
        if (oldSpec == null || oldSpec.getPaths() == null || newSpec == null || newSpec.getPaths() == null) {
            return Collections.emptyList();
        }

        List<ViolationDTO> violations = new ArrayList<>();

        for (Map.Entry<String, PathItem> entry : oldSpec.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem oldPathItem = entry.getValue();
            if (oldPathItem == null) continue;

            PathItem newPathItem = newSpec.getPaths().get(path);
            if (newPathItem == null) continue;

            checkMethodResponses(violations, path, "GET", oldPathItem.getGet(), newPathItem.getGet());
            checkMethodResponses(violations, path, "POST", oldPathItem.getPost(), newPathItem.getPost());
            checkMethodResponses(violations, path, "PUT", oldPathItem.getPut(), newPathItem.getPut());
            checkMethodResponses(violations, path, "DELETE", oldPathItem.getDelete(), newPathItem.getDelete());
            checkMethodResponses(violations, path, "PATCH", oldPathItem.getPatch(), newPathItem.getPatch());
        }

        return violations;
    }

    private void checkMethodResponses(List<ViolationDTO> violations, String path, String method, Operation oldOp, Operation newOp) {
        if (oldOp == null || newOp == null || oldOp.getResponses() == null) return;

        ApiResponses oldResponses = oldOp.getResponses();
        ApiResponses newResponses = newOp.getResponses();

        for (Map.Entry<String, ApiResponse> respEntry : oldResponses.entrySet()) {
            String statusCode = respEntry.getKey();
            ApiResponse oldResp = respEntry.getValue();
            if (oldResp == null) continue;

            String oldSchema = extractResponseSchema(oldResp);

            // Check if status code was completely removed in new spec
            if (newResponses == null || !newResponses.containsKey(statusCode)) {
                // If it's a success response with a return schema that was removed
                if (isSuccessStatusCode(statusCode) && oldSchema != null) {
                    violations.add(ViolationDTO.builder()
                            .ruleId(getRuleId())
                            .severity("BREAKING")
                            .endpoint(path)
                            .message("HTTP " + statusCode + " response with payload '" + oldSchema + "' was removed from " + method + " " + path)
                            .oldValue(oldSchema)
                            .newValue("REMOVED")
                            .build());
                }
                continue;
            }

            ApiResponse newResp = newResponses.get(statusCode);
            String newSchema = extractResponseSchema(newResp);

            if (oldSchema != null && newSchema == null) {
                violations.add(ViolationDTO.builder()
                        .ruleId(getRuleId())
                        .severity("BREAKING")
                        .endpoint(path)
                        .message("Response body schema for HTTP " + statusCode + " in " + method + " " + path + " of type '" + oldSchema + "' was removed")
                        .oldValue(oldSchema)
                        .newValue("NONE")
                        .build());
            } else if (oldSchema != null && newSchema != null && !oldSchema.equals(newSchema)) {
                violations.add(ViolationDTO.builder()
                        .ruleId(getRuleId())
                        .severity("BREAKING")
                        .endpoint(path)
                        .message("Response payload return type for HTTP " + statusCode + " in " + method + " " + path + " changed from '" + oldSchema + "' to '" + newSchema + "'")
                        .oldValue(oldSchema)
                        .newValue(newSchema)
                        .build());
            }
        }
    }

    private boolean isSuccessStatusCode(String statusCode) {
        return statusCode.startsWith("2") || statusCode.equalsIgnoreCase("default");
    }

    private String extractResponseSchema(ApiResponse response) {
        if (response == null || response.getContent() == null) {
            return null;
        }

        for (MediaType mediaType : response.getContent().values()) {
            if (mediaType == null || mediaType.getSchema() == null) continue;
            return resolveSchemaName(mediaType.getSchema());
        }

        return null;
    }

    private String resolveSchemaName(Schema<?> schema) {
        if (schema == null) return null;

        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            int lastSlash = ref.lastIndexOf('/');
            return lastSlash >= 0 ? ref.substring(lastSlash + 1) : ref;
        }

        if (schema instanceof ArraySchema || "array".equalsIgnoreCase(schema.getType())) {
            Schema<?> items = schema.getItems();
            if (items != null) {
                String itemType = resolveSchemaName(items);
                return "List<" + (itemType != null ? itemType : "Object") + ">";
            }
            return "List<Object>";
        }

        if (schema.getType() != null) {
            if (schema.getFormat() != null) {
                return schema.getType() + "(" + schema.getFormat() + ")";
            }
            return schema.getType();
        }

        return "Object";
    }
}
