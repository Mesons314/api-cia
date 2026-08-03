package com.apicia.model.extraction;

public class ExtractedResponse {

    private String statusCode;
    private String description;
    private String javaType;

    public ExtractedResponse(String statusCode, String description, String javaType) {
        this.statusCode = statusCode;
        this.description = description;
        this.javaType = javaType;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getDescription() {
        return description;
    }

    public String getJavaType() {
        return javaType;
    }
}
