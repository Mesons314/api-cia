package com.apicia.service.sgm.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Test;

import java.util.List;

public class RestPathStylingRuleTest {

    private final RestPathStylingRule rule = new RestPathStylingRule();

    @Test
    public void testSingularNounFlagged() {
        OpenAPI spec = new OpenAPI();
        spec.path("/api/user", new PathItem());
        spec.path("/api/clinic", new PathItem());

        List<ViolationDTO> violations = rule.evaluate(null, spec);

        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("users")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("clinics")));
        assertEquals("SGM-010", violations.get(0).getRuleId());
        assertEquals("WARNING", violations.get(0).getSeverity());
    }

    @Test
    public void testPluralNounPasses() {
        OpenAPI spec = new OpenAPI();
        spec.path("/api/v1/users", new PathItem());
        spec.path("/api/v1/clinics", new PathItem());
        spec.path("/api/v1/users/{id}", new PathItem());
        spec.path("/api/health", new PathItem());

        List<ViolationDTO> violations = rule.evaluate(null, spec);
        assertTrue(violations.isEmpty(), "Plural paths with path variables and health check should not produce violations");
    }

    @Test
    public void testUppercasePathFlagged() {
        OpenAPI spec = new OpenAPI();
        spec.path("/api/UserProfiles", new PathItem());

        List<ViolationDTO> violations = rule.evaluate(null, spec);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("contains uppercase characters")));
    }
}
