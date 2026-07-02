package com.apicia.service.sam;

import com.apicia.model.dto.SecurityAlertDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AuthRemovedCheck implements SecurityCheck {

    @Override
    public String getCheckId() {
        return "SAM-001";
    }

    @Override
    public List<SecurityAlertDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec) {
        if (newSpec == null || newSpec.getPaths() == null || oldSpec == null || oldSpec.getPaths() == null) {
            return Collections.emptyList();
        }

        List<SecurityAlertDTO> alerts = new ArrayList<>();

        for (Map.Entry<String, PathItem> entry : newSpec.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem newPathItem = entry.getValue();
            if (newPathItem == null) continue;

            PathItem oldPathItem = oldSpec.getPaths().get(path);
            if (oldPathItem == null) continue;

            checkAuthRemoved(alerts, path, "GET", oldPathItem.getGet(), newPathItem.getGet());
            checkAuthRemoved(alerts, path, "POST", oldPathItem.getPost(), newPathItem.getPost());
            checkAuthRemoved(alerts, path, "PUT", oldPathItem.getPut(), newPathItem.getPut());
            checkAuthRemoved(alerts, path, "DELETE", oldPathItem.getDelete(), newPathItem.getDelete());
            checkAuthRemoved(alerts, path, "PATCH", oldPathItem.getPatch(), newPathItem.getPatch());
        }

        return alerts;
    }

    private void checkAuthRemoved(List<SecurityAlertDTO> alerts, String path, String method, Operation oldOp, Operation newOp) {
        if (oldOp == null || newOp == null) return;

        boolean oldHasAuth = oldOp.getSecurity() != null && !oldOp.getSecurity().isEmpty();
        boolean newHasAuth = newOp.getSecurity() != null && !newOp.getSecurity().isEmpty();

        if (oldHasAuth && !newHasAuth) {
            alerts.add(SecurityAlertDTO.builder()
                    .checkId(getCheckId())
                    .severity("CRITICAL")
                    .endpoint(method + " " + path)
                    .description("Authentication removed from " + method + " " + path + ". This endpoint is now publicly accessible without any credentials")
                    .build());
        }
    }
}
