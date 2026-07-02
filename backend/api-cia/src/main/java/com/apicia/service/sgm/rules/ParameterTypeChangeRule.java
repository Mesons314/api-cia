package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ParameterTypeChangeRule implements DesignRule {

    @Override
    public String getRuleId() {
        return "SGM-004";
    }

    @Override
    public String getRuleName() {
        return "Parameter Type Change Check";
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

            checkParameters(violations, path, "GET", oldPathItem.getGet(), newPathItem.getGet());
            checkParameters(violations, path, "POST", oldPathItem.getPost(), newPathItem.getPost());
            checkParameters(violations, path, "PUT", oldPathItem.getPut(), newPathItem.getPut());
            checkParameters(violations, path, "DELETE", oldPathItem.getDelete(), newPathItem.getDelete());
            checkParameters(violations, path, "PATCH", oldPathItem.getPatch(), newPathItem.getPatch());
        }

        return violations;
    }

    private void checkParameters(List<ViolationDTO> violations, String path, String method, Operation oldOp, Operation newOp) {
        if (oldOp == null || newOp == null) return;

        List<Parameter> oldParams = oldOp.getParameters();
        List<Parameter> newParams = newOp.getParameters();
        if (oldParams == null || newParams == null) return;

        Map<String, Parameter> oldParamMap = new HashMap<>();
        for (Parameter p : oldParams) {
            if (p != null && p.getName() != null) {
                oldParamMap.put(p.getName(), p);
            }
        }

        for (Parameter newParam : newParams) {
            if (newParam == null || newParam.getName() == null) continue;

            Parameter oldParam = oldParamMap.get(newParam.getName());
            if (oldParam == null) continue;

            String oldType = getParameterType(oldParam);
            String newType = getParameterType(newParam);

            if (oldType == null) oldType = "unknown";
            if (newType == null) newType = "unknown";

            if (!oldType.equals(newType)) {
                violations.add(ViolationDTO.builder()
                        .ruleId(getRuleId())
                        .severity("BREAKING")
                        .endpoint(path)
                        .message("Parameter '" + newParam.getName() + "' in " + method + " " + path + " changed type from " + oldType + " to " + newType)
                        .oldValue(oldType)
                        .newValue(newType)
                        .build());
            }
        }
    }

    private String getParameterType(Parameter param) {
        if (param.getSchema() != null) {
            return param.getSchema().getType();
        }
        return null;
    }
}
