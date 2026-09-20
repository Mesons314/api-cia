package com.apicia.service.sgm.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ResponseTypeChangeRuleTest {

    private final ResponseTypeChangeRule rule = new ResponseTypeChangeRule();

    @Test
    public void testResponseTypeChangeFromObjectToListDetected() {
        OpenAPI oldSpec = new OpenAPI();
        PathItem oldPathItem = new PathItem();
        Operation oldGet = new Operation();
        ApiResponses oldResponses = new ApiResponses();
        ApiResponse old200 = new ApiResponse();
        Content oldContent = new Content();
        MediaType oldMedia = new MediaType();
        Schema<?> oldSchema = new Schema<>();
        oldSchema.set$ref("#/components/schemas/UserResponse");
        oldMedia.setSchema(oldSchema);
        oldContent.addMediaType("application/json", oldMedia);
        old200.setContent(oldContent);
        oldResponses.addApiResponse("200", old200);
        oldGet.setResponses(oldResponses);
        oldPathItem.setGet(oldGet);
        oldSpec.path("/api/users/{id}", oldPathItem);

        OpenAPI newSpec = new OpenAPI();
        PathItem newPathItem = new PathItem();
        Operation newGet = new Operation();
        ApiResponses newResponses = new ApiResponses();
        ApiResponse new200 = new ApiResponse();
        Content newContent = new Content();
        MediaType newMedia = new MediaType();
        ArraySchema arraySchema = new ArraySchema();
        Schema<?> itemSchema = new Schema<>();
        itemSchema.set$ref("#/components/schemas/UserResponse");
        arraySchema.setItems(itemSchema);
        newMedia.setSchema(arraySchema);
        newContent.addMediaType("application/json", newMedia);
        new200.setContent(newContent);
        newResponses.addApiResponse("200", new200);
        newGet.setResponses(newResponses);
        newPathItem.setGet(newGet);
        newSpec.path("/api/users/{id}", newPathItem);

        List<ViolationDTO> violations = rule.evaluate(oldSpec, newSpec);

        assertFalse(violations.isEmpty(), "Schema mutation from UserResponse to List<UserResponse> must be detected");
        ViolationDTO v = violations.get(0);
        assertEquals("SGM-013", v.getRuleId());
        assertEquals("BREAKING", v.getSeverity());
        assertEquals("UserResponse", v.getOldValue());
        assertEquals("List<UserResponse>", v.getNewValue());
    }

    @Test
    public void testResponseSchemaRemovedDetected() {
        OpenAPI oldSpec = new OpenAPI();
        PathItem oldPathItem = new PathItem();
        Operation oldGet = new Operation();
        ApiResponses oldResponses = new ApiResponses();
        ApiResponse old200 = new ApiResponse();
        Content oldContent = new Content();
        MediaType oldMedia = new MediaType();
        Schema<?> oldSchema = new Schema<>();
        oldSchema.setType("string");
        oldMedia.setSchema(oldSchema);
        oldContent.addMediaType("application/json", oldMedia);
        old200.setContent(oldContent);
        oldResponses.addApiResponse("200", old200);
        oldGet.setResponses(oldResponses);
        oldPathItem.setGet(oldGet);
        oldSpec.path("/api/status", oldPathItem);

        OpenAPI newSpec = new OpenAPI();
        PathItem newPathItem = new PathItem();
        Operation newGet = new Operation();
        ApiResponses newResponses = new ApiResponses();
        ApiResponse new200 = new ApiResponse(); // Empty response
        newResponses.addApiResponse("200", new200);
        newGet.setResponses(newResponses);
        newPathItem.setGet(newGet);
        newSpec.path("/api/status", newPathItem);

        List<ViolationDTO> violations = rule.evaluate(oldSpec, newSpec);

        assertFalse(violations.isEmpty(), "Removal of response schema must be detected");
        ViolationDTO v = violations.get(0);
        assertEquals("SGM-013", v.getRuleId());
        assertEquals("BREAKING", v.getSeverity());
        assertEquals("string", v.getOldValue());
        assertEquals("NONE", v.getNewValue());
    }

    @Test
    public void testUnchangedResponseSchemaPasses() {
        OpenAPI oldSpec = new OpenAPI();
        PathItem oldPathItem = new PathItem();
        Operation oldGet = new Operation();
        ApiResponses oldResponses = new ApiResponses();
        ApiResponse old200 = new ApiResponse();
        Content oldContent = new Content();
        MediaType oldMedia = new MediaType();
        Schema<?> oldSchema = new Schema<>();
        oldSchema.set$ref("#/components/schemas/UserResponse");
        oldMedia.setSchema(oldSchema);
        oldContent.addMediaType("application/json", oldMedia);
        old200.setContent(oldContent);
        oldResponses.addApiResponse("200", old200);
        oldGet.setResponses(oldResponses);
        oldPathItem.setGet(oldGet);
        oldSpec.path("/api/users", oldPathItem);

        OpenAPI newSpec = new OpenAPI();
        PathItem newPathItem = new PathItem();
        Operation newGet = new Operation();
        ApiResponses newResponses = new ApiResponses();
        ApiResponse new200 = new ApiResponse();
        Content newContent = new Content();
        MediaType newMedia = new MediaType();
        Schema<?> newSchema = new Schema<>();
        newSchema.set$ref("#/components/schemas/UserResponse");
        newMedia.setSchema(newSchema);
        newContent.addMediaType("application/json", newMedia);
        new200.setContent(newContent);
        newResponses.addApiResponse("200", new200);
        newGet.setResponses(newResponses);
        newPathItem.setGet(newGet);
        newSpec.path("/api/users", newPathItem);

        List<ViolationDTO> violations = rule.evaluate(oldSpec, newSpec);
        assertTrue(violations.isEmpty(), "Identical response schemas should yield no violations");
    }
}
