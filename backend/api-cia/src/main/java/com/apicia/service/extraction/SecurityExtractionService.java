package com.apicia.service.extraction;

import com.apicia.model.extraction.ExtractedEndpoint;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SecurityExtractionService {

    public void extractSecurity(List<ExtractedEndpoint> endpoints, List<SourceUnit> units) {
        // 1. Find all security filter chain methods and extract path rules
        List<SecurityPathRule> pathRules = new ArrayList<>();
        String detectedAuthType = "UNKNOWN";

        for (SourceUnit unit : units) {
            for (ClassOrInterfaceDeclaration clazz : unit.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                for (MethodDeclaration method : clazz.getMethods()) {
                    if (isSecurityFilterChain(method)) {
                        parseSecurityFilterChain(method, pathRules);
                        String authType = detectAuthType(units, method);
                        if (!"UNKNOWN".equals(authType)) {
                            detectedAuthType = authType;
                        }
                    }
                }
            }
        }

        // 2. Index controller classes and method ASTs to read annotations
        Map<String, ClassOrInterfaceDeclaration> controllerClasses = new HashMap<>();
        for (SourceUnit unit : units) {
            for (ClassOrInterfaceDeclaration clazz : unit.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (isController(clazz)) {
                    controllerClasses.put(qualifiedName(unit.compilationUnit, clazz), clazz);
                }
            }
        }

        // 3. Resolve security for each endpoint
        AntPathMatcher pathMatcher = new AntPathMatcher();
        for (ExtractedEndpoint endpoint : endpoints) {
            // First check if there is method/class level security annotations
            SecurityAnnotationInfo annotInfo = getAnnotationSecurityInfo(endpoint, controllerClasses);
            if (annotInfo != null) {
                if (annotInfo.authenticationRequired) {
                    endpoint.setAuthenticationRequired("true");
                    endpoint.setAuthenticationType(detectedAuthType);
                    endpoint.setAuthorization(annotInfo.authorization);
                } else {
                    endpoint.setAuthenticationRequired("false");
                    endpoint.setAuthenticationType("UNKNOWN");
                    endpoint.setAuthorization(null);
                }
                continue;
            }

            // Fallback to SecurityFilterChain path matching rules
            SecurityPathRule matchingRule = matchPathRule(endpoint, pathRules, pathMatcher);
            if (matchingRule != null) {
                if (matchingRule.isAuthenticationRequired()) {
                    endpoint.setAuthenticationRequired("true");
                    endpoint.setAuthenticationType(detectedAuthType);
                    endpoint.setAuthorization(formatAuthorization(matchingRule));
                } else {
                    endpoint.setAuthenticationRequired("false");
                    endpoint.setAuthenticationType("UNKNOWN");
                    endpoint.setAuthorization(null);
                }
            } else {
                // If security cannot be determined statically
                endpoint.setAuthenticationRequired("UNKNOWN");
                endpoint.setAuthenticationType("UNKNOWN");
                endpoint.setAuthorization(null);
            }
        }
    }

    public Map<String, Object> getSecuritySchemes(List<ExtractedEndpoint> endpoints) {
        Map<String, Object> schemes = new LinkedHashMap<>();
        boolean hasJwt = false;
        boolean hasBasic = false;
        boolean hasOAuth2 = false;

        for (ExtractedEndpoint endpoint : endpoints) {
            if ("true".equals(endpoint.getAuthenticationRequired())) {
                if ("JWT".equals(endpoint.getAuthenticationType())) {
                    hasJwt = true;
                } else if ("Basic".equals(endpoint.getAuthenticationType())) {
                    hasBasic = true;
                } else if ("OAuth2".equals(endpoint.getAuthenticationType())) {
                    hasOAuth2 = true;
                }
            }
        }

        if (hasJwt) {
            schemes.put("bearerAuth", Map.of(
                    "type", "http",
                    "scheme", "bearer",
                    "bearerFormat", "JWT"
            ));
        }
        if (hasBasic) {
            schemes.put("basicAuth", Map.of(
                    "type", "http",
                    "scheme", "basic"
            ));
        }
        if (hasOAuth2) {
            schemes.put("oauth2Auth", Map.of(
                    "type", "oauth2",
                    "flows", Map.of(
                            "implicit", Map.of(
                                    "authorizationUrl", "http://example.com/oauth/authorize",
                                    "scopes", Map.of()
                            )
                    )
            ));
        }
        return schemes;
    }

    private boolean isSecurityFilterChain(MethodDeclaration method) {
        return method.getType().asString().equals("SecurityFilterChain") || hasAnnotation(method, "Bean");
    }

    private void parseSecurityFilterChain(MethodDeclaration method, List<SecurityPathRule> rules) {
        // Look for lambda parameters in authorizeHttpRequests or authorizeRequests
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            String callName = call.getNameAsString();
            if (callName.equals("authorizeHttpRequests") || callName.equals("authorizeRequests") || callName.equals("authorizeUrls")) {
                if (call.getArguments().size() > 0 && call.getArgument(0) instanceof LambdaExpr lambda) {
                    String paramName = lambda.getParameter(0).getNameAsString();
                    com.github.javaparser.ast.stmt.Statement body = lambda.getBody();
                    if (body.isBlockStmt()) {
                        body.asBlockStmt().getStatements().forEach(stmt -> {
                            if (stmt.isExpressionStmt()) {
                                parseCallChain(stmt.asExpressionStmt().getExpression(), paramName, rules, new ArrayList<>(), new ArrayList<>());
                            }
                        });
                    } else if (body.isExpressionStmt()) {
                        parseCallChain(body.asExpressionStmt().getExpression(), paramName, rules, new ArrayList<>(), new ArrayList<>());
                    }
                }
            }
        }
    }

    private void parseCallChain(Expression expr, String paramName, List<SecurityPathRule> rules, List<String> currentPatterns, List<String> currentMethods) {
        if (expr instanceof MethodCallExpr call) {
            String name = call.getNameAsString();
            Expression scope = call.getScope().orElse(null);

            if (scope != null) {
                parseCallChain(scope, paramName, rules, currentPatterns, currentMethods);
            }

            if (name.equals("requestMatchers") || name.equals("antMatchers") || name.equals("mvcMatchers") || name.equals("regexMatchers")) {
                currentPatterns.clear();
                currentMethods.clear();
                for (Expression arg : call.getArguments()) {
                    if (arg instanceof StringLiteralExpr str) {
                        currentPatterns.add(str.getValue());
                    } else if (arg.toString().contains("HttpMethod.")) {
                        String methodText = arg.toString();
                        if (methodText.contains("GET")) currentMethods.add("GET");
                        else if (methodText.contains("POST")) currentMethods.add("POST");
                        else if (methodText.contains("PUT")) currentMethods.add("PUT");
                        else if (methodText.contains("DELETE")) currentMethods.add("DELETE");
                        else if (methodText.contains("PATCH")) currentMethods.add("PATCH");
                    }
                }
            } else if (name.equals("anyRequest")) {
                currentPatterns.clear();
                currentPatterns.add("/**");
                currentMethods.clear();
            } else if (name.equals("permitAll")) {
                if (!currentPatterns.isEmpty()) {
                    rules.add(new SecurityPathRule(new ArrayList<>(currentPatterns), new ArrayList<>(currentMethods), "permitAll", new ArrayList<>(), false));
                    currentPatterns.clear();
                    currentMethods.clear();
                }
            } else if (name.equals("authenticated")) {
                if (!currentPatterns.isEmpty()) {
                    rules.add(new SecurityPathRule(new ArrayList<>(currentPatterns), new ArrayList<>(currentMethods), "authenticated", new ArrayList<>(), true));
                    currentPatterns.clear();
                    currentMethods.clear();
                }
            } else if (name.equals("hasRole")) {
                String role = extractStringArg(call);
                if (!currentPatterns.isEmpty()) {
                    rules.add(new SecurityPathRule(new ArrayList<>(currentPatterns), new ArrayList<>(currentMethods), "hasRole", List.of(role), true));
                    currentPatterns.clear();
                    currentMethods.clear();
                }
            } else if (name.equals("hasAnyRole")) {
                List<String> roles = extractStringArgs(call);
                if (!currentPatterns.isEmpty()) {
                    rules.add(new SecurityPathRule(new ArrayList<>(currentPatterns), new ArrayList<>(currentMethods), "hasAnyRole", roles, true));
                    currentPatterns.clear();
                    currentMethods.clear();
                }
            } else if (name.equals("hasAuthority")) {
                String authority = extractStringArg(call);
                if (!currentPatterns.isEmpty()) {
                    rules.add(new SecurityPathRule(new ArrayList<>(currentPatterns), new ArrayList<>(currentMethods), "hasAuthority", List.of(authority), true));
                    currentPatterns.clear();
                    currentMethods.clear();
                }
            } else if (name.equals("hasAnyAuthority")) {
                List<String> authorities = extractStringArgs(call);
                if (!currentPatterns.isEmpty()) {
                    rules.add(new SecurityPathRule(new ArrayList<>(currentPatterns), new ArrayList<>(currentMethods), "hasAnyAuthority", authorities, true));
                    currentPatterns.clear();
                    currentMethods.clear();
                }
            }
        }
    }

    private String extractStringArg(MethodCallExpr call) {
        if (call.getArguments().size() > 0) {
            Expression arg = call.getArgument(0);
            if (arg instanceof StringLiteralExpr str) {
                return str.getValue();
            }
            return arg.toString().replace("\"", "").replace("'", "");
        }
        return "";
    }

    private List<String> extractStringArgs(MethodCallExpr call) {
        List<String> args = new ArrayList<>();
        for (Expression arg : call.getArguments()) {
            if (arg instanceof StringLiteralExpr str) {
                args.add(str.getValue());
            } else {
                args.add(arg.toString().replace("\"", "").replace("'", ""));
            }
        }
        return args;
    }

    private String detectAuthType(List<SourceUnit> units, MethodDeclaration securityMethod) {
        boolean hasHttpBasic = false;
        boolean hasOAuth2 = false;
        boolean hasJwtFilter = false;

        for (MethodCallExpr call : securityMethod.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            if (name.equals("httpBasic")) {
                hasHttpBasic = true;
            } else if (name.equals("oauth2ResourceServer") || name.equals("oauth2Login") || name.equals("oauth2Client")) {
                hasOAuth2 = true;
            } else if (name.equals("addFilterBefore") || name.equals("addFilterAfter") || name.equals("addFilterAt") || name.equals("addFilter")) {
                for (Expression arg : call.getArguments()) {
                    String argStr = arg.toString().toLowerCase();
                    if (argStr.contains("jwt") || argStr.contains("token")) {
                        hasJwtFilter = true;
                    }
                }
            }
        }

        boolean hasJwtClass = false;
        for (SourceUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                String className = type.getNameAsString().toLowerCase();
                if (className.contains("jwtconfig") || className.contains("jwtfilter") || className.contains("jwtauth") || className.contains("jwtprovider") || className.contains("jwtutils")) {
                    hasJwtClass = true;
                }
            }
        }

        if (hasJwtFilter || hasJwtClass) {
            return "JWT";
        }
        if (hasOAuth2) {
            return "OAuth2";
        }
        if (hasHttpBasic) {
            return "Basic";
        }
        return "UNKNOWN";
    }

    private SecurityAnnotationInfo getAnnotationSecurityInfo(ExtractedEndpoint endpoint, Map<String, ClassOrInterfaceDeclaration> controllerClasses) {
        ClassOrInterfaceDeclaration clazz = controllerClasses.get(endpoint.getControllerClass());
        if (clazz == null) {
            return null;
        }

        // 1. Check Method-level Annotations
        for (MethodDeclaration method : clazz.getMethods()) {
            if (method.getNameAsString().equals(endpoint.getControllerMethod())) {
                for (AnnotationExpr annotation : method.getAnnotations()) {
                    SecurityAnnotationInfo info = getSecurityAnnotation(annotation);
                    if (info != null) {
                        return info;
                    }
                }
            }
        }

        // 2. Check Class-level Annotations
        for (AnnotationExpr annotation : clazz.getAnnotations()) {
            SecurityAnnotationInfo info = getSecurityAnnotation(annotation);
            if (info != null) {
                return info;
            }
        }

        return null;
    }

    private SecurityAnnotationInfo getSecurityAnnotation(AnnotationExpr annotation) {
        String name = annotation.getNameAsString();
        if (name.equals("PermitAll")) {
            return new SecurityAnnotationInfo(false, null);
        }
        if (name.equals("PreAuthorize")) {
            String val = extractAnnotationValue(annotation);
            if (val.replace(" ", "").replace("\"", "").replace("'", "").equals("permitAll()")) {
                return new SecurityAnnotationInfo(false, null);
            }
            return new SecurityAnnotationInfo(true, val);
        }
        if (name.equals("RolesAllowed")) {
            List<String> roles = extractAnnotationValues(annotation);
            String rolesStr = String.join(", ", roles);
            return new SecurityAnnotationInfo(true, "hasAnyRole(" + rolesStr + ")");
        }
        if (name.equals("Secured")) {
            List<String> roles = extractAnnotationValues(annotation);
            String rolesStr = String.join(", ", roles);
            return new SecurityAnnotationInfo(true, "hasAnyRole(" + rolesStr + ")");
        }
        return null;
    }

    private String extractAnnotationValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return cleanStringValue(single.getMemberValue());
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("value")) {
                    return cleanStringValue(pair.getValue());
                }
            }
        }
        return "";
    }

    private List<String> extractAnnotationValues(AnnotationExpr annotation) {
        List<String> values = new ArrayList<>();
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            collectValues(single.getMemberValue(), values);
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("value")) {
                    collectValues(pair.getValue(), values);
                }
            }
        }
        return values;
    }

    private void collectValues(Expression expr, List<String> values) {
        if (expr instanceof ArrayInitializerExpr array) {
            for (Expression element : array.getValues()) {
                values.add(cleanStringValue(element));
            }
        } else {
            values.add(cleanStringValue(expr));
        }
    }

    private String cleanStringValue(Expression expr) {
        String str = expr.toString();
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return str.substring(1, str.length() - 1);
        }
        if (str.startsWith("'") && str.endsWith("'")) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    private SecurityPathRule matchPathRule(ExtractedEndpoint endpoint, List<SecurityPathRule> pathRules, AntPathMatcher pathMatcher) {
        List<SecurityPathRule> matchingRules = new ArrayList<>();
        for (SecurityPathRule rule : pathRules) {
            if (rule.getHttpMethods() != null && !rule.getHttpMethods().isEmpty()) {
                if (!rule.getHttpMethods().contains(endpoint.getHttpMethod().toUpperCase())) {
                    continue;
                }
            }
            for (String pattern : rule.getPatterns()) {
                if (pathMatcher.match(pattern, endpoint.getPath())) {
                    matchingRules.add(rule);
                    break;
                }
            }
        }

        if (matchingRules.isEmpty()) {
            return null;
        }

        matchingRules.sort((rule1, rule2) -> {
            String bestPattern1 = findBestPattern(rule1.getPatterns(), endpoint.getPath(), pathMatcher);
            String bestPattern2 = findBestPattern(rule2.getPatterns(), endpoint.getPath(), pathMatcher);

            Comparator<String> patternComparator = pathMatcher.getPatternComparator(endpoint.getPath());
            return patternComparator.compare(bestPattern1, bestPattern2);
        });

        return matchingRules.get(0);
    }

    private String findBestPattern(List<String> patterns, String path, AntPathMatcher pathMatcher) {
        List<String> matches = new ArrayList<>();
        for (String p : patterns) {
            if (pathMatcher.match(p, path)) {
                matches.add(p);
            }
        }
        if (matches.isEmpty()) {
            return "/**";
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        matches.sort(pathMatcher.getPatternComparator(path));
        return matches.get(0);
    }

    private String formatAuthorization(SecurityPathRule rule) {
        String type = rule.getRuleType();
        if (type.equals("authenticated")) {
            return "authenticated";
        }
        if (type.equals("permitAll")) {
            return "permitAll()";
        }
        if (type.equals("hasRole") || type.equals("hasAuthority")) {
            return type + "('" + rule.getArguments().get(0) + "')";
        }
        if (type.equals("hasAnyRole") || type.equals("hasAnyAuthority")) {
            List<String> quotedArgs = rule.getArguments().stream().map(arg -> "'" + arg + "'").toList();
            return type + "(" + String.join(", ", quotedArgs) + ")";
        }
        return "UNKNOWN";
    }

    private boolean isController(ClassOrInterfaceDeclaration type) {
        return hasAnnotation(type, "RestController")
                || hasAnnotation(type, "Controller")
                || type.getNameAsString().endsWith("Controller");
    }

    private boolean hasAnnotation(ClassOrInterfaceDeclaration type, String annotationName) {
        return type.getAnnotationByName(annotationName).isPresent();
    }

    private boolean hasAnnotation(MethodDeclaration method, String annotationName) {
        return method.getAnnotationByName(annotationName).isPresent();
    }

    private String qualifiedName(CompilationUnit cu, ClassOrInterfaceDeclaration type) {
        String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString() + ".").orElse("");
        return packageName + type.getNameAsString();
    }

    private static class SecurityAnnotationInfo {
        public final boolean authenticationRequired;
        public final String authorization;

        public SecurityAnnotationInfo(boolean authenticationRequired, String authorization) {
            this.authenticationRequired = authenticationRequired;
            this.authorization = authorization;
        }
    }
}
