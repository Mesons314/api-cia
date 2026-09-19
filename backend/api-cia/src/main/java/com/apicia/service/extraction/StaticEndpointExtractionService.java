package com.apicia.service.extraction;

import com.apicia.config.EndpointExtractionProperties;
import com.apicia.model.extraction.ExtractedEndpoint;
import com.apicia.model.extraction.ExtractedParameter;
import com.apicia.model.extraction.ExtractedRequestBody;
import com.apicia.model.extraction.ExtractedResponse;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StaticEndpointExtractionService {

    private static final List<String> DEFAULT_REQUEST_METHODS = List.of("get", "post", "put", "delete", "patch");
    private static final Map<String, String> MAPPING_METHODS = Map.ofEntries(
            Map.entry("GetMapping", "get"),
            Map.entry("PostMapping", "post"),
            Map.entry("PutMapping", "put"),
            Map.entry("DeleteMapping", "delete"),
            Map.entry("PatchMapping", "patch"),
            Map.entry("MessageMapping", "post"),
            Map.entry("SubscribeMapping", "get")
    );

    private static final Set<String> WEBSOCKET_FRAMEWORK_TYPES = Set.of(
            "SimpMessageHeaderAccessor", "MessageHeaderAccessor", "HeaderAccessor",
            "MessageHeaders", "Message", "Principal", "Authentication", "StompHeaderAccessor",
            "SessionDisconnectEvent", "SessionSubscribeEvent", "SessionConnectedEvent",
            "SimpMessagingTemplate", "HttpServletRequest", "HttpServletResponse", "HttpSession",
            "BindingResult", "Errors", "Model", "ModelMap"
    );

    private final EndpointExtractionProperties properties;
    private final SecurityExtractionService securityExtractionService;
    private final Map<String, ClassSchema> indexedClasses = new HashMap<>();

    public StaticEndpointExtractionService(
            EndpointExtractionProperties properties,
            SecurityExtractionService securityExtractionService
    ) {
        this.properties = properties;
        this.securityExtractionService = securityExtractionService;
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    public List<ExtractedEndpoint> extractEndpoints() {
        return extractEndpoints(null);
    }

    public List<ExtractedEndpoint> extractEndpoints(String sourceRootOverride) {
        Path sourceRoot = sourceRoot(sourceRootOverride);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalStateException("Endpoint extraction source root does not exist: " + sourceRoot);
        }

        List<SourceUnit> units = parseSources(sourceRoot);
        indexClasses(units);

        List<ExtractedEndpoint> endpoints = new ArrayList<>();
        for (SourceUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (!isController(type)) {
                    continue;
                }
                String controllerClass = qualifiedName(unit.compilationUnit, type);
                if (!properties.isIncludeExtractorEndpoints()
                        && controllerClass.equals("com.apicia.controller.ExtractionController")) {
                    continue;
                }

                List<String> basePaths = mappingPaths(type, "RequestMapping");
                if (basePaths.isEmpty()) {
                    basePaths = List.of("");
                }
                Set<String> classConsumes = mappingMediaTypes(type, "consumes");
                Set<String> classProduces = mappingMediaTypes(type, "produces");

                for (MethodDeclaration method : type.getMethods()) {
                    Mapping mapping = methodMapping(method);
                    if (mapping == null) {
                        continue;
                    }
                    for (String basePath : basePaths) {
                        for (String methodPath : mapping.paths()) {
                            for (String httpMethod : mapping.httpMethods()) {
                                ExtractedEndpoint endpoint = new ExtractedEndpoint();
                                endpoint.setPath(normalizePath(basePath, methodPath));
                                endpoint.setHttpMethod(httpMethod);
                                endpoint.setControllerClass(controllerClass);
                                endpoint.setControllerMethod(method.getNameAsString());
                                endpoint.setReturnType(method.getType().asString());
                                endpoint.setSourceFile(sourceRoot.relativize(unit.path).toString().replace('\\', '/'));
                                endpoint.setLineNumber(method.getRange().map(range -> range.begin.line).orElse(0));
                                endpoint.getConsumes().addAll(classConsumes);
                                endpoint.getConsumes().addAll(mapping.consumes());
                                endpoint.getProduces().addAll(classProduces);
                                endpoint.getProduces().addAll(mapping.produces());
                                enrichParameters(endpoint, method);
                                endpoint.getResponses().add(response(method));
                                endpoints.add(endpoint);
                            }
                        }
                    }
                }
            }
        }

        securityExtractionService.extractSecurity(endpoints, units);
 
        endpoints.sort(Comparator.comparing(ExtractedEndpoint::getPath)
                .thenComparing(ExtractedEndpoint::getHttpMethod)
                .thenComparing(ExtractedEndpoint::getControllerClass)
                .thenComparing(ExtractedEndpoint::getControllerMethod));
        return endpoints;
    }

    public Map<String, Object> generateOpenApi() {
        return generateOpenApi(null);
    }

    public Map<String, Object> generateOpenApi(String sourceRootOverride) {
        List<ExtractedEndpoint> endpoints = extractEndpoints(sourceRootOverride);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", Map.of(
                "title", properties.getTitle(),
                "description", properties.getDescription(),
                "version", properties.getVersion()
        ));
        spec.put("servers", properties.getServers().stream().map(url -> Map.of("url", url)).toList());
        spec.put("paths", paths(endpoints));
        
        Map<String, Object> componentsMap = new LinkedHashMap<>();
        componentsMap.put("schemas", components(endpoints));
        Map<String, Object> schemes = securityExtractionService.getSecuritySchemes(endpoints);
        if (!schemes.isEmpty()) {
            componentsMap.put("securitySchemes", schemes);
        }
        spec.put("components", componentsMap);

        List<SourceUnit> units = parseSourceUnits(sourceRootOverride);
        Map<String, Object> globalError = extractGlobalErrorHandling(units);
        if (!globalError.isEmpty()) {
            spec.put("x-global-error-handling", globalError);
        }

        spec.put("x-extraction", Map.of(
                "mode", "static-source",
                "sourceRoot", sourceRoot().toString(),
                "endpointCount", endpoints.size()
        ));
        return spec;
    }

    private Map<String, Object> extractGlobalErrorHandling(List<SourceUnit> units) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (SourceUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                boolean isAdvice = type.getAnnotationByName("RestControllerAdvice").isPresent()
                        || type.getAnnotationByName("ControllerAdvice").isPresent()
                        || type.getNameAsString().endsWith("ExceptionHandler")
                        || type.getNameAsString().endsWith("GlobalExceptionHandler");

                if (!isAdvice) {
                    continue;
                }

                List<String> handledExceptions = new ArrayList<>();
                String defaultReturnType = null;

                for (MethodDeclaration method : type.getMethods()) {
                    if (method.getAnnotationByName("ExceptionHandler").isPresent()) {
                        Optional<AnnotationExpr> annot = method.getAnnotationByName("ExceptionHandler");
                        annot.ifPresent(a -> handledExceptions.addAll(annotationValues(a, "value").stream().map(Expression::toString).toList()));
                        if (defaultReturnType == null) {
                            defaultReturnType = unwrapReturnType(method.getType().asString());
                        }
                    }
                }

                result.put("present", true);
                result.put("handlerClass", qualifiedName(unit.compilationUnit, type));
                result.put("handledExceptions", handledExceptions);
                if (defaultReturnType != null && !defaultReturnType.isEmpty() && !"void".equalsIgnoreCase(defaultReturnType)) {
                    result.put("errorResponseSchema", defaultReturnType);
                }
                return result;
            }
        }
        return result;
    }

    public Path getSourceRoot(String override) {
        return sourceRoot(override);
    }

    public List<SourceUnit> parseSourceUnits(String sourceRootOverride) {
        Path root = sourceRoot(sourceRootOverride);
        if (!Files.isDirectory(root)) {
            return java.util.Collections.emptyList();
        }
        return parseSources(root);
    }

    private List<SourceUnit> parseSources(Path sourceRoot) {
        List<SourceUnit> units = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> parseSource(path).ifPresent(units::add));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to scan Java sources under " + sourceRoot, ex);
        }
        return units;
    }

    private Optional<SourceUnit> parseSource(Path path) {
        try {
            StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
            return Optional.of(new SourceUnit(path, StaticJavaParser.parse(path)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse Java source: " + path, ex);
        }
    }

    private void indexClasses(List<SourceUnit> units) {
        indexedClasses.clear();
        for (SourceUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                ClassSchema schema = new ClassSchema(type.getNameAsString(), qualifiedName(unit.compilationUnit, type));
                for (FieldDeclaration field : type.getFields()) {
                    field.getVariables().forEach(variable -> schema.fields.put(variable.getNameAsString(), variable.getType().asString()));
                }
                indexedClasses.put(schema.simpleName, schema);
                indexedClasses.put(schema.qualifiedName, schema);
            }
        }
    }

    private boolean isController(ClassOrInterfaceDeclaration type) {
        return hasAnnotation(type, "RestController")
                || hasAnnotation(type, "Controller")
                || type.getNameAsString().endsWith("Controller");
    }

    private Mapping methodMapping(MethodDeclaration method) {
        for (Map.Entry<String, String> entry : MAPPING_METHODS.entrySet()) {
            Optional<AnnotationExpr> mappingAnnotation = annotation(method, entry.getKey());
            if (mappingAnnotation.isPresent()) {
                AnnotationExpr annotation = mappingAnnotation.get();
                List<String> paths = mappingPaths(annotation);
                return new Mapping(paths.isEmpty() ? List.of("") : paths,
                        List.of(entry.getValue()),
                        mediaTypes(annotation, "consumes"),
                        mediaTypes(annotation, "produces"));
            }
        }
        Optional<AnnotationExpr> requestMapping = annotation(method, "RequestMapping");
        if (requestMapping.isEmpty()) {
            return null;
        }
        AnnotationExpr annotation = requestMapping.get();
        List<String> paths = mappingPaths(annotation);
        List<String> methods = requestMethods(annotation);
        return new Mapping(
                paths.isEmpty() ? List.of("") : paths,
                methods.isEmpty() ? DEFAULT_REQUEST_METHODS : methods,
                mediaTypes(annotation, "consumes"),
                mediaTypes(annotation, "produces"));
    }

    private void enrichParameters(ExtractedEndpoint endpoint, MethodDeclaration method) {
        boolean isWs = hasAnnotation(method, "MessageMapping") || hasAnnotation(method, "SubscribeMapping");
        Parameter potentialPayloadParam = null;

        for (Parameter parameter : method.getParameters()) {
            String paramType = parameter.getType().asString();
            String simpleParamType = paramType.contains("<") ? paramType.substring(0, paramType.indexOf('<')) : paramType;
            if (simpleParamType.contains(".")) {
                simpleParamType = simpleParamType.substring(simpleParamType.lastIndexOf('.') + 1);
            }

            if (WEBSOCKET_FRAMEWORK_TYPES.contains(simpleParamType)) {
                continue;
            }

            if (hasAnnotation(parameter, "RequestBody") || hasAnnotation(parameter, "Payload")) {
                ExtractedRequestBody body = new ExtractedRequestBody();
                body.setJavaType(parameter.getType().asString());
                body.setRequired(required(parameter, true));
                body.getValidations().putAll(validations(parameter));
                endpoint.setRequestBody(body);
                continue;
            }

            String in = null;
            if (hasAnnotation(parameter, "PathVariable") || hasAnnotation(parameter, "DestinationVariable")) {
                in = "path";
            } else if (hasAnnotation(parameter, "RequestParam")) {
                in = "query";
            } else if (hasAnnotation(parameter, "RequestHeader") || hasAnnotation(parameter, "Header")) {
                in = "header";
            }

            if (in != null) {
                ExtractedParameter extracted = new ExtractedParameter();
                extracted.setName(parameterName(parameter));
                extracted.setIn(in);
                extracted.setJavaType(parameter.getType().asString());
                extracted.setRequired("path".equals(in) || required(parameter, true));
                extracted.getValidations().putAll(validations(parameter));
                endpoint.getParameters().add(extracted);
            } else if (isWs && potentialPayloadParam == null) {
                potentialPayloadParam = parameter;
            }
        }

        if (isWs && endpoint.getRequestBody() == null && potentialPayloadParam != null) {
            ExtractedRequestBody body = new ExtractedRequestBody();
            body.setJavaType(potentialPayloadParam.getType().asString());
            body.setRequired(required(potentialPayloadParam, true));
            body.getValidations().putAll(validations(potentialPayloadParam));
            endpoint.setRequestBody(body);
        }
    }

    private ExtractedResponse response(MethodDeclaration method) {
        String statusCode = statusCode(method).orElse("200");
        String description = "Successful response";
        Optional<AnnotationExpr> sendTo = annotation(method, "SendTo");
        if (sendTo.isPresent()) {
            List<Expression> vals = annotationValues(sendTo.get(), "value");
            if (!vals.isEmpty()) {
                description = "Broadcasts to " + stringValue(vals.get(0));
            }
        } else {
            Optional<AnnotationExpr> sendToUser = annotation(method, "SendToUser");
            if (sendToUser.isPresent()) {
                List<Expression> vals = annotationValues(sendToUser.get(), "value");
                if (!vals.isEmpty()) {
                    description = "Sends to user " + stringValue(vals.get(0));
                }
            }
        }
        return new ExtractedResponse(statusCode, description, unwrapReturnType(method.getType().asString()));
    }

    private Map<String, Object> paths(List<ExtractedEndpoint> endpoints) {
        Map<String, Object> paths = new LinkedHashMap<>();
        for (ExtractedEndpoint endpoint : endpoints) {
            Map<String, Object> pathItem = nestedMap(paths, endpoint.getPath());
            pathItem.put(endpoint.getHttpMethod(), operation(endpoint));
        }
        return paths;
    }

    private Map<String, Object> operation(ExtractedEndpoint endpoint) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", operationId(endpoint));
        operation.put("tags", List.of(simpleName(endpoint.getControllerClass())));
        operation.put("x-controller-class", endpoint.getControllerClass());
        operation.put("x-controller-method", endpoint.getControllerMethod());
        operation.put("x-source-file", endpoint.getSourceFile());
        operation.put("x-source-line", endpoint.getLineNumber());
        operation.put("x-return-type", endpoint.getReturnType());
        if (!endpoint.getParameters().isEmpty()) {
            operation.put("parameters", endpoint.getParameters().stream().map(this::parameter).toList());
        }
        if (endpoint.getRequestBody() != null) {
            operation.put("requestBody", requestBody(endpoint));
        }
        operation.put("responses", responses(endpoint));

        // Security info
        Map<String, Object> xSecurity = new LinkedHashMap<>();
        if ("true".equals(endpoint.getAuthenticationRequired())) {
            xSecurity.put("authenticationRequired", true);
            if (endpoint.getAuthenticationType() != null && !"UNKNOWN".equals(endpoint.getAuthenticationType())) {
                xSecurity.put("authenticationType", endpoint.getAuthenticationType());
            }
            if (endpoint.getAuthorization() != null) {
                xSecurity.put("authorization", endpoint.getAuthorization());
            }
            
            String schemeName = "bearerAuth";
            if ("Basic".equals(endpoint.getAuthenticationType())) {
                schemeName = "basicAuth";
            } else if ("OAuth2".equals(endpoint.getAuthenticationType())) {
                schemeName = "oauth2Auth";
            }
            operation.put("security", List.of(Map.of(schemeName, List.of())));
        } else if ("false".equals(endpoint.getAuthenticationRequired())) {
            xSecurity.put("authenticationRequired", false);
        } else {
            xSecurity.put("authenticationRequired", "UNKNOWN");
        }
        operation.put("x-security", xSecurity);

        return operation;
    }

    private Map<String, Object> parameter(ExtractedParameter source) {
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("name", source.getName());
        parameter.put("in", source.getIn());
        parameter.put("required", source.isRequired());
        Map<String, Object> schema = new LinkedHashMap<>(schemaFor(source.getJavaType(), new LinkedHashSet<>()));
        schema.putAll(source.getValidations());
        parameter.put("schema", schema);
        return parameter;
    }

    private Map<String, Object> requestBody(ExtractedEndpoint endpoint) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("required", endpoint.getRequestBody().isRequired());
        Map<String, Object> schema = new LinkedHashMap<>(schemaFor(endpoint.getRequestBody().getJavaType(), new LinkedHashSet<>()));
        schema.putAll(endpoint.getRequestBody().getValidations());
        requestBody.put("content", content(endpoint.getConsumes(), schema));
        return requestBody;
    }

    private Map<String, Object> responses(ExtractedEndpoint endpoint) {
        Map<String, Object> responses = new LinkedHashMap<>();
        for (ExtractedResponse responseDefinition : endpoint.getResponses()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("description", responseDefinition.getDescription());
            if (!isVoid(responseDefinition.getJavaType())) {
                response.put("content", content(endpoint.getProduces(), schemaFor(responseDefinition.getJavaType(), new LinkedHashSet<>())));
            }
            responses.put(responseDefinition.getStatusCode(), response);
        }
        return responses;
    }

    private Map<String, Object> content(Set<String> mediaTypes, Map<String, Object> schema) {
        Set<String> resolved = mediaTypes.isEmpty() ? Set.of("application/json") : mediaTypes;
        Map<String, Object> content = new LinkedHashMap<>();
        resolved.forEach(mediaType -> content.put(mediaType, Map.of("schema", schema)));
        return content;
    }

    private Map<String, Object> components(List<ExtractedEndpoint> endpoints) {
        Set<String> referenced = new LinkedHashSet<>();
        for (ExtractedEndpoint endpoint : endpoints) {
            if (endpoint.getRequestBody() != null) {
                collectComponent(endpoint.getRequestBody().getJavaType(), referenced);
            }
            endpoint.getResponses().forEach(response -> collectComponent(response.getJavaType(), referenced));
        }
        Map<String, Object> schemas = new LinkedHashMap<>();
        for (String name : referenced) {
            ClassSchema classSchema = indexedClasses.get(name);
            if (classSchema == null || schemas.containsKey(classSchema.simpleName)) {
                continue;
            }
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<>();
            classSchema.fields.forEach((field, type) -> properties.put(field, schemaFor(type, new LinkedHashSet<>())));
            schema.put("properties", properties);
            schema.put("x-java-type", classSchema.qualifiedName);
            schemas.put(classSchema.simpleName, schema);
        }
        return schemas;
    }

    private void collectComponent(String javaType, Set<String> referenced) {
        String type = cleanType(unwrapReturnType(javaType));
        if (type.contains("<") && type.endsWith(">")) {
            String inner = type.substring(type.indexOf('<') + 1, type.length() - 1);
            collectComponent(inner, referenced);
            return;
        }
        if (indexedClasses.containsKey(type)) {
            ClassSchema schema = indexedClasses.get(type);
            if (referenced.add(schema.simpleName)) {
                schema.fields.values().forEach(fieldType -> collectComponent(fieldType, referenced));
            }
        }
    }

    private Map<String, Object> schemaFor(String javaType, Set<String> visited) {
        String type = cleanType(unwrapReturnType(javaType));
        if (type.contains("<") && type.endsWith(">")) {
            String outer = type.substring(0, type.indexOf('<'));
            String inner = type.substring(type.indexOf('<') + 1, type.length() - 1);
            if (isCollection(outer)) {
                return Map.of("type", "array", "items", schemaFor(inner, visited));
            }
            if ("Map".equals(outer) || outer.endsWith(".Map")) {
                return Map.of("type", "object", "additionalProperties", true);
            }
        }
        if (isVoid(type)) {
            return Map.of("type", "string", "nullable", true);
        }
        if (isString(type)) {
            return Map.of("type", "string");
        }
        if (isInteger(type)) {
            return Map.of("type", "integer", "format", "int32");
        }
        if (isLong(type)) {
            return Map.of("type", "integer", "format", "int64");
        }
        if (isNumber(type)) {
            return Map.of("type", "number", "format", "double");
        }
        if (isBoolean(type)) {
            return Map.of("type", "boolean");
        }
        if (isDateTime(type)) {
            return Map.of("type", "string", "format", "date-time");
        }
        if (indexedClasses.containsKey(type)) {
            ClassSchema schema = indexedClasses.get(type);
            return Map.of("$ref", "#/components/schemas/" + schema.simpleName);
        }
        return Map.of("type", "object", "x-java-type", type);
    }

    private List<String> mappingPaths(ClassOrInterfaceDeclaration type, String annotationName) {
        return annotation(type, annotationName).map(this::mappingPaths).orElse(List.of());
    }

    private List<String> mappingPaths(AnnotationExpr annotation) {
        return annotationValues(annotation, "path", "value").stream()
                .map(this::stringValue)
                .filter(StringUtils::hasText)
                .toList();
    }

    private Set<String> mappingMediaTypes(ClassOrInterfaceDeclaration type, String attribute) {
        return annotation(type, "RequestMapping")
                .map(annotation -> mediaTypes(annotation, attribute))
                .orElse(Set.of());
    }

    private Set<String> mediaTypes(AnnotationExpr annotation, String attribute) {
        Set<String> result = new LinkedHashSet<>();
        annotationValues(annotation, attribute).stream()
                .map(this::stringValue)
                .filter(StringUtils::hasText)
                .map(this::mediaTypeValue)
                .forEach(result::add);
        return result;
    }

    private List<String> requestMethods(AnnotationExpr annotation) {
        return annotationValues(annotation, "method").stream()
                .map(Expression::toString)
                .map(value -> value.substring(value.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT))
                .toList();
    }

    private List<Expression> annotationValues(AnnotationExpr annotation, String... names) {
        List<String> requested = List.of(names);
        if (annotation instanceof SingleMemberAnnotationExpr && requested.contains("value")) {
            SingleMemberAnnotationExpr single = (SingleMemberAnnotationExpr) annotation;
            return values(single.getMemberValue());
        }
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            for (MemberValuePair pair : normal.getPairs()) {
                if (requested.contains(pair.getNameAsString())) {
                    return values(pair.getValue());
                }
            }
        }
        return List.of();
    }

    private List<Expression> values(Expression expression) {
        if (expression instanceof ArrayInitializerExpr) {
            ArrayInitializerExpr array = (ArrayInitializerExpr) expression;
            return new ArrayList<>(array.getValues());
        }
        return List.of(expression);
    }

    private String parameterName(Parameter parameter) {
        for (String annotationName : List.of("PathVariable", "RequestParam", "RequestHeader", "DestinationVariable", "Header")) {
            Optional<AnnotationExpr> parameterAnnotation = annotation(parameter, annotationName);
            if (parameterAnnotation.isPresent()) {
                List<Expression> values = annotationValues(parameterAnnotation.get(), "name", "value");
                if (!values.isEmpty()) {
                    String value = stringValue(values.get(0));
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                }
            }
        }
        return parameter.getNameAsString();
    }

    private boolean required(Parameter parameter, boolean defaultValue) {
        for (AnnotationExpr annotation : parameter.getAnnotations()) {
            List<Expression> requiredValues = annotationValues(annotation, "required");
            if (!requiredValues.isEmpty()) {
                return Boolean.parseBoolean(requiredValues.get(0).toString());
            }
        }
        return defaultValue;
    }

    private Map<String, Object> validations(Parameter parameter) {
        Map<String, Object> validations = new LinkedHashMap<>();
        if (hasAnnotation(parameter, "NotNull") || hasAnnotation(parameter, "NotBlank") || hasAnnotation(parameter, "NotEmpty")) {
            validations.put("nullable", false);
        }
        annotation(parameter, "Size").ifPresent(annotation -> {
            annotationValues(annotation, "min").stream().findFirst().ifPresent(value -> validations.put("minLength", integerValue(value)));
            annotationValues(annotation, "max").stream().findFirst().ifPresent(value -> validations.put("maxLength", integerValue(value)));
        });
        annotation(parameter, "Min").flatMap(annotation -> annotationValues(annotation, "value").stream().findFirst())
                .ifPresent(value -> validations.put("minimum", integerValue(value)));
        annotation(parameter, "Max").flatMap(annotation -> annotationValues(annotation, "value").stream().findFirst())
                .ifPresent(value -> validations.put("maximum", integerValue(value)));
        annotation(parameter, "Pattern").flatMap(annotation -> annotationValues(annotation, "regexp").stream().findFirst())
                .ifPresent(value -> validations.put("pattern", stringValue(value)));
        return validations;
    }

    private Optional<String> statusCode(MethodDeclaration method) {
        return annotation(method, "ResponseStatus")
                .flatMap(annotation -> annotationValues(annotation, "code", "value").stream().findFirst())
                .map(Expression::toString)
                .map(value -> {
                    String clean = value.substring(value.lastIndexOf('.') + 1);
                    if ("CREATED".equals(clean)) {
                        return "201";
                    }
                    if ("ACCEPTED".equals(clean)) {
                        return "202";
                    }
                    if ("NO_CONTENT".equals(clean)) {
                        return "204";
                    }
                    if ("BAD_REQUEST".equals(clean)) {
                        return "400";
                    }
                    if ("NOT_FOUND".equals(clean)) {
                        return "404";
                    }
                    return "200";
                });
    }

    private Optional<AnnotationExpr> annotation(ClassOrInterfaceDeclaration type, String name) {
        return annotation(type.getAnnotations(), name);
    }

    private Optional<AnnotationExpr> annotation(MethodDeclaration method, String name) {
        return annotation(method.getAnnotations(), name);
    }

    private Optional<AnnotationExpr> annotation(Parameter parameter, String name) {
        return annotation(parameter.getAnnotations(), name);
    }

    private Optional<AnnotationExpr> annotation(NodeList<AnnotationExpr> annotations, String name) {
        return annotations.stream().filter(annotation -> annotation.getNameAsString().equals(name)).findFirst();
    }

    private boolean hasAnnotation(ClassOrInterfaceDeclaration type, String name) {
        return annotation(type, name).isPresent();
    }

    private boolean hasAnnotation(MethodDeclaration method, String name) {
        return annotation(method, name).isPresent();
    }

    private boolean hasAnnotation(Parameter parameter, String name) {
        return annotation(parameter, name).isPresent();
    }

    private String qualifiedName(CompilationUnit compilationUnit, ClassOrInterfaceDeclaration type) {
        return compilationUnit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getNameAsString() + "." + type.getNameAsString())
                .orElse(type.getNameAsString());
    }

    private String normalizePath(String basePath, String methodPath) {
        String combined = ("/" + stripQuotes(basePath) + "/" + stripQuotes(methodPath)).replace('\\', '/');
        combined = combined.replaceAll("/+", "/");
        if (combined.length() > 1 && combined.endsWith("/")) {
            combined = combined.substring(0, combined.length() - 1);
        }
        return combined;
    }

    private String stringValue(Expression expression) {
        if (expression.isStringLiteralExpr()) {
            return expression.asStringLiteralExpr().asString();
        }
        return stripQuotes(expression.toString());
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            return cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private Integer integerValue(Expression expression) {
        try {
            return Integer.parseInt(stripQuotes(expression.toString()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String mediaTypeValue(String value) {
        if ("MediaType.APPLICATION_JSON_VALUE".equals(value) || "APPLICATION_JSON_VALUE".equals(value)) {
            return "application/json";
        }
        if ("MediaType.APPLICATION_XML_VALUE".equals(value) || "APPLICATION_XML_VALUE".equals(value)) {
            return "application/xml";
        }
        if ("MediaType.TEXT_PLAIN_VALUE".equals(value) || "TEXT_PLAIN_VALUE".equals(value)) {
            return "text/plain";
        }
        return stripQuotes(value);
    }

    private String operationId(ExtractedEndpoint endpoint) {
        return endpoint.getControllerMethod() + "_" + endpoint.getHttpMethod() + "_"
                + endpoint.getPath().replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private String unwrapReturnType(String javaType) {
        String type = cleanType(javaType);
        if (type.startsWith("ResponseEntity<") && type.endsWith(">")) {
            return type.substring("ResponseEntity<".length(), type.length() - 1);
        }
        return type;
    }

    private String cleanType(String javaType) {
        if (javaType == null) {
            return "void";
        }
        return javaType.trim().replace("? extends ", "").replace("? super ", "");
    }

    private String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    private boolean isVoid(String type) {
        return "void".equals(type) || "Void".equals(type);
    }

    private boolean isCollection(String type) {
        return Set.of("List", "Set", "Collection", "Iterable").contains(type) || type.endsWith(".List") || type.endsWith(".Set");
    }

    private boolean isString(String type) {
        return Set.of("String", "char", "Character", "UUID").contains(type) || type.endsWith(".String") || type.endsWith(".UUID");
    }

    private boolean isInteger(String type) {
        return Set.of("int", "Integer", "Short", "short", "Byte", "byte").contains(type);
    }

    private boolean isLong(String type) {
        return Set.of("long", "Long").contains(type);
    }

    private boolean isNumber(String type) {
        return Set.of("double", "Double", "float", "Float", "BigDecimal").contains(type) || type.endsWith(".BigDecimal");
    }

    private boolean isBoolean(String type) {
        return Set.of("boolean", "Boolean").contains(type);
    }

    private boolean isDateTime(String type) {
        return Set.of("LocalDate", "LocalDateTime", "Instant", "OffsetDateTime", "Date").contains(type)
                || type.endsWith(".LocalDate")
                || type.endsWith(".LocalDateTime")
                || type.endsWith(".Instant")
                || type.endsWith(".OffsetDateTime")
                || type.endsWith(".Date");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> root, String key) {
        return (Map<String, Object>) root.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
    }

    private Path sourceRoot() {
        return sourceRoot(null);
    }

    private Path sourceRoot(String override) {
        String rootStr = (override != null && !override.trim().isEmpty()) ? override : properties.getSourceRoot();
        if (rootStr != null && System.getProperty("os.name", "").toLowerCase().contains("win")) {
            if (rootStr.matches("^/[a-zA-Z]/.*")) {
                rootStr = rootStr.substring(1, 2).toUpperCase() + ":" + rootStr.substring(2);
            }
        }
        Path configured = Paths.get(rootStr);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        return Paths.get(System.getProperty("user.dir")).resolve(configured).normalize();
    }

    private static class Mapping {
        private final List<String> paths;
        private final List<String> httpMethods;
        private final Set<String> consumes;
        private final Set<String> produces;

        private Mapping(List<String> paths, List<String> httpMethods, Set<String> consumes, Set<String> produces) {
            this.paths = paths;
            this.httpMethods = httpMethods;
            this.consumes = consumes;
            this.produces = produces;
        }

        private List<String> paths() {
            return paths;
        }

        private List<String> httpMethods() {
            return httpMethods;
        }

        private Set<String> consumes() {
            return consumes;
        }

        private Set<String> produces() {
            return produces;
        }
    }


    private static class ClassSchema {
        private final String simpleName;
        private final String qualifiedName;
        private final Map<String, String> fields = new LinkedHashMap<>();

        private ClassSchema(String simpleName, String qualifiedName) {
            this.simpleName = simpleName;
            this.qualifiedName = qualifiedName;
        }
    }
}
