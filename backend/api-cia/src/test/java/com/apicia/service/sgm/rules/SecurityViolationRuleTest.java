package com.apicia.service.sgm.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SecurityViolationRuleTest {

    private final SecurityViolationRule rule = new SecurityViolationRule();

    @Test
    public void testAuthenticationDowngrade() {
        OpenAPI oldSpec = createSpecWithAuth("/api/secure", "true", "JWT", "hasRole('ADMIN')");
        OpenAPI newSpec = createSpecWithAuth("/api/secure", "false", "JWT", "");

        List<ViolationDTO> violations = rule.evaluate(oldSpec, newSpec);
        
        assertFalse(violations.isEmpty());
        ViolationDTO violation = violations.get(0);
        assertEquals("SGM-007", violation.getRuleId());
        assertEquals("CRITICAL", violation.getSeverity());
        assertTrue(violation.getMessage().contains("Security downgrade"));
        assertEquals("Authenticated", violation.getOldValue());
        assertEquals("Public", violation.getNewValue());
    }

    @Test
    public void testAuthenticationUpgrade() {
        OpenAPI oldSpec = createSpecWithAuth("/api/secure", "false", "JWT", "");
        OpenAPI newSpec = createSpecWithAuth("/api/secure", "true", "JWT", "hasRole('ADMIN')");

        List<ViolationDTO> violations = rule.evaluate(oldSpec, newSpec);
        
        assertFalse(violations.isEmpty());
        ViolationDTO violation = violations.get(0);
        assertEquals("SGM-007", violation.getRuleId());
        assertEquals("BREAKING", violation.getSeverity());
        assertTrue(violation.getMessage().contains("Security restriction added"));
        assertEquals("Public", violation.getOldValue());
        assertEquals("Authenticated", violation.getNewValue());
    }

    @Test
    public void testAuthorizationRoleDowngrade() {
        OpenAPI oldSpec = createSpecWithAuth("/api/secure", "true", "JWT", "hasRole('ADMIN')");
        OpenAPI newSpec = createSpecWithAuth("/api/secure", "true", "JWT", "hasRole('USER')");

        List<ViolationDTO> violations = rule.evaluate(oldSpec, newSpec);
        
        assertFalse(violations.isEmpty());
        ViolationDTO violation = violations.stream()
                .filter(v -> v.getMessage().contains("Security authorization weakened"))
                .findFirst()
                .orElse(null);
        
        assertViolationFields(violation, "SGM-007", "WARNING", "hasRole('ADMIN')", "hasRole('USER')");
    }

    @Test
    public void testAuthorizationRoleUpgrade() {
        OpenAPI oldSpec = createSpecWithAuth("/api/secure", "true", "JWT", "hasRole('USER')");
        OpenAPI newSpec = createSpecWithAuth("/api/secure", "true", "JWT", "hasRole('ADMIN')");

        List<ViolationDTO> violations = rule.evaluate(oldSpec, newSpec);
        
        assertFalse(violations.isEmpty());
        ViolationDTO violation = violations.stream()
                .filter(v -> v.getMessage().contains("Security authorization tightened"))
                .findFirst()
                .orElse(null);
        
        assertViolationFields(violation, "SGM-007", "BREAKING", "hasRole('USER')", "hasRole('ADMIN')");
    }

    @Test
    public void testStandardSecurityRequirementAdded() {
        // Spec 1 without standard security
        OpenAPI oldSpec = new OpenAPI();
        PathItem oldPath = new PathItem();
        Operation oldOp = new Operation();
        oldPath.setGet(oldOp);
        oldSpec.path("/api/data", oldPath);

        // Spec 2 with standard security requirements
        OpenAPI newSpec = new OpenAPI();
        PathItem newPath = new PathItem();
        Operation newOp = new Operation();
        SecurityRequirement secReq = new SecurityRequirement();
        secReq.addList("bearerAuth");
        newOp.setSecurity(List.of(secReq));
        newPath.setGet(newOp);
        newSpec.path("/api/data", newPath);

        List<ViolationDTO> violations = rule.evaluate(oldSpec, newSpec);

        assertFalse(violations.isEmpty());
        ViolationDTO violation = violations.get(0);
        assertEquals("SGM-007", violation.getRuleId());
        assertEquals("BREAKING", violation.getSeverity());
        assertTrue(violation.getMessage().contains("Security scheme requirement added"));
    }

    private OpenAPI createSpecWithAuth(String path, String authReq, String authType, String authz) {
        OpenAPI spec = new OpenAPI();
        PathItem pathItem = new PathItem();
        Operation op = new Operation();
        
        Map<String, Object> xSecurity = new LinkedHashMap<>();
        xSecurity.put("authenticationRequired", authReq);
        xSecurity.put("authenticationType", authType);
        xSecurity.put("authorization", authz);
        
        op.addExtension("x-security", xSecurity);
        
        if ("true".equals(authReq)) {
            SecurityRequirement req = new SecurityRequirement();
            req.addList("bearerAuth");
            op.setSecurity(List.of(req));
        }
        
        pathItem.setGet(op);
        spec.path(path, pathItem);
        return spec;
    }

    private void assertViolationFields(ViolationDTO violation, String ruleId, String severity, String oldVal, String newVal) {
        assertTrue(violation != null, "Violation should not be null");
        assertEquals(ruleId, violation.getRuleId());
        assertEquals(severity, violation.getSeverity());
        assertEquals(oldVal, violation.getOldValue());
        assertEquals(newVal, violation.getNewValue());
    }
}
