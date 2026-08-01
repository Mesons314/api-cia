package com.apicia.model.extraction;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExtractedParameter {

    private String name;
    private String in;
    private boolean required;
    private String javaType;
    private Map<String, Object> validations = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIn() {
        return in;
    }

    public void setIn(String in) {
        this.in = in;
    }

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
