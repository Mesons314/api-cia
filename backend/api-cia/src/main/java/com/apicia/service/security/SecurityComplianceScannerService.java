package com.apicia.service.security;

import com.apicia.config.EndpointExtractionProperties;
import com.apicia.model.dto.PublicEndpointDTO;
import com.apicia.model.dto.SAMResultDTO;
import com.apicia.model.dto.SecurityAlertDTO;
import com.apicia.model.extraction.ExtractedEndpoint;
import com.apicia.model.extraction.ExtractedParameter;
import com.apicia.service.extraction.SecurityExtractionService;
import com.apicia.service.extraction.SourceUnit;
import com.apicia.service.extraction.StaticEndpointExtractionService;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SecurityComplianceScannerService {

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password", "passwd", "pwd", "secret", "ssn", "socialsecurity",
            "token", "jwt", "bearer", "apikey", "api_key", "privatekey", "private_key",
            "creditcard", "credit_card", "cardnumber", "card_number", "cc", "cvv", "cvc", "pin",
            "authtoken", "auth_token", "refreshtoken", "refresh_token", "accesstoken", "access_token"
    );

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            ".*(password|passwd|secret|ssn|social_?security|token|jwt|bearer|api_?key|private_?key|credit_?card|card_?number|\\bcc\\b|\\bcvv\\b|\\bcvc\\b|\\bpin\\b).*",
            Pattern.CASE_INSENSITIVE
    );

    private final StaticEndpointExtractionService extractionService;
    private final SecurityExtractionService securityExtractionService;
    private final EndpointExtractionProperties properties;

    public SecurityComplianceScannerService(
            StaticEndpointExtractionService extractionService,
            SecurityExtractionService securityExtractionService,
            EndpointExtractionProperties properties
    ) {
        this.extractionService = extractionService;
        this.securityExtractionService = securityExtractionService;
        this.properties = properties;
    }

    /**
     * Runs full Security & Compliance Analysis on the project source code.
     */
    public SAMResultDTO auditSourceCode(String sourceRootOverride) {
        List<ExtractedEndpoint> endpoints = extractionService.extractEndpoints(sourceRootOverride);
        List<SourceUnit> units = extractionService.parseSourceUnits(sourceRootOverride);
        Path sourceRoot = extractionService.getSourceRoot(sourceRootOverride);

        List<SecurityAlertDTO> allAlerts = new ArrayList<>();
        List<SecurityAlertDTO> corsViolations = new ArrayList<>();
        List<SecurityAlertDTO> sensitiveDataAlerts = new ArrayList<>();
        List<PublicEndpointDTO> publicEndpoints = new ArrayList<>();

        // 1. Audit Over-Permission (Wildcard CORS)
        auditCors(units, sourceRoot, endpoints, corsViolations);
        allAlerts.addAll(corsViolations);

        // 2. Audit Sensitive Data Leakage in DTOs and parameters
        auditSensitiveData(units, sourceRoot, endpoints, sensitiveDataAlerts);
        allAlerts.addAll(sensitiveDataAlerts);

        // 3. Audit No-Auth / Public Endpoints
        auditPublicEndpoints(endpoints, publicEndpoints, allAlerts);

        return buildResult(allAlerts, corsViolations, sensitiveDataAlerts, publicEndpoints);
    }

    /**
     * Audits an OpenAPI specification (JSON/YAML) for sensitive schema fields and unauthenticated operations.
     */
    public SAMResultDTO auditOpenApi(OpenAPI openAPI) {
        if (openAPI == null) {
            return buildResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        List<SecurityAlertDTO> allAlerts = new ArrayList<>();
        List<SecurityAlertDTO> sensitiveDataAlerts = new ArrayList<>();
        List<PublicEndpointDTO> publicEndpoints = new ArrayList<>();
        Set<String> alertedKeys = new HashSet<>();

        // Inspect OpenAPI Paths for response schema sensitive data and unauthenticated operations
        if (openAPI.getPaths() != null) {
            for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                PathItem pathItem = pathEntry.getValue();
                if (pathItem == null) continue;

                auditOpenApiOperation(openAPI, path, "GET", pathItem.getGet(), alertedKeys, sensitiveDataAlerts, allAlerts, publicEndpoints);
                auditOpenApiOperation(openAPI, path, "POST", pathItem.getPost(), alertedKeys, sensitiveDataAlerts, allAlerts, publicEndpoints);
                auditOpenApiOperation(openAPI, path, "PUT", pathItem.getPut(), alertedKeys, sensitiveDataAlerts, allAlerts, publicEndpoints);
                auditOpenApiOperation(openAPI, path, "DELETE", pathItem.getDelete(), alertedKeys, sensitiveDataAlerts, allAlerts, publicEndpoints);
                auditOpenApiOperation(openAPI, path, "PATCH", pathItem.getPatch(), alertedKeys, sensitiveDataAlerts, allAlerts, publicEndpoints);
            }
        }

        return buildResult(allAlerts, Collections.emptyList(), sensitiveDataAlerts, publicEndpoints);
    }

    private void auditOpenApiOperation(
            OpenAPI openAPI,
            String path,
            String method,
            Operation op,
            Set<String> alertedKeys,
            List<SecurityAlertDTO> sensitiveDataAlerts,
            List<SecurityAlertDTO> allAlerts,
            List<PublicEndpointDTO> publicEndpoints
    ) {
        if (op == null) return;

        // 1. Audit response body schemas for sensitive data leakage
        auditOpenApiResponseSchemas(openAPI, path, method, op, alertedKeys, sensitiveDataAlerts, allAlerts);

        // 2. Audit no-auth / public endpoint requirements
        checkOpenApiOperation(allAlerts, publicEndpoints, path, method, op, openAPI);
    }

    private void auditOpenApiResponseSchemas(
            OpenAPI openAPI,
            String path,
            String method,
            Operation op,
            Set<String> alertedKeys,
            List<SecurityAlertDTO> sensitiveDataAlerts,
            List<SecurityAlertDTO> allAlerts
    ) {
        if (op == null || op.getResponses() == null) return;

        String controllerClass = extractExtensionString(op, "x-controller-class");
        if (controllerClass != null && controllerClass.contains(".")) {
            controllerClass = controllerClass.substring(controllerClass.lastIndexOf('.') + 1);
        }
        String controllerMethod = extractExtensionString(op, "x-controller-method");
        String sourceFile = extractExtensionString(op, "x-source-file");
        Integer sourceLine = extractExtensionInt(op, "x-source-line");

        String location = sourceFile != null ? (sourceLine != null ? sourceFile + ":" + sourceLine : sourceFile) : "paths:" + path;

        Map<String, Schema> allSchemas = (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null)
                ? openAPI.getComponents().getSchemas()
                : Collections.emptyMap();

        for (Map.Entry<String, io.swagger.v3.oas.models.responses.ApiResponse> entry : op.getResponses().entrySet()) {
            io.swagger.v3.oas.models.responses.ApiResponse resp = entry.getValue();
            if (resp == null || resp.getContent() == null) continue;

            for (Map.Entry<String, io.swagger.v3.oas.models.media.MediaType> mtEntry : resp.getContent().entrySet()) {
                io.swagger.v3.oas.models.media.MediaType mt = mtEntry.getValue();
                if (mt == null || mt.getSchema() == null) continue;

                inspectOpenApiSchemaRecursive(
                        mt.getSchema(),
                        allSchemas,
                        path,
                        method,
                        controllerClass,
                        controllerMethod,
                        location,
                        new HashSet<>(),
                        alertedKeys,
                        sensitiveDataAlerts,
                        allAlerts
                );
            }
        }
    }

    private void inspectOpenApiSchemaRecursive(
            Schema<?> schema,
            Map<String, Schema> allSchemas,
            String path,
            String httpMethod,
            String controllerClass,
            String controllerMethod,
            String location,
            Set<String> visitedSchemas,
            Set<String> alertedKeys,
            List<SecurityAlertDTO> sensitiveDataAlerts,
            List<SecurityAlertDTO> allAlerts
    ) {
        if (schema == null) return;

        String ref = schema.get$ref();
        if (ref != null) {
            String schemaName = ref.substring(ref.lastIndexOf('/') + 1);
            if (!visitedSchemas.add(schemaName)) {
                return;
            }
            Schema<?> referencedSchema = allSchemas.get(schemaName);
            if (referencedSchema != null) {
                inspectOpenApiSchemaProperties(
                        schemaName,
                        referencedSchema,
                        allSchemas,
                        path,
                        httpMethod,
                        controllerClass,
                        controllerMethod,
                        location,
                        visitedSchemas,
                        alertedKeys,
                        sensitiveDataAlerts,
                        allAlerts
                );
            }
            return;
        }

        inspectOpenApiSchemaProperties(
                "InlineSchema",
                schema,
                allSchemas,
                path,
                httpMethod,
                controllerClass,
                controllerMethod,
                location,
                visitedSchemas,
                alertedKeys,
                sensitiveDataAlerts,
                allAlerts
        );
    }

    private void inspectOpenApiSchemaProperties(
            String schemaName,
            Schema<?> schema,
            Map<String, Schema> allSchemas,
            String path,
            String httpMethod,
            String controllerClass,
            String controllerMethod,
            String location,
            Set<String> visitedSchemas,
            Set<String> alertedKeys,
            List<SecurityAlertDTO> sensitiveDataAlerts,
            List<SecurityAlertDTO> allAlerts
    ) {
        if (schema.getProperties() != null) {
            for (Map.Entry<String, Schema> propEntry : schema.getProperties().entrySet()) {
                String propName = propEntry.getKey();
                Schema<?> propSchema = propEntry.getValue();

                if (isSensitiveName(propName)) {
                    String alertKey = (controllerClass != null ? controllerClass : path) + "#" + propName;
                    if (alertedKeys.add(alertKey)) {
                        String severity = isHighRiskKeyword(propName) ? "CRITICAL" : "HIGH";
                        String endpointDisplay = (httpMethod != null ? httpMethod + " " : "") + path;

                        SecurityAlertDTO alert = SecurityAlertDTO.builder()
                                .checkId("SEC-LEAK-001")
                                .category("SENSITIVE_DATA_EXPOSURE")
                                .severity(severity)
                                .endpoint(endpointDisplay)
                                .controller(controllerClass != null ? controllerClass : "UnknownController")
                                .method(controllerMethod != null ? controllerMethod : "unknownMethod")
                                .location(location)
                                .target(propName)
                                .description("Sensitive field '" + propName + "' in response DTO schema '" + schemaName + "' is exposed in response payload.")
                                .remediation("Ensure sensitive properties (passwords, tokens, PII) are excluded or masked in API response DTOs using @JsonIgnore or dedicated response models.")
                                .build();
                        sensitiveDataAlerts.add(alert);
                        allAlerts.add(alert);
                    }
                } else if (propSchema != null && propSchema.get$ref() != null) {
                    inspectOpenApiSchemaRecursive(
                            propSchema,
                            allSchemas,
                            path,
                            httpMethod,
                            controllerClass,
                            controllerMethod,
                            location,
                            visitedSchemas,
                            alertedKeys,
                            sensitiveDataAlerts,
                            allAlerts
                    );
                }
            }
        }
    }

    public SAMResultDTO auditOpenApiJson(String rawJson) {
        SwaggerParseResult result = new OpenAPIParser().readContents(rawJson, null, null);
        return auditOpenApi(result.getOpenAPI());
    }

    // =========================================================================
    // 1. OVER-PERMISSION (CORS WILDCARD) AUDIT
    // =========================================================================

    private void auditCors(List<SourceUnit> units, Path sourceRoot, List<ExtractedEndpoint> endpoints, List<SecurityAlertDTO> corsViolations) {
        for (SourceUnit unit : units) {
            for (ClassOrInterfaceDeclaration clazz : unit.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (!isController(clazz)) {
                    continue;
                }
                String controllerClass = qualifiedName(unit.compilationUnit, clazz);
                String relativeSource = sourceRoot != null ? sourceRoot.relativize(unit.path).toString().replace('\\', '/') : unit.path.toString();

                // 1. Check Class-level @CrossOrigin
                Optional<AnnotationExpr> classCors = clazz.getAnnotationByName("CrossOrigin");
                if (classCors.isPresent()) {
                    CorsCheckResult check = inspectCorsAnnotation(classCors.get());
                    if (check.isWildcard) {
                        int line = classCors.get().getRange().map(r -> r.begin.line).orElse(0);
                        SecurityAlertDTO alert = SecurityAlertDTO.builder()
                                .checkId("SEC-CORS-001")
                                .category("CORS_OVER_PERMISSION")
                                .severity("HIGH")
                                .endpoint("Class: " + clazz.getNameAsString())
                                .controller(controllerClass)
                                .method("CLASS_LEVEL")
                                .location(relativeSource + ":" + line)
                                .target(classCors.get().toString())
                                .description("Controller '" + clazz.getNameAsString() + "' is configured with wildcard CORS (" + check.detail + "). This exposes all controller endpoints to Cross-Origin attacks from any external domain.")
                                .remediation("Replace wildcard origins '*' with an explicit whitelist of trusted origins, e.g., @CrossOrigin(origins = {\"https://yourdomain.com\"}).")
                                .build();
                        corsViolations.add(alert);
                    }
                }

                // 2. Check Method-level @CrossOrigin
                for (MethodDeclaration method : clazz.getMethods()) {
                    Optional<AnnotationExpr> methodCors = method.getAnnotationByName("CrossOrigin");
                    if (methodCors.isPresent()) {
                        CorsCheckResult check = inspectCorsAnnotation(methodCors.get());
                        if (check.isWildcard) {
                            int line = methodCors.get().getRange().map(r -> r.begin.line).orElse(0);
                            String httpMethod = resolveMethodHttpVerb(method);
                            String severity = ("POST".equals(httpMethod) || "PUT".equals(httpMethod) || "DELETE".equals(httpMethod) || "PATCH".equals(httpMethod))
                                    ? "CRITICAL" : "HIGH";

                            SecurityAlertDTO alert = SecurityAlertDTO.builder()
                                    .checkId("SEC-CORS-001")
                                    .category("CORS_OVER_PERMISSION")
                                    .severity(severity)
                                    .endpoint(httpMethod + " in " + clazz.getNameAsString() + "#" + method.getNameAsString())
                                    .controller(controllerClass)
                                    .method(method.getNameAsString())
                                    .location(relativeSource + ":" + line)
                                    .target(methodCors.get().toString())
                                    .description("Method '" + method.getNameAsString() + "' uses wildcard CORS (" + check.detail + ").")
                                    .remediation("Restrict allowed origins to specific domains rather than allowing wildcard '*'.")
                                    .build();
                            corsViolations.add(alert);
                        }
                    }
                }
            }
        }
    }

    private CorsCheckResult inspectCorsAnnotation(AnnotationExpr annotation) {
        if (annotation.isMarkerAnnotationExpr()) {
            // @CrossOrigin with no attributes defaults to origins = "*"
            return new CorsCheckResult(true, "origins defaulted to '*'");
        }

        List<String> origins = new ArrayList<>();
        List<String> originPatterns = new ArrayList<>();

        if (annotation instanceof SingleMemberAnnotationExpr single) {
            collectStringValues(single.getMemberValue(), origins);
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                String pairName = pair.getNameAsString();
                if ("origins".equals(pairName) || "value".equals(pairName)) {
                    collectStringValues(pair.getValue(), origins);
                } else if ("originPatterns".equals(pairName)) {
                    collectStringValues(pair.getValue(), originPatterns);
                }
            }
        }

        if (origins.isEmpty() && originPatterns.isEmpty()) {
            return new CorsCheckResult(true, "origins defaulted to '*'");
        }

        for (String origin : origins) {
            if ("*".equals(origin) || "**".equals(origin)) {
                return new CorsCheckResult(true, "origins = '*' detected");
            }
        }

        for (String pattern : originPatterns) {
            if ("*".equals(pattern) || "**".equals(pattern) || pattern.startsWith("*")) {
                return new CorsCheckResult(true, "originPatterns = '" + pattern + "' contains wildcard");
            }
        }

        return new CorsCheckResult(false, "");
    }

    private static class CorsCheckResult {
        final boolean isWildcard;
        final String detail;

        CorsCheckResult(boolean isWildcard, String detail) {
            this.isWildcard = isWildcard;
            this.detail = detail;
        }
    }

    // =========================================================================
    // 2. SENSITIVE DATA SCANNER (PII / SECRET LEAKAGE)
    // =========================================================================

    private void auditSensitiveData(List<SourceUnit> units, Path sourceRoot, List<ExtractedEndpoint> endpoints, List<SecurityAlertDTO> sensitiveDataAlerts) {
        // Map all DTO classes and their fields
        Map<String, DtoClassInfo> dtoMap = new HashMap<>();

        for (SourceUnit unit : units) {
            String relativeSource = sourceRoot != null ? sourceRoot.relativize(unit.path).toString().replace('\\', '/') : unit.path.toString();
            for (ClassOrInterfaceDeclaration clazz : unit.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (isController(clazz)) continue;

                String qualified = qualifiedName(unit.compilationUnit, clazz);
                String simple = clazz.getNameAsString();
                DtoClassInfo classInfo = new DtoClassInfo(simple, qualified, relativeSource);

                for (FieldDeclaration field : clazz.getFields()) {
                    boolean isJsonIgnored = field.getAnnotationByName("JsonIgnore").isPresent();
                    for (VariableDeclarator var : field.getVariables()) {
                        String fieldName = var.getNameAsString();
                        String fieldType = var.getTypeAsString();
                        int line = var.getRange().map(r -> r.begin.line).orElse(0);
                        classInfo.fields.add(new DtoFieldInfo(fieldName, fieldType, isJsonIgnored, line));
                    }
                }
                dtoMap.put(simple, classInfo);
                dtoMap.put(qualified, classInfo);
            }
        }

        // 1. Scan controller methods for URL-based sensitive parameters (e.g. GET ?password=...)
        for (SourceUnit unit : units) {
            String relativeSource = sourceRoot != null ? sourceRoot.relativize(unit.path).toString().replace('\\', '/') : unit.path.toString();
            for (ClassOrInterfaceDeclaration clazz : unit.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (!isController(clazz)) continue;
                String controllerClass = qualifiedName(unit.compilationUnit, clazz);

                for (MethodDeclaration method : clazz.getMethods()) {
                    String httpMethod = resolveMethodHttpVerb(method);
                    for (Parameter param : method.getParameters()) {
                        String paramName = param.getNameAsString();
                        if (isSensitiveName(paramName)) {
                            int line = param.getRange().map(r -> r.begin.line).orElse(0);
                            String category = "GET".equalsIgnoreCase(httpMethod) ? "SENSITIVE_DATA_IN_URL" : "SENSITIVE_PARAMETER";
                            String severity = isHighRiskKeyword(paramName) ? "CRITICAL" : "HIGH";
                            String desc = "GET".equalsIgnoreCase(httpMethod)
                                    ? "Sensitive parameter '" + paramName + "' accepted in GET request URL. Query parameters are logged in plaintext across server access logs and browser histories."
                                    : "Sensitive parameter '" + paramName + "' in method '" + method.getNameAsString() + "'.";

                            SecurityAlertDTO alert = SecurityAlertDTO.builder()
                                    .checkId("SEC-LEAK-002")
                                    .category(category)
                                    .severity(severity)
                                    .endpoint(httpMethod + " " + clazz.getNameAsString() + "#" + method.getNameAsString())
                                    .controller(controllerClass)
                                    .method(method.getNameAsString())
                                    .location(relativeSource + ":" + line)
                                    .target(param.toString())
                                    .description(desc)
                                    .remediation("Pass sensitive credentials/tokens via HTTP Request Body or Authorization headers instead of URL parameters.")
                                    .build();
                            sensitiveDataAlerts.add(alert);
                        }
                    }
                }
            }
        }

        // 2. Scan DTOs returned or accepted by endpoints
        Set<String> alertedFieldKeys = new HashSet<>();
        for (ExtractedEndpoint ep : endpoints) {
            String returnType = ep.getReturnType();
            if (returnType != null) {
                String cleanType = cleanGenericType(returnType);
                inspectDtoForSensitiveFields(cleanType, dtoMap, ep, alertedFieldKeys, sensitiveDataAlerts);
            }
        }
    }

    private void inspectDtoForSensitiveFields(String typeName, Map<String, DtoClassInfo> dtoMap, ExtractedEndpoint ep, Set<String> alertedKeys, List<SecurityAlertDTO> alerts) {
        DtoClassInfo info = dtoMap.get(typeName);
        if (info == null) return;

        for (DtoFieldInfo field : info.fields) {
            if (isSensitiveName(field.name)) {
                if (field.isJsonIgnored) {
                    continue; // Correctly protected by @JsonIgnore
                }
                String key = info.qualifiedName + "#" + field.name;
                if (alertedKeys.add(key)) {
                    String severity = isHighRiskKeyword(field.name) ? "CRITICAL" : "HIGH";
                    SecurityAlertDTO alert = SecurityAlertDTO.builder()
                            .checkId("SEC-LEAK-001")
                            .category("SENSITIVE_DATA_EXPOSURE")
                            .severity(severity)
                            .endpoint(ep.getHttpMethod().toUpperCase() + " " + ep.getPath())
                            .controller(ep.getControllerClass())
                            .method(ep.getControllerMethod())
                            .location(info.sourceFile + ":" + field.lineNumber)
                            .target(field.name + ": " + field.type)
                            .description("Sensitive field '" + field.name + "' in DTO '" + info.simpleName + "' is exposed in endpoint response payload without @JsonIgnore.")
                            .remediation("Annotate field with @JsonIgnore, use a dedicated response DTO, or mask sensitive values before returning.")
                            .build();
                    alerts.add(alert);
                }
            } else {
                // Recursively check nested complex types
                String nestedType = cleanGenericType(field.type);
                if (!nestedType.equals(typeName) && dtoMap.containsKey(nestedType)) {
                    inspectDtoForSensitiveFields(nestedType, dtoMap, ep, alertedKeys, alerts);
                }
            }
        }
    }

    private boolean isSensitiveName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYWORDS.contains(lower) || SENSITIVE_PATTERN.matcher(lower).matches();
    }

    private boolean isHighRiskKeyword(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("passwd") || lower.contains("ssn")
                || lower.contains("cvv") || lower.contains("cvc") || lower.contains("privatekey") || lower.contains("creditcard");
    }

    private String cleanGenericType(String type) {
        if (type == null) return "";
        if (type.contains("<") && type.contains(">")) {
            type = type.substring(type.indexOf('<') + 1, type.lastIndexOf('>')).trim();
        }
        if (type.contains(",")) {
            type = type.split(",")[0].trim();
        }
        if (type.contains(".")) {
            type = type.substring(type.lastIndexOf('.') + 1);
        }
        return type;
    }

    // =========================================================================
    // 3. NO-AUTH ENDPOINT REPORTER
    // =========================================================================

    private void auditPublicEndpoints(List<ExtractedEndpoint> endpoints, List<PublicEndpointDTO> publicEndpoints, List<SecurityAlertDTO> allAlerts) {
        for (ExtractedEndpoint ep : endpoints) {
            boolean isPublic = "false".equals(ep.getAuthenticationRequired())
                    || (ep.getAuthorization() != null && ep.getAuthorization().toLowerCase().contains("permitall"));

            if (isPublic) {
                String reason = ep.getAuthorization() != null ? ep.getAuthorization() : "Configured with public access / permitAll";
                PublicEndpointDTO pubDto = PublicEndpointDTO.builder()
                        .path(ep.getPath())
                        .httpMethod(ep.getHttpMethod().toUpperCase())
                        .controllerClass(ep.getControllerClass())
                        .controllerMethod(ep.getControllerMethod())
                        .sourceFile(ep.getSourceFile())
                        .lineNumber(ep.getLineNumber())
                        .reason(reason)
                        .build();
                publicEndpoints.add(pubDto);

                // If a state-modifying endpoint (DELETE, POST, PUT, PATCH) is unauthenticated, flag it for audit
                String verb = ep.getHttpMethod().toUpperCase();
                if ("DELETE".equals(verb) || "PUT".equals(verb) || "PATCH".equals(verb)) {
                    SecurityAlertDTO alert = SecurityAlertDTO.builder()
                            .checkId("SEC-NOAUTH-001")
                            .category("UNAUTHENTICATED_STATE_MODIFICATION")
                            .severity("HIGH")
                            .endpoint(verb + " " + ep.getPath())
                            .controller(ep.getControllerClass())
                            .method(ep.getControllerMethod())
                            .location(ep.getSourceFile() + ":" + ep.getLineNumber())
                            .target(verb + " " + ep.getPath())
                            .description("State-modifying endpoint '" + verb + " " + ep.getPath() + "' is configured with public access without authentication.")
                            .remediation("Verify whether this endpoint should require authentication (e.g., via SecurityFilterChain or @PreAuthorize).")
                            .build();
                    allAlerts.add(alert);
                }
            }
        }
    }

    private void checkOpenApiOperation(List<SecurityAlertDTO> allAlerts, List<PublicEndpointDTO> publicEndpoints, String path, String method, Operation op, OpenAPI openAPI) {
        if (op == null) return;

        boolean hasSecurity = (op.getSecurity() != null && !op.getSecurity().isEmpty())
                || (openAPI.getSecurity() != null && !openAPI.getSecurity().isEmpty());

        // Check for x-security extension
        if (op.getExtensions() != null && op.getExtensions().get("x-security") instanceof Map) {
            Map<?, ?> xSec = (Map<?, ?>) op.getExtensions().get("x-security");
            if ("false".equals(String.valueOf(xSec.get("authenticationRequired")))) {
                hasSecurity = false;
            } else if ("true".equals(String.valueOf(xSec.get("authenticationRequired")))) {
                hasSecurity = true;
            }
        }

        if (!hasSecurity) {
            String controllerClass = extractExtensionString(op, "x-controller-class");
            if (controllerClass != null && controllerClass.contains(".")) {
                controllerClass = controllerClass.substring(controllerClass.lastIndexOf('.') + 1);
            }
            String controllerMethod = extractExtensionString(op, "x-controller-method");
            String sourceFile = extractExtensionString(op, "x-source-file");
            Integer sourceLine = extractExtensionInt(op, "x-source-line");
            String location = sourceFile != null ? (sourceLine != null ? sourceFile + ":" + sourceLine : sourceFile) : "paths:" + path;

            publicEndpoints.add(PublicEndpointDTO.builder()
                    .path(path)
                    .httpMethod(method)
                    .controllerClass(controllerClass)
                    .controllerMethod(controllerMethod)
                    .sourceFile(sourceFile)
                    .lineNumber(sourceLine)
                    .reason("No security requirements defined in OpenAPI specification")
                    .build());

            if ("DELETE".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                allAlerts.add(SecurityAlertDTO.builder()
                        .checkId("SEC-NOAUTH-001")
                        .category("UNAUTHENTICATED_STATE_MODIFICATION")
                        .severity("HIGH")
                        .endpoint(method + " " + path)
                        .controller(controllerClass != null ? controllerClass : "UnknownController")
                        .method(controllerMethod != null ? controllerMethod : "unknownMethod")
                        .location(location)
                        .target(method + " " + path)
                        .description("Operation '" + method + " " + path + "' has no security scheme requirement in OpenAPI specification.")
                        .remediation("Add a security requirement (e.g. bearerAuth, oauth2) to protect this operation.")
                        .build());
            }
        }
    }

    // =========================================================================
    // HELPER & RESULT BUILDER METHODS
    // =========================================================================

    private SAMResultDTO buildResult(
            List<SecurityAlertDTO> allAlerts,
            List<SecurityAlertDTO> corsViolations,
            List<SecurityAlertDTO> sensitiveDataAlerts,
            List<PublicEndpointDTO> publicEndpoints
    ) {
        int criticalCount = 0;
        int highCount = 0;
        int warningCount = 0;
        int infoCount = 0;

        for (SecurityAlertDTO alert : allAlerts) {
            if (alert.getSeverity() != null) {
                switch (alert.getSeverity().toUpperCase()) {
                    case "CRITICAL" -> criticalCount++;
                    case "HIGH" -> highCount++;
                    case "WARNING" -> warningCount++;
                    default -> infoCount++;
                }
            }
        }

        // Calculate security compliance score (aSec: 0.0 - 1.0, 1.0 being 100% compliant)
        double penalty = (criticalCount * 0.35) + (highCount * 0.15) + (warningCount * 0.05);
        double aSec = Math.max(0.0, Math.round((1.0 - Math.min(1.0, penalty)) * 100.0) / 100.0);

        return SAMResultDTO.builder()
                .totalAlerts(allAlerts.size())
                .criticalCount(criticalCount)
                .highCount(highCount)
                .warningCount(warningCount)
                .infoCount(infoCount)
                .aSec(aSec)
                .alerts(allAlerts)
                .corsViolations(corsViolations)
                .sensitiveDataAlerts(sensitiveDataAlerts)
                .publicEndpoints(publicEndpoints)
                .build();
    }

    private boolean isController(ClassOrInterfaceDeclaration type) {
        return type.getAnnotationByName("RestController").isPresent()
                || type.getAnnotationByName("Controller").isPresent()
                || type.getNameAsString().endsWith("Controller");
    }

    private String qualifiedName(CompilationUnit cu, ClassOrInterfaceDeclaration type) {
        String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString() + ".").orElse("");
        return packageName + type.getNameAsString();
    }

    private String resolveMethodHttpVerb(MethodDeclaration method) {
        if (method.getAnnotationByName("GetMapping").isPresent()) return "GET";
        if (method.getAnnotationByName("PostMapping").isPresent()) return "POST";
        if (method.getAnnotationByName("PutMapping").isPresent()) return "PUT";
        if (method.getAnnotationByName("DeleteMapping").isPresent()) return "DELETE";
        if (method.getAnnotationByName("PatchMapping").isPresent()) return "PATCH";
        return "GET";
    }

    private void collectStringValues(Expression expr, List<String> list) {
        if (expr instanceof StringLiteralExpr str) {
            list.add(str.getValue());
        } else if (expr instanceof ArrayInitializerExpr array) {
            for (Expression item : array.getValues()) {
                collectStringValues(item, list);
            }
        } else {
            String s = expr.toString().replace("\"", "").replace("'", "").trim();
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
    }

    private String extractExtensionString(Operation op, String key) {
        if (op != null && op.getExtensions() != null) {
            Object val = op.getExtensions().get(key);
            if (val != null) {
                return String.valueOf(val);
            }
        }
        return null;
    }

    private Integer extractExtensionInt(Operation op, String key) {
        if (op != null && op.getExtensions() != null) {
            Object val = op.getExtensions().get(key);
            if (val instanceof Number num) {
                return num.intValue();
            } else if (val != null) {
                try {
                    return Integer.parseInt(String.valueOf(val));
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private static class DtoClassInfo {
        final String simpleName;
        final String qualifiedName;
        final String sourceFile;
        final List<DtoFieldInfo> fields = new ArrayList<>();

        DtoClassInfo(String simpleName, String qualifiedName, String sourceFile) {
            this.simpleName = simpleName;
            this.qualifiedName = qualifiedName;
            this.sourceFile = sourceFile;
        }
    }

    private static class DtoFieldInfo {
        final String name;
        final String type;
        final boolean isJsonIgnored;
        final int lineNumber;

        DtoFieldInfo(String name, String type, boolean isJsonIgnored, int lineNumber) {
            this.name = name;
            this.type = type;
            this.isJsonIgnored = isJsonIgnored;
            this.lineNumber = lineNumber;
        }
    }
}
