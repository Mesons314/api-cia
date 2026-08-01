package com.apicia.model.extraction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ExtractedEndpoint {

    private String path;
    private String httpMethod;
    private String controllerClass;
    private String controllerMethod;
    private String returnType;
    private String sourceFile;
    private int lineNumber;
    private Set<String> consumes = new LinkedHashSet<>();
    private Set<String> produces = new LinkedHashSet<>();
    private List<ExtractedParameter> parameters = new ArrayList<>();
    private ExtractedRequestBody requestBody;
    private List<ExtractedResponse> responses = new ArrayList<>();

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getControllerClass() {
        return controllerClass;
    }

    public void setControllerClass(String controllerClass) {
        this.controllerClass = controllerClass;
    }

    public String getControllerMethod() {
        return controllerMethod;
    }

    public void setControllerMethod(String controllerMethod) {
        this.controllerMethod = controllerMethod;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Set<String> getConsumes() {
        return consumes;
    }

    public Set<String> getProduces() {
        return produces;
    }

    public List<ExtractedParameter> getParameters() {
        return parameters;
    }

    public ExtractedRequestBody getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(ExtractedRequestBody requestBody) {
        this.requestBody = requestBody;
    }

    public List<ExtractedResponse> getResponses() {
        return responses;
    }

    private String authenticationRequired = "UNKNOWN";
    private String authenticationType = "UNKNOWN";
    private String authorization;

    public String getAuthenticationRequired() {
        return authenticationRequired;
    }

    public void setAuthenticationRequired(String authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    public String getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(String authenticationType) {
        this.authenticationType = authenticationType;
    }

    public String getAuthorization() {
        return authorization;
    }

    public void setAuthorization(String authorization) {
        this.authorization = authorization;
    }
}
