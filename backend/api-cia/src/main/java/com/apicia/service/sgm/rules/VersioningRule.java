package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

// @Component
public class VersioningRule implements DesignRule {

    @Override
    public String getRuleId() {
        return "SGM-001";
    }

    @Override
    public String getRuleName() {
        return "URI Versioning Check";
    }

    @Override
    public List<ViolationDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec) {
        if (newSpec == null || newSpec.getPaths() == null) {
            return Collections.emptyList();
        }

        List<ViolationDTO> violations = new ArrayList<>();
        for (String path : newSpec.getPaths().keySet()) {
            if (path == null) continue;
            if (!path.matches("^/v[0-9]+/.*")) {
                violations.add(ViolationDTO.builder()
                        .ruleId(getRuleId())
                        .severity("BREAKING")
                        .endpoint(path)
                        .message("Path " + path + " is missing version prefix. Expected format: /v1/endpoint")
                        .oldValue("N/A")
                        .newValue(path)
                        .build());
            }
        }
        return violations;
    }
}
