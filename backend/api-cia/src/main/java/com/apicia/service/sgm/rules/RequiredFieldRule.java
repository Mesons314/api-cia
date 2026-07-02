package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RequiredFieldRule implements DesignRule {

    @Override
    public String getRuleId() {
        return "SGM-005";
    }

    @Override
    public String getRuleName() {
        return "Required Field Change Check";
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

            checkRequestBody(violations, path, "GET", oldPathItem.getGet(), newPathItem.getGet());
            checkRequestBody(violations, path, "POST", oldPathItem.getPost(), newPathItem.getPost());
            checkRequestBody(violations, path, "PUT", oldPathItem.getPut(), newPathItem.getPut());
            checkRequestBody(violations, path, "DELETE", oldPathItem.getDelete(), newPathItem.getDelete());
            checkRequestBody(violations, path, "PATCH", oldPathItem.getPatch(), newPathItem.getPatch());
        }

        return violations;
    }

    private void checkRequestBody(List<ViolationDTO> violations, String path, String method, Operation oldOp, Operation newOp) {
        if (oldOp == null || newOp == null) return;

        Set<String> oldRequired = getRequiredFields(oldOp.getRequestBody());
        Set<String> newRequired = getRequiredFields(newOp.getRequestBody());

        // Field was required in old but not in new
        for (String field : oldRequired) {
            if (!newRequired.contains(field)) {
                violations.add(ViolationDTO.builder()
                        .ruleId(getRuleId())
                        .severity("WARNING")
                        .endpoint(path)
                        .message("Required field '" + field + "' made optional in " + path + ". May break server-side validation")
                        .oldValue("REQUIRED")
                        .newValue("OPTIONAL")
                        .build());
            }
        }

        // New required field added in new that was not in old
        for (String field : newRequired) {
            if (!oldRequired.contains(field)) {
                violations.add(ViolationDTO.builder()
                        .ruleId(getRuleId())
                        .severity("BREAKING")
                        .endpoint(path)
                        .message("New required field '" + field + "' added to " + path + ". Existing clients not sending this will fail")
                        .oldValue("N/A")
                        .newValue("REQUIRED")
                        .build());
            }
        }
    }

    private Set<String> getRequiredFields(RequestBody requestBody) {
        Set<String> required = new HashSet<>();
        if (requestBody == null || requestBody.getContent() == null) {
            return required;
        }
        for (MediaType mediaType : requestBody.getContent().values()) {
            if (mediaType == null || mediaType.getSchema() == null) continue;
            Schema<?> schema = mediaType.getSchema();
            List<String> reqList = schema.getRequired();
            if (reqList != null) {
                required.addAll(reqList);
            }
        }
        return required;
    }
}
