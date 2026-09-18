package com.apicia.service.sgm.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Test;

import java.util.List;

public class VerbInPathRuleTest {

    private final VerbInPathRule rule = new VerbInPathRule();

    @Test
    public void testVerbInPathFlagged() {
        OpenAPI spec = new OpenAPI();
        spec.path("/api/user/createUser", new PathItem());
        spec.path("/api/clinic/updateClinic", new PathItem());
        spec.path("/api/orders/deleteOrder", new PathItem());

        List<ViolationDTO> violations = rule.evaluate(null, spec);

        assertEquals(3, violations.size());
        assertTrue(violations.stream().allMatch(v -> "SGM-011".equals(v.getRuleId())));
        assertTrue(violations.stream().allMatch(v -> "WARNING".equals(v.getSeverity())));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("create")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("update")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("delete")));
    }

    @Test
    public void testCleanRestPathPasses() {
        OpenAPI spec = new OpenAPI();
        spec.path("/api/users", new PathItem());
        spec.path("/api/clinics/{id}", new PathItem());
        spec.path("/api/orders/{orderId}/items", new PathItem());

        List<ViolationDTO> violations = rule.evaluate(null, spec);
        assertTrue(violations.isEmpty(), "Clean REST paths should produce no verb warnings");
    }
}
