package com.apicia.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationDTO {
    private String ruleId;
    private String severity;
    private String endpoint;
    private String message;
    private String oldValue;
    private String newValue;

    private String changeType;
    private String oldPath;
    private String newPath;
    private String controller;
    private String method;
}
