package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RemovedEndpointRule implements DesignRule {

    @Override
    public String getRuleId() {
        return "SGM-003";
    }

    @Override
    public String getRuleName() {
        return "Removed Endpoint Check";
    }

    @Override
    public List<ViolationDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec) {
        if (oldSpec == null || oldSpec.getPaths() == null) {
            return Collections.emptyList();
        }

        List<ViolationDTO> violations = new ArrayList<>();

        for (Map.Entry<String, PathItem> entry : oldSpec.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem oldPathItem = entry.getValue();
            if (oldPathItem == null) continue;

            checkAndAdd(violations, newSpec, path, "GET", oldPathItem.getGet());
            checkAndAdd(violations, newSpec, path, "POST", oldPathItem.getPost());
            checkAndAdd(violations, newSpec, path, "PUT", oldPathItem.getPut());
            checkAndAdd(violations, newSpec, path, "DELETE", oldPathItem.getDelete());
            checkAndAdd(violations, newSpec, path, "PATCH", oldPathItem.getPatch());
        }

        return violations;
    }

    private void checkAndAdd(List<ViolationDTO> violations, OpenAPI newSpec, String path, String method, Object oldOperation) {
        if (oldOperation == null) return;

        boolean existsInNew = false;
        if (newSpec != null && newSpec.getPaths() != null) {
            PathItem newPathItem = newSpec.getPaths().get(path);
            if (newPathItem != null) {
                Object newOperation = null;
                switch (method) {
                    case "GET": newOperation = newPathItem.getGet(); break;
                    case "POST": newOperation = newPathItem.getPost(); break;
                    case "PUT": newOperation = newPathItem.getPut(); break;
                    case "DELETE": newOperation = newPathItem.getDelete(); break;
                    case "PATCH": newOperation = newPathItem.getPatch(); break;
                }
                if (newOperation != null) {
                    existsInNew = true;
                }
            }
        }

        if (!existsInNew) {
            violations.add(ViolationDTO.builder()
                    .ruleId(getRuleId())
                    .severity("BREAKING")
                    .endpoint(path)
                    .message("Endpoint " + method + " " + path + " was removed. Existing clients will get 404")
                    .oldValue("EXISTS")
                    .newValue("REMOVED")
                    .build());
        }
    }
}
