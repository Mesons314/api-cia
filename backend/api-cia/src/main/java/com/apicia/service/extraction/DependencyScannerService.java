package com.apicia.service.extraction;

import com.apicia.model.entity.ClientDependency;
import com.apicia.model.entity.ClientProject;
import com.apicia.repository.ClientDependencyRepository;
import com.apicia.repository.ClientProjectRepository;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DependencyScannerService {

    private final ClientProjectRepository clientProjectRepository;
    private final ClientDependencyRepository clientDependencyRepository;

    public DependencyScannerService(
            ClientProjectRepository clientProjectRepository,
            ClientDependencyRepository clientDependencyRepository
    ) {
        this.clientProjectRepository = clientProjectRepository;
        this.clientDependencyRepository = clientDependencyRepository;
    }

    public ClientProject registerAndScan(String projectName, String sourcePath) {
        Path root = Paths.get(sourcePath);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source path does not exist or is not a directory: " + sourcePath);
        }

        ClientProject project = clientProjectRepository.findByProjectName(projectName)
                .orElse(ClientProject.builder()
                        .projectName(projectName)
                        .sourcePath(sourcePath)
                        .build());

        project.setSourcePath(sourcePath);
        project.setScannedAt(LocalDateTime.now());
        project = clientProjectRepository.save(project);

        scanProject(project);

        return project;
    }

    public void scanProject(ClientProject project) {
        // Clear old dependencies
        clientDependencyRepository.deleteByClientProjectId(project.getId());

        Path sourceRoot = Paths.get(project.getSourcePath());
        List<ClientDependency> dependencies = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            CompilationUnit cu = StaticJavaParser.parse(path);
                            Path relativePath = sourceRoot.relativize(path);
                            
                            scanFeignClients(project, cu, relativePath, dependencies);
                            scanRestTemplateCalls(project, cu, relativePath, dependencies);
                            scanWebClientCalls(project, cu, relativePath, dependencies);
                            
                        } catch (Exception e) {
                            // Suppress individual file parse errors and continue
                            System.err.println("Failed to parse client source file: " + path + ". Error: " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to traverse source directory: " + project.getSourcePath(), e);
        }

        if (!dependencies.isEmpty()) {
            clientDependencyRepository.saveAll(dependencies);
        }
    }

    private void scanFeignClients(ClientProject project, CompilationUnit cu, Path relativePath, List<ClientDependency> dependencies) {
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            Optional<AnnotationExpr> feignAnnOpt = type.getAnnotationByName("FeignClient");
            if (feignAnnOpt.isEmpty()) {
                continue;
            }
            AnnotationExpr feignAnn = feignAnnOpt.get();

            String basePath = "";
            if (feignAnn.isNormalAnnotationExpr()) {
                NormalAnnotationExpr normal = feignAnn.asNormalAnnotationExpr();
                for (MemberValuePair pair : normal.getPairs()) {
                    if ("path".equals(pair.getNameAsString())) {
                        basePath = stripQuotes(pair.getValue().toString());
                    }
                }
            }

            Optional<AnnotationExpr> reqMappingOpt = type.getAnnotationByName("RequestMapping");
            if (reqMappingOpt.isPresent()) {
                List<String> mappingPaths = getPathsFromMapping(reqMappingOpt.get());
                if (!mappingPaths.isEmpty()) {
                    basePath = combinePaths(basePath, mappingPaths.get(0));
                }
            }

            for (MethodDeclaration method : type.getMethods()) {
                for (String mappingAnnName : List.of("GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping")) {
                    Optional<AnnotationExpr> methodAnnOpt = method.getAnnotationByName(mappingAnnName);
                    if (methodAnnOpt.isEmpty()) {
                        continue;
                    }
                    AnnotationExpr methodAnn = methodAnnOpt.get();
                    List<String> methodPaths = getPathsFromMapping(methodAnn);
                    String httpMethod = getHttpMethodFromAnnotation(mappingAnnName, methodAnn);

                    if (methodPaths.isEmpty()) {
                        methodPaths = List.of("");
                    }

                    for (String methodPath : methodPaths) {
                        String rawPath = combinePaths(basePath, methodPath);
                        String normalized = normalizePath(rawPath);

                        dependencies.add(ClientDependency.builder()
                                .clientProject(project)
                                .filePath(relativePath.toString().replace('\\', '/'))
                                .lineNumber(method.getRange().map(range -> range.begin.line).orElse(0))
                                .httpMethod(httpMethod)
                                .rawPath(rawPath)
                                .normalizedPath(normalized)
                                .build());
                    }
                }
            }
        }
    }

    private void scanRestTemplateCalls(ClientProject project, CompilationUnit cu, Path relativePath, List<ClientDependency> dependencies) {
        List<String> restTemplateMethods = List.of(
                "getForObject", "getForEntity", "postForObject", "postForEntity",
                "put", "delete", "exchange", "execute", "patchForObject"
        );

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            if (!restTemplateMethods.contains(name)) {
                continue;
            }
            if (call.getArguments().isEmpty()) {
                continue;
            }

            Expression urlExpr = call.getArguments().get(0);
            List<String> parts = new ArrayList<>();
            collectStringParts(urlExpr, parts);
            String rawPath = String.join("", parts);
            String normalized = normalizePath(rawPath);
            String httpMethod = getMethodFromRestTemplateCall(name, call);

            dependencies.add(ClientDependency.builder()
                    .clientProject(project)
                    .filePath(relativePath.toString().replace('\\', '/'))
                    .lineNumber(call.getRange().map(range -> range.begin.line).orElse(0))
                    .httpMethod(httpMethod)
                    .rawPath(rawPath)
                    .normalizedPath(normalized)
                    .build());
        }
    }

    private void scanWebClientCalls(ClientProject project, CompilationUnit cu, Path relativePath, List<ClientDependency> dependencies) {
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!"uri".equals(call.getNameAsString())) {
                continue;
            }
            if (call.getArguments().isEmpty()) {
                continue;
            }

            String httpMethod = "GET";
            if (call.getScope().isPresent() && call.getScope().get().isMethodCallExpr()) {
                MethodCallExpr scopeCall = call.getScope().get().asMethodCallExpr();
                String scopeName = scopeCall.getNameAsString().toLowerCase();
                if (List.of("get", "post", "put", "delete", "patch").contains(scopeName)) {
                    httpMethod = scopeName.toUpperCase();
                } else if ("method".equals(scopeName) && !scopeCall.getArguments().isEmpty()) {
                    String methodArg = scopeCall.getArguments().get(0).toString().toUpperCase();
                    if (methodArg.contains("GET")) httpMethod = "GET";
                    else if (methodArg.contains("POST")) httpMethod = "POST";
                    else if (methodArg.contains("PUT")) httpMethod = "PUT";
                    else if (methodArg.contains("DELETE")) httpMethod = "DELETE";
                    else if (methodArg.contains("PATCH")) httpMethod = "PATCH";
                }
            }

            Expression urlExpr = call.getArguments().get(0);
            List<String> parts = new ArrayList<>();
            collectStringParts(urlExpr, parts);
            String rawPath = String.join("", parts);
            String normalized = normalizePath(rawPath);

            dependencies.add(ClientDependency.builder()
                    .clientProject(project)
                    .filePath(relativePath.toString().replace('\\', '/'))
                    .lineNumber(call.getRange().map(range -> range.begin.line).orElse(0))
                    .httpMethod(httpMethod)
                    .rawPath(rawPath)
                    .normalizedPath(normalized)
                    .build());
        }
    }

    private void collectStringParts(Expression expr, List<String> parts) {
        Expression resolved = resolveExpression(expr);
        if (resolved.isStringLiteralExpr()) {
            parts.add(resolved.asStringLiteralExpr().asString());
        } else if (resolved.isBinaryExpr()) {
            BinaryExpr binaryExpr = resolved.asBinaryExpr();
            if (binaryExpr.getOperator() == BinaryExpr.Operator.PLUS) {
                collectStringParts(binaryExpr.getLeft(), parts);
                collectStringParts(binaryExpr.getRight(), parts);
            }
        } else {
            parts.add("{}");
        }
    }

    private Expression resolveExpression(Expression expr) {
        if (expr.isNameExpr()) {
            String varName = expr.asNameExpr().getNameAsString();
            Optional<MethodDeclaration> methodOpt = expr.findAncestor(MethodDeclaration.class);
            if (methodOpt.isPresent()) {
                for (VariableDeclarator varDecl : methodOpt.get().findAll(VariableDeclarator.class)) {
                    if (varDecl.getNameAsString().equals(varName) && varDecl.getInitializer().isPresent()) {
                        return resolveExpression(varDecl.getInitializer().get());
                    }
                }
            }
        }
        return expr;
    }

    private String getMethodFromRestTemplateCall(String methodName, MethodCallExpr call) {
        if (methodName.startsWith("get")) return "GET";
        if (methodName.startsWith("post")) return "POST";
        if (methodName.startsWith("put")) return "PUT";
        if (methodName.startsWith("delete")) return "DELETE";
        if (methodName.startsWith("patch")) return "PATCH";

        if (call.getArguments().size() >= 2) {
            Expression methodExpr = call.getArguments().get(1);
            String val = methodExpr.toString().toUpperCase();
            if (val.contains("GET")) return "GET";
            if (val.contains("POST")) return "POST";
            if (val.contains("PUT")) return "PUT";
            if (val.contains("DELETE")) return "DELETE";
            if (val.contains("PATCH")) return "PATCH";
        }
        return "GET";
    }

    private String getHttpMethodFromAnnotation(String annName, AnnotationExpr ann) {
        if ("GetMapping".equals(annName)) return "GET";
        if ("PostMapping".equals(annName)) return "POST";
        if ("PutMapping".equals(annName)) return "PUT";
        if ("DeleteMapping".equals(annName)) return "DELETE";
        if ("PatchMapping".equals(annName)) return "PATCH";

        if (ann.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normal = ann.asNormalAnnotationExpr();
            for (MemberValuePair pair : normal.getPairs()) {
                if ("method".equals(pair.getNameAsString())) {
                    String val = pair.getValue().toString();
                    if (val.contains("GET")) return "GET";
                    if (val.contains("POST")) return "POST";
                    if (val.contains("PUT")) return "PUT";
                    if (val.contains("DELETE")) return "DELETE";
                    if (val.contains("PATCH")) return "PATCH";
                }
            }
        }
        return "GET";
    }

    public String normalizePath(String path) {
        if (path == null) {
            return "/";
        }
        String cleaned = path.trim();

        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            int firstSlashAfterProto = cleaned.indexOf('/', cleaned.indexOf("://") + 3);
            if (firstSlashAfterProto != -1) {
                cleaned = cleaned.substring(firstSlashAfterProto);
            } else {
                cleaned = "/";
            }
        }

        cleaned = cleaned.replaceAll("\\{[^}]+}", "{}");
        cleaned = cleaned.replace("*", "{}");

        String[] segments = cleaned.split("/");
        StringBuilder sb = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            if (sb.length() > 0) sb.append("/");

            if (segment.matches("-?\\d+")) {
                sb.append("{}");
            } else {
                sb.append(segment);
            }
        }
        cleaned = "/" + sb.toString();
        cleaned = cleaned.replaceAll("/+", "/");

        while (cleaned.startsWith("/{}/") || cleaned.equals("/{}")) {
            if (cleaned.startsWith("/{}/")) {
                cleaned = cleaned.substring(3);
            } else {
                cleaned = "/";
            }
        }
        if (!cleaned.startsWith("/")) {
            cleaned = "/" + cleaned;
        }

        cleaned = cleaned.toLowerCase();

        if (cleaned.length() > 1 && cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        return cleaned;
    }

    private List<String> getPathsFromMapping(AnnotationExpr annotation) {
        List<Expression> values = annotationValues(annotation, "path", "value");
        List<String> paths = new ArrayList<>();
        for (Expression expr : values) {
            paths.add(stripQuotes(expr.toString()));
        }
        return paths;
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

    private String combinePaths(String basePath, String methodPath) {
        String combined = ("/" + basePath + "/" + methodPath).replace('\\', '/');
        combined = combined.replaceAll("/+", "/");
        if (combined.length() > 1 && combined.endsWith("/")) {
            combined = combined.substring(0, combined.length() - 1);
        }
        return combined;
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
}
