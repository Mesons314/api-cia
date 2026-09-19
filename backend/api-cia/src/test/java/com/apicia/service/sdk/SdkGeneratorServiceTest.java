package com.apicia.service.sdk;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class SdkGeneratorServiceTest {

    private SdkGeneratorService sdkGeneratorService;

    private static final String SAMPLE_OPENAPI = """
    {
      "openapi": "3.0.0",
      "info": {
        "title": "HealQueue API",
        "version": "1.0.0",
        "description": "Clinic Queue Management System"
      },
      "paths": {
        "/api/clinic/appointments": {
          "get": {
            "summary": "Get all appointments",
            "tags": ["ClinicController"],
            "responses": {
              "200": { "description": "OK" }
            }
          },
          "post": {
            "summary": "Create new appointment",
            "tags": ["ClinicController"],
            "requestBody": {
              "content": {
                "application/json": {
                  "schema": {
                    "$ref": "#/components/schemas/AppointmentRequest"
                  }
                }
              }
            },
            "responses": {
              "201": { "description": "Created" }
            }
          }
        }
      },
      "components": {
        "schemas": {
          "AppointmentRequest": {
            "type": "object",
            "properties": {
              "patientName": { "type": "string" },
              "patientEmail": { "type": "string" },
              "clinicId": { "type": "integer" }
            }
          }
        }
      }
    }
    """;

    @BeforeEach
    public void setUp() {
        sdkGeneratorService = new SdkGeneratorService();
    }

    @Test
    public void testExportPostmanCollection() {
        Map<String, Object> postman = sdkGeneratorService.exportPostmanCollection(SAMPLE_OPENAPI);

        assertNotNull(postman);
        assertTrue(postman.containsKey("info"));
        assertTrue(postman.containsKey("item"));

        Map<?, ?> info = (Map<?, ?>) postman.get("info");
        assertEquals("HealQueue API", info.get("name"));

        List<?> items = (List<?>) postman.get("item");
        assertFalse(items.isEmpty());
    }

    @Test
    public void testGenerateSdkZip() throws Exception {
        byte[] zipBytes = sdkGeneratorService.generateSdk(SAMPLE_OPENAPI, "typescript-axios");

        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);
        // ZIP magic bytes: PK (0x50, 0x4B)
        assertEquals((byte) 0x50, zipBytes[0]);
        assertEquals((byte) 0x4B, zipBytes[1]);
    }

    @Test
    public void testInvalidSpecThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            sdkGeneratorService.generateSdk("", "typescript-axios");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            sdkGeneratorService.exportPostmanCollection(null);
        });
    }
}
