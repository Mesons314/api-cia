package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NamingConventionRule implements DesignRule {

    @Override
    public String getRuleId() {
        return "SGM-002";
    }

    @Override
    public String getRuleName() {
        return "Naming Convention Check";
    }

    @Override
    public List<ViolationDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec) {
        if (newSpec == null || newSpec.getPaths() == null) {
            return Collections.emptyList();
        }

        List<ViolationDTO> violations = new ArrayList<>();
        for (String path : newSpec.getPaths().keySet()) {
            if (path == null) continue;
            String[] segments = path.split("/");
            for (String segment : segments) {
                if (segment != null && segment.contains("_")) {
                    violations.add(ViolationDTO.builder()
                            .ruleId(getRuleId())
                            .severity("WARNING")
                            .endpoint(path)
                            .message("Path " + path + " uses snake_case naming. Use camelCase instead")
                            .oldValue("N/A")
                            .newValue(path)
                            .build());
                    break; // report once per path
                }
            }
        }
        return violations;
    }
}
