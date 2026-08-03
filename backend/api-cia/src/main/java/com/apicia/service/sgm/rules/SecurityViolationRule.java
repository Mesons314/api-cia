package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SecurityViolationRule implements DesignRule {

    @Override
    public String getRuleId() {
        return "SGM-007";
    }

    @Override
    public String getRuleName() {
        return "Security Configuration Rule Check";
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

            checkSecurity(violations, path, "GET", oldPathItem.getGet(), newPathItem.getGet());
            checkSecurity(violations, path, "POST", oldPathItem.getPost(), newPathItem.getPost());
            checkSecurity(violations, path, "PUT", oldPathItem.getPut(), newPathItem.getPut());
            checkSecurity(violations, path, "DELETE", oldPathItem.getDelete(), newPathItem.getDelete());
            checkSecurity(violations, path, "PATCH", oldPathItem.getPatch(), newPathItem.getPatch());
        }

        return violations;
    }

    private void checkSecurity(List<ViolationDTO> violations, String path, String method, Operation oldOp, Operation newOp) {
        if (oldOp == null || newOp == null) return;

        // 1. Check x-security extension (extracted statically by api-cia)
        Map<String, Object> oldXSec = getXSecurity(oldOp);
        Map<String, Object> newXSec = getXSecurity(newOp);

        Object oldAuthReq = oldXSec != null ? oldXSec.get("authenticationRequired") : null;
        Object newAuthReq = newXSec != null ? newXSec.get("authenticationRequired") : null;

        String oldAuthType = oldXSec != null ? (String) oldXSec.get("authenticationType") : null;
        String newAuthType = newXSec != null ? (String) newXSec.get("authenticationType") : null;

        String oldAuthz = oldXSec != null ? (String) oldXSec.get("authorization") : null;
        String newAuthz = newXSec != null ? (String) newXSec.get("authorization") : null;

        // Flag changes in Authentication Required status
        if (oldAuthReq != null && newAuthReq != null && !oldAuthReq.equals(newAuthReq)) {
            if ("true".equals(oldAuthReq.toString()) && "false".equals(newAuthReq.toString())) {
                // Downgrade: previously authenticated, now public
                violations.add(ViolationDTO.builder()
                        .ruleId(getRuleId())
                        .severity("CRITICAL")
                        .endpoint(method + " " + path)
                        .message("Security downgrade: Endpoint '" + method + " " + path + "' changed from Authenticated to Public (PermitAll).")
                        .oldValue("Authenticated")
                        .newValue("Public")
                        .build());
            } else if ("false".equals(oldAuthReq.toString()) && "true".equals(newAuthReq.toString())) {
                // Upgrade: previously public, now authenticated (breaks clients calling it publicly)
                violations.add(ViolationDTO.builder()
                        .ruleId(getRuleId())
                        .severity("BREAKING")
                        .endpoint(method + " " + path)
                        .message("Security restriction added: Endpoint '" + method + " " + path + "' now requires authentication. Existing public clients will break.")
                        .oldValue("Public")
                        .newValue("Authenticated")
                        .build());
            }
        }

        // Flag changes in Authentication Type
        if (oldAuthType != null && newAuthType != null && !oldAuthType.equals(newAuthType)) {
            violations.add(ViolationDTO.builder()
                    .ruleId(getRuleId())
                    .severity("WARNING")
                    .endpoint(method + " " + path)
                    .message("Authentication type for endpoint '" + method + " " + path + "' changed from " + oldAuthType + " to " + newAuthType + ".")
                    .oldValue(oldAuthType)
                    .newValue(newAuthType)
                    .build());
        }

        // Flag changes in Authorization / Roles
        if (oldAuthz != null || newAuthz != null) {
            String oldAuthzVal = oldAuthz == null ? "" : oldAuthz;
            String newAuthzVal = newAuthz == null ? "" : newAuthz;
            if (!oldAuthzVal.equals(newAuthzVal)) {
                boolean isDowngrade = isRoleDowngrade(oldAuthzVal, newAuthzVal);
                String sev = isDowngrade ? "WARNING" : "BREAKING";
                String msg = isDowngrade
                        ? "Security authorization weakened for '" + method + " " + path + "': '" + oldAuthzVal + "' was changed to '" + newAuthzVal + "'."
                        : "Security authorization tightened for '" + method + " " + path + "': '" + oldAuthzVal + "' was changed to '" + newAuthzVal + "'. Existing client credentials may fail.";
                violations.add(ViolationDTO.builder()
                        .ruleId(getRuleId())
                        .severity(sev)
                        .endpoint(method + " " + path)
                        .message(msg)
                        .oldValue(oldAuthzVal.isEmpty() ? "None" : oldAuthzVal)
                        .newValue(newAuthzVal.isEmpty() ? "None" : newAuthzVal)
                        .build());
            }
        }

        // 2. Check standard OpenAPI security requirement changes
        List<SecurityRequirement> oldSecurity = oldOp.getSecurity();
        List<SecurityRequirement> newSecurity = newOp.getSecurity();

        boolean oldHasSec = oldSecurity != null && !oldSecurity.isEmpty();
        boolean newHasSec = newSecurity != null && !newSecurity.isEmpty();

        if (oldHasSec && !newHasSec && (newAuthReq == null || !"false".equals(newAuthReq.toString()))) {
            violations.add(ViolationDTO.builder()
                    .ruleId(getRuleId())
                    .severity("WARNING")
                    .endpoint(method + " " + path)
                    .message("Security scheme requirement removed from endpoint '" + method + " " + path + "' in OpenAPI specification.")
                    .oldValue("Has Security Requirement")
                    .newValue("No Security Requirement")
                    .build());
        } else if (!oldHasSec && newHasSec && (oldAuthReq == null || !"false".equals(oldAuthReq.toString()))) {
            violations.add(ViolationDTO.builder()
                    .ruleId(getRuleId())
                    .severity("BREAKING")
                    .endpoint(method + " " + path)
                    .message("Security scheme requirement added to endpoint '" + method + " " + path + "' in OpenAPI specification. Existing calls may be rejected.")
                    .oldValue("No Security Requirement")
                    .newValue("Has Security Requirement")
                    .build());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getXSecurity(Operation op) {
        if (op == null || op.getExtensions() == null) {
            return null;
        }
        Object xSec = op.getExtensions().get("x-security");
        if (xSec instanceof Map) {
            return (Map<String, Object>) xSec;
        }
        return null;
    }

    private boolean isRoleDowngrade(String oldAuthz, String newAuthz) {
        if (!oldAuthz.isEmpty() && (newAuthz.isEmpty() || newAuthz.toLowerCase().contains("permitall"))) {
            return true;
        }
        if (oldAuthz.toUpperCase().contains("ADMIN") && !newAuthz.toUpperCase().contains("ADMIN")) {
            if (newAuthz.toUpperCase().contains("USER") || newAuthz.toUpperCase().contains("AUTHENTICATED") || newAuthz.toUpperCase().contains("MEMBER")) {
                return true;
            }
        }
        return false;
    }
}
