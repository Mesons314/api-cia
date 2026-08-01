package com.apicia.service.extraction;

import java.util.List;

public class SecurityPathRule {
    private final List<String> patterns;
    private final List<String> httpMethods;
    private final String ruleType;
    private final List<String> arguments;
    private final boolean authenticationRequired;

    public SecurityPathRule(List<String> patterns, List<String> httpMethods, String ruleType, List<String> arguments, boolean authenticationRequired) {
        this.patterns = patterns;
        this.httpMethods = httpMethods;
        this.ruleType = ruleType;
        this.arguments = arguments;
        this.authenticationRequired = authenticationRequired;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public List<String> getHttpMethods() {
        return httpMethods;
    }

    public String getRuleType() {
        return ruleType;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }
}
