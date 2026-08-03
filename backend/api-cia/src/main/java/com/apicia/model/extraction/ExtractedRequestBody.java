package com.apicia.model.extraction;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExtractedRequestBody {

    private boolean required;
    private String javaType;
    private Map<String, Object> validations = new LinkedHashMap<>();

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public Map<String, Object> getValidations() {
        return validations;
    }
}
