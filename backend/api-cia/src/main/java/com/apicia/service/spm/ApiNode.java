package com.apicia.service.spm;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiNode {
    private String nodeId;
    private String path;
    private String httpMethod;
    private List<String> parameterNames;
    private String requestBodyRef;
    private List<String> responseRefs;
    private boolean requiresAuth;
}
