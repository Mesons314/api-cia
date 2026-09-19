package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ConsistentErrorResponseRule implements DesignRule {

    @Override
    public String getRuleId() {
        return "SGM-012";
    }

    @Override
    public String getRuleName() {
        return "Consistent Error Response Check";
    }

    @Override
    public List<ViolationDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec) {
        if (newSpec == null || newSpec.getPaths() == null) {
            return Collections.emptyList();
        }

        if (hasGlobalErrorHandler(newSpec)) {
            return Collections.emptyList();
        }

        List<ViolationDTO> violations = new ArrayList<>();

        for (Map.Entry<String, PathItem> entry : newSpec.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            if (pathItem == null) continue;

            checkOperation(violations, path, "GET", pathItem.getGet());
            checkOperation(violations, path, "POST", pathItem.getPost());
            checkOperation(violations, path, "PUT", pathItem.getPut());
            checkOperation(violations, path, "DELETE", pathItem.getDelete());
            checkOperation(violations, path, "PATCH", pathItem.getPatch());
        }

        return violations;
    }

    private void checkOperation(List<ViolationDTO> violations, String path, String httpMethod, Operation operation) {
        if (operation == null) return;

        String controller = null;
        String method = null;
        if (operation.getExtensions() != null) {
            String cClass = (String) operation.getExtensions().get("x-controller-class");
            if (cClass != null) {
                int idx = cClass.lastIndexOf('.');
                controller = idx >= 0 ? cClass.substring(idx + 1) : cClass;
            }
            method = (String) operation.getExtensions().get("x-controller-method");
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null || responses.isEmpty()) {
            violations.add(ViolationDTO.builder()
                    .ruleId(getRuleId())
                    .severity("WARNING")
                    .endpoint(httpMethod + " " + path)
                    .oldPath(path)
                    .newPath(path)
                    .controller(controller)
                    .method(method)
                    .message("Consistent Error Response: Endpoint '" + httpMethod + " " + path + "' does not document any responses.")
                    .oldValue("N/A")
                    .newValue("No Responses")
                    .build());
            return;
        }

        boolean hasErrorResponse = false;

        for (Map.Entry<String, ApiResponse> respEntry : responses.entrySet()) {
            String statusCode = respEntry.getKey();
            ApiResponse response = respEntry.getValue();
            if (statusCode == null || response == null) continue;

            if (isErrorCode(statusCode)) {
                hasErrorResponse = true;
                checkErrorStructure(violations, path, httpMethod, statusCode, response, controller, method);
            }
        }

        // Flag operations that have responses documented (e.g. 200 OK) but lack error responses
        if (!hasErrorResponse) {
            violations.add(ViolationDTO.builder()
                    .ruleId(getRuleId())
                    .severity("WARNING")
                    .endpoint(httpMethod + " " + path)
                    .oldPath(path)
                    .newPath(path)
                    .controller(controller)
                    .method(method)
                    .message("Consistent Error Response: Endpoint '" + httpMethod + " " + path + "' does not document any error payloads (e.g., 400 Bad Request, 404 Not Found, 500 Internal Server Error).")
                    .oldValue("N/A")
                    .newValue("Missing Error Response Docs")
                    .build());
        }
    }

    private boolean isErrorCode(String statusCode) {
        String code = statusCode.trim().toLowerCase(Locale.ROOT);
        if (code.equals("default") || code.startsWith("4") || code.startsWith("5")) {
            return true;
        }
        try {
            int numeric = Integer.parseInt(code);
            return numeric >= 400 && numeric < 600;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void checkErrorStructure(List<ViolationDTO> violations, String path, String httpMethod, String statusCode, ApiResponse response, String controller, String method) {
        if (response.getContent() == null || response.getContent().isEmpty()) {
            violations.add(ViolationDTO.builder()
                    .ruleId(getRuleId())
                    .severity("WARNING")
                    .endpoint(httpMethod + " " + path)
                    .oldPath(path)
                    .newPath(path)
                    .controller(controller)
                    .method(method)
                    .message("Consistent Error Response: Error response '" + statusCode + "' in endpoint '" + httpMethod + " " + path + "' has an empty body schema. It should return a standardized error structure (e.g., ErrorResponse).")
                    .oldValue("N/A")
                    .newValue("Empty Error Schema")
                    .build());
            return;
        }

        for (Map.Entry<String, MediaType> mediaTypeEntry : response.getContent().entrySet()) {
            String mediaTypeName = mediaTypeEntry.getKey();
            MediaType mediaType = mediaTypeEntry.getValue();

            if (mediaType == null || mediaType.getSchema() == null) {
                violations.add(ViolationDTO.builder()
                        .ruleId(getRuleId())
                        .severity("WARNING")
                        .endpoint(httpMethod + " " + path)
                        .oldPath(path)
                        .newPath(path)
                        .controller(controller)
                        .method(method)
                        .message("Consistent Error Response: Error response '" + statusCode + "' (" + mediaTypeName + ") in endpoint '" + httpMethod + " " + path + "' does not define a schema structure.")
                        .oldValue("N/A")
                        .newValue("Missing Schema")
                        .build());
                continue;
            }

            Schema<?> schema = mediaType.getSchema();
            String schemaType = schema.getType();
            String schemaRef = schema.get$ref();

            // Flag primitive string/text error responses
            if (schemaRef == null && ("string".equalsIgnoreCase(schemaType) || mediaTypeName.contains("text/plain"))) {
                violations.add(ViolationDTO.builder()
                        .ruleId(getRuleId())
                        .severity("WARNING")
                        .endpoint(httpMethod + " " + path)
                        .oldPath(path)
                        .newPath(path)
                        .controller(controller)
                        .method(method)
                        .message("Consistent Error Response: Error response '" + statusCode + "' in endpoint '" + httpMethod + " " + path + "' returns raw text/string instead of a standardized structured error object (e.g., ErrorResponse).")
                        .oldValue("String/Text")
                        .newValue("ErrorResponse Object Required")
                        .build());
            }
        }
    }

    private boolean hasGlobalErrorHandler(OpenAPI spec) {
        if (spec == null) return false;
        if (spec.getExtensions() != null) {
            Object xGlobal = spec.getExtensions().get("x-global-error-handling");
            if (xGlobal instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) xGlobal;
                if (Boolean.TRUE.equals(map.get("present")) || "true".equalsIgnoreCase(String.valueOf(map.get("present")))) {
                    return true;
                }
            }
        }
        if (spec.getComponents() != null && spec.getComponents().getSchemas() != null) {
            for (String schemaName : spec.getComponents().getSchemas().keySet()) {
                String lower = schemaName.toLowerCase(Locale.ROOT);
                if (lower.equals("errorresponse") || lower.equals("apierror") || lower.equals("errordto") || lower.equals("apierrorresponse") || lower.equals("errorinfo")) {
                    return true;
                }
            }
        }
        return false;
    }
}
