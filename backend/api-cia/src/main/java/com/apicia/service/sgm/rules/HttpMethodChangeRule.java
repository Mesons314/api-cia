package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class HttpMethodChangeRule implements DesignRule {

    @Override
    public String getRuleId() {
        return "SGM-006";
    }

    @Override
    public String getRuleName() {
        return "HTTP Method Change Check";
    }

    @Override
    public List<ViolationDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec) {
        if (oldSpec == null || oldSpec.getPaths() == null || newSpec == null || newSpec.getPaths() == null) {
            return Collections.emptyList();
        }

        List<ViolationDTO> violations = new ArrayList<>();

        for (Map.Entry<String, PathItem> entry : oldSpec.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem oldPathItem = entry.getValue();
            if (oldPathItem == null) continue;

            PathItem newPathItem = newSpec.getPaths().get(path);
            if (newPathItem == null) continue;

            Set<String> oldMethods = getMethods(oldPathItem);
            Set<String> newMethods = getMethods(newPathItem);

            if (oldMethods.isEmpty() || newMethods.isEmpty()) continue;

            for (String oldMethod : oldMethods) {
                if (!newMethods.contains(oldMethod)) {
                    for (String newMethod : newMethods) {
                        if (!oldMethods.contains(newMethod)) {
                            violations.add(ViolationDTO.builder()
                                    .ruleId(getRuleId())
                                    .severity("CRITICAL")
                                    .endpoint(path)
                                    .message("HTTP method for " + path + " changed from " + oldMethod + " to " + newMethod + ". All existing clients will break")
                                    .oldValue(oldMethod)
                                    .newValue(newMethod)
                                    .build());
                            break;
                        }
                    }
                }
            }
        }

        return violations;
    }

    private Set<String> getMethods(PathItem pathItem) {
        Set<String> methods = new LinkedHashSet<>();
        if (pathItem.getGet() != null) methods.add("GET");
        if (pathItem.getPost() != null) methods.add("POST");
        if (pathItem.getPut() != null) methods.add("PUT");
        if (pathItem.getDelete() != null) methods.add("DELETE");
        if (pathItem.getPatch() != null) methods.add("PATCH");
        return methods;
    }
}
