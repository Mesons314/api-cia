package com.apicia.service.sgm.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ConsistentErrorResponseRuleTest {

    private final ConsistentErrorResponseRule rule = new ConsistentErrorResponseRule();

    @Test
    public void testMissingErrorResponsesFlagged() {
        OpenAPI spec = new OpenAPI();
        PathItem pathItem = new PathItem();
        Operation getOp = new Operation();
        ApiResponses responses = new ApiResponses();
        
        ApiResponse okResp = new ApiResponse();
        okResp.setDescription("OK");
        responses.addApiResponse("200", okResp);
        
        getOp.setResponses(responses);
        pathItem.setGet(getOp);
        spec.path("/api/users", pathItem);

        List<ViolationDTO> violations = rule.evaluate(null, spec);

        assertFalse(violations.isEmpty());
        ViolationDTO v = violations.get(0);
        assertEquals("SGM-012", v.getRuleId());
        assertEquals("WARNING", v.getSeverity());
        assertTrue(v.getMessage().contains("does not document any error payloads"));
    }

    @Test
    public void testRawStringErrorResponseFlagged() {
        OpenAPI spec = new OpenAPI();
        PathItem pathItem = new PathItem();
        Operation postOp = new Operation();
        ApiResponses responses = new ApiResponses();

        ApiResponse badReq = new ApiResponse();
        badReq.setDescription("Bad Request");
        Content content = new Content();
        MediaType mediaType = new MediaType();
        Schema<?> stringSchema = new Schema<>();
        stringSchema.setType("string");
        mediaType.setSchema(stringSchema);
        content.addMediaType("text/plain", mediaType);
        badReq.setContent(content);

        responses.addApiResponse("400", badReq);
        postOp.setResponses(responses);
        pathItem.setPost(postOp);
        spec.path("/api/users", pathItem);

        List<ViolationDTO> violations = rule.evaluate(null, spec);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("returns raw text/string")));
    }

    @Test
    public void testStandardizedErrorResponsePasses() {
        OpenAPI spec = new OpenAPI();
        PathItem pathItem = new PathItem();
        Operation getOp = new Operation();
        ApiResponses responses = new ApiResponses();

        ApiResponse badReq = new ApiResponse();
        badReq.setDescription("Bad Request");
        Content content = new Content();
        MediaType mediaType = new MediaType();
        Schema<?> errorSchema = new Schema<>();
        errorSchema.set$ref("#/components/schemas/ErrorResponse");
        mediaType.setSchema(errorSchema);
        content.addMediaType("application/json", mediaType);
        badReq.setContent(content);

        responses.addApiResponse("200", new ApiResponse());
        responses.addApiResponse("400", badReq);
        getOp.setResponses(responses);
        pathItem.setGet(getOp);
        spec.path("/api/users", pathItem);

        List<ViolationDTO> violations = rule.evaluate(null, spec);
        assertTrue(violations.isEmpty(), "Documented structured ErrorResponse should produce no violations");
    }

    @Test
    public void testGlobalExceptionHandlerSuppressesWarnings() {
        OpenAPI spec = new OpenAPI();
        PathItem pathItem = new PathItem();
        Operation getOp = new Operation();
        ApiResponses responses = new ApiResponses();
        responses.addApiResponse("200", new ApiResponse());
        getOp.setResponses(responses);
        pathItem.setGet(getOp);
        spec.path("/api/users", pathItem);

        // Add x-global-error-handling extension
        spec.addExtension("x-global-error-handling", java.util.Map.of("present", true, "handlerClass", "com.example.GlobalExceptionHandler"));

        List<ViolationDTO> violations = rule.evaluate(null, spec);

        assertTrue(violations.isEmpty(), "Active GlobalExceptionHandler should suppress endpoint-level missing error warnings");
    }
}
