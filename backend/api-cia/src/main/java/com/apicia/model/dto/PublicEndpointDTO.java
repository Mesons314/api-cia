package com.apicia.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicEndpointDTO {
    private String path;
    private String httpMethod;
    private String controllerClass;
    private String controllerMethod;
    private String sourceFile;
    private Integer lineNumber;
    private String reason;
}
