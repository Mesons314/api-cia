package com.apicia.service.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.apicia.config.EndpointExtractionProperties;
import com.apicia.model.dto.PublicEndpointDTO;
import com.apicia.model.dto.SAMResultDTO;
import com.apicia.model.dto.SecurityAlertDTO;
import com.apicia.model.extraction.ExtractedEndpoint;
import com.apicia.service.extraction.SecurityExtractionService;
import com.apicia.service.extraction.SourceUnit;
import com.apicia.service.extraction.StaticEndpointExtractionService;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

public class SecurityComplianceScannerServiceTest {

    private StaticEndpointExtractionService extractionService;
    private SecurityExtractionService securityExtractionService;
    private EndpointExtractionProperties properties;
    private SecurityComplianceScannerService securityScanner;

    @BeforeEach
    public void setUp() {
        extractionService = mock(StaticEndpointExtractionService.class);
        securityExtractionService = mock(SecurityExtractionService.class);
        properties = new EndpointExtractionProperties();
        securityScanner = new SecurityComplianceScannerService(extractionService, securityExtractionService, properties);
    }

    @Test
    public void testAuditCors_WildcardDetected(@TempDir Path tempDir) {
        String controllerCode = """
            package com.example.controller;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/orders")
            @CrossOrigin(origins = "*")
            public class OrderController {

                @GetMapping
                public String getOrders() { return "orders"; }

                @PostMapping
                @CrossOrigin(origins = "*")
                public String createOrder() { return "created"; }
            }
        """;

        CompilationUnit cu = StaticJavaParser.parse(controllerCode);
        Path dummyPath = tempDir.resolve("OrderController.java");
        SourceUnit unit = new SourceUnit(dummyPath, cu);

        ExtractedEndpoint ep1 = new ExtractedEndpoint();
        ep1.setPath("/api/orders");
        ep1.setHttpMethod("GET");
        ep1.setControllerClass("com.example.controller.OrderController");
        ep1.setControllerMethod("getOrders");
        ep1.setSourceFile("OrderController.java");

        when(extractionService.extractEndpoints(any())).thenReturn(List.of(ep1));
        when(extractionService.parseSourceUnits(any())).thenReturn(List.of(unit));
        when(extractionService.getSourceRoot(any())).thenReturn(tempDir);

        SAMResultDTO result = securityScanner.auditSourceCode(null);

        assertNotNull(result);
        assertTrue(result.getTotalAlerts() >= 2);
        assertTrue(result.getCorsViolations().size() >= 2);

        SecurityAlertDTO classCorsAlert = result.getCorsViolations().stream()
                .filter(a -> "CLASS_LEVEL".equals(a.getMethod()))
                .findFirst().orElse(null);
        assertNotNull(classCorsAlert);
        assertEquals("SEC-CORS-001", classCorsAlert.getCheckId());
        assertEquals("CORS_OVER_PERMISSION", classCorsAlert.getCategory());
        assertEquals("HIGH", classCorsAlert.getSeverity());

        SecurityAlertDTO methodCorsAlert = result.getCorsViolations().stream()
                .filter(a -> "createOrder".equals(a.getMethod()))
                .findFirst().orElse(null);
        assertNotNull(methodCorsAlert);
        assertEquals("CRITICAL", methodCorsAlert.getSeverity()); // Modifying POST method gets CRITICAL
    }

    @Test
    public void testAuditSensitiveData_KeywordsDetected(@TempDir Path tempDir) {
        String dtoCode = """
            package com.example.dto;
            import com.fasterxml.jackson.annotation.JsonIgnore;

            public class UserResponseDTO {
                private Long id;
                private String username;
                private String passwordHash;
                private String creditCardNumber;

                @JsonIgnore
                private String ssn;
            }
        """;

        String controllerCode = """
            package com.example.controller;
            import org.springframework.web.bind.annotation.*;
            import com.example.dto.UserResponseDTO;

            @RestController
            public class UserController {
                @GetMapping("/api/user")
                public UserResponseDTO getUser(@RequestParam("token") String token) {
                    return new UserResponseDTO();
                }
            }
        """;

        CompilationUnit dtoCu = StaticJavaParser.parse(dtoCode);
        CompilationUnit ctrlCu = StaticJavaParser.parse(controllerCode);

        SourceUnit dtoUnit = new SourceUnit(tempDir.resolve("UserResponseDTO.java"), dtoCu);
        SourceUnit ctrlUnit = new SourceUnit(tempDir.resolve("UserController.java"), ctrlCu);

        ExtractedEndpoint ep = new ExtractedEndpoint();
        ep.setPath("/api/user");
        ep.setHttpMethod("GET");
        ep.setControllerClass("com.example.controller.UserController");
        ep.setControllerMethod("getUser");
        ep.setReturnType("UserResponseDTO");
        ep.setSourceFile("UserController.java");

        when(extractionService.extractEndpoints(any())).thenReturn(List.of(ep));
        when(extractionService.parseSourceUnits(any())).thenReturn(List.of(dtoUnit, ctrlUnit));
        when(extractionService.getSourceRoot(any())).thenReturn(tempDir);

        SAMResultDTO result = securityScanner.auditSourceCode(null);

        assertNotNull(result);
        List<SecurityAlertDTO> leaks = result.getSensitiveDataAlerts();
        assertFalse(leaks.isEmpty());

        // passwordHash and creditCardNumber should be flagged
        boolean hasPasswordLeak = leaks.stream().anyMatch(a -> a.getDescription().contains("passwordHash"));
        boolean hasCardLeak = leaks.stream().anyMatch(a -> a.getDescription().contains("creditCardNumber"));
        boolean hasSsnLeak = leaks.stream().anyMatch(a -> a.getDescription().contains("ssn")); // @JsonIgnore should prevent this
        boolean hasUrlParamLeak = leaks.stream().anyMatch(a -> "SENSITIVE_DATA_IN_URL".equals(a.getCategory()));

        assertTrue(hasPasswordLeak, "Should flag passwordHash");
        assertTrue(hasCardLeak, "Should flag creditCardNumber");
        assertFalse(hasSsnLeak, "Should NOT flag ssn because it is marked with @JsonIgnore");
        assertTrue(hasUrlParamLeak, "Should flag sensitive GET query param token in URL");
    }

    @Test
    public void testAuditPublicEndpoints_Reported(@TempDir Path tempDir) {
        ExtractedEndpoint publicGet = new ExtractedEndpoint();
        publicGet.setPath("/api/public/products");
        publicGet.setHttpMethod("GET");
        publicGet.setControllerClass("com.example.ProductController");
        publicGet.setControllerMethod("getProducts");
        publicGet.setAuthenticationRequired("false");
        publicGet.setAuthorization("permitAll()");
        publicGet.setSourceFile("ProductController.java");
        publicGet.setLineNumber(20);

        ExtractedEndpoint publicDelete = new ExtractedEndpoint();
        publicDelete.setPath("/api/public/products/{id}");
        publicDelete.setHttpMethod("DELETE");
        publicDelete.setControllerClass("com.example.ProductController");
        publicDelete.setControllerMethod("deleteProduct");
        publicDelete.setAuthenticationRequired("false");
        publicDelete.setAuthorization("permitAll()");
        publicDelete.setSourceFile("ProductController.java");
        publicDelete.setLineNumber(40);

        when(extractionService.extractEndpoints(any())).thenReturn(List.of(publicGet, publicDelete));
        when(extractionService.parseSourceUnits(any())).thenReturn(List.of());
        when(extractionService.getSourceRoot(any())).thenReturn(tempDir);

        SAMResultDTO result = securityScanner.auditSourceCode(null);

        assertNotNull(result);
        List<PublicEndpointDTO> publicEndpoints = result.getPublicEndpoints();
        assertEquals(2, publicEndpoints.size());

        // Check that state-modifying DELETE without authentication generates a SecurityAlert
        boolean hasUnauthModAlert = result.getAlerts().stream()
                .anyMatch(a -> "SEC-NOAUTH-001".equals(a.getCheckId()) && a.getEndpoint().contains("DELETE"));
        assertTrue(hasUnauthModAlert);
    }

    @Test
    public void testAuditOpenApiSpec() {
        String openApiJson = """
        {
          "openapi": "3.0.0",
          "info": { "title": "Test API", "version": "1.0.0" },
          "paths": {
            "/api/payments": {
              "post": {
                "summary": "Process payment",
                "responses": {
                  "200": {
                    "description": "OK",
                    "content": {
                      "application/json": {
                        "schema": {
                          "$ref": "#/components/schemas/PaymentResponse"
                        }
                      }
                    }
                  }
                }
              }
            }
          },
          "components": {
            "schemas": {
              "PaymentResponse": {
                "type": "object",
                "properties": {
                  "amount": { "type": "number" },
                  "creditCardNumber": { "type": "string" },
                  "cvv": { "type": "string" }
                }
              }
            }
          }
        }
        """;

        OpenAPI openAPI = new OpenAPIParser().readContents(openApiJson, null, null).getOpenAPI();
        SAMResultDTO result = securityScanner.auditOpenApi(openAPI);

        assertNotNull(result);
        assertTrue(result.getCriticalCount() >= 2); // creditCardNumber and cvv are CRITICAL
        assertEquals(1, result.getPublicEndpoints().size());
        assertEquals("/api/payments", result.getPublicEndpoints().get(0).getPath());
    }

    @Test
    public void testDetectCircularDtoSerializationCycle(@TempDir Path tempDir) {
        String parentDto = """
            package com.example.dto;
            import java.util.List;
            public class ClinicDTO {
                private Long id;
                private List<DoctorDTO> doctors;
            }
        """;

        String childDto = """
            package com.example.dto;
            public class DoctorDTO {
                private Long id;
                private ClinicDTO clinic; // Circular cycle ClinicDTO <-> DoctorDTO
            }
        """;

        CompilationUnit cu1 = StaticJavaParser.parse(parentDto);
        CompilationUnit cu2 = StaticJavaParser.parse(childDto);
        SourceUnit u1 = new SourceUnit(tempDir.resolve("ClinicDTO.java"), cu1);
        SourceUnit u2 = new SourceUnit(tempDir.resolve("DoctorDTO.java"), cu2);

        when(extractionService.extractEndpoints(any())).thenReturn(List.of());
        when(extractionService.parseSourceUnits(any())).thenReturn(List.of(u1, u2));
        when(extractionService.getSourceRoot(any())).thenReturn(tempDir);

        SAMResultDTO result = securityScanner.auditSourceCode(null);

        assertNotNull(result);
        boolean hasCycleAlert = result.getAlerts().stream()
                .anyMatch(a -> "PERF-CYCLE-001".equals(a.getCheckId()));
        assertTrue(hasCycleAlert, "Should detect circular DTO serialization cycle between ClinicDTO and DoctorDTO");
    }

    @Test
    public void testDetectNPlusOneDatabaseQueriesInLoop(@TempDir Path tempDir) {
        String serviceCode = """
            package com.example.service;
            import org.springframework.stereotype.Service;
            import java.util.List;

            @Service
            public class AppointmentService {
                private UserRepository userRepository;

                public void notifyUsers(List<Long> userIds) {
                    for (Long id : userIds) {
                        userRepository.findById(id); // N+1 Query in loop!
                    }
                }
            }
        """;

        CompilationUnit cu = StaticJavaParser.parse(serviceCode);
        SourceUnit unit = new SourceUnit(tempDir.resolve("AppointmentService.java"), cu);

        when(extractionService.extractEndpoints(any())).thenReturn(List.of());
        when(extractionService.parseSourceUnits(any())).thenReturn(List.of(unit));
        when(extractionService.getSourceRoot(any())).thenReturn(tempDir);

        SAMResultDTO result = securityScanner.auditSourceCode(null);

        assertNotNull(result);
        boolean hasNPlusOneAlert = result.getAlerts().stream()
                .anyMatch(a -> "PERF-NPLUS1-001".equals(a.getCheckId()));
        assertTrue(hasNPlusOneAlert, "Should detect N+1 repository call inside loop");
    }

    @Test
    public void testDetectOpenApiCircularSchema() {
        String openApiJson = """
        {
          "openapi": "3.0.0",
          "info": { "title": "Cycle API", "version": "1.0.0" },
          "paths": {},
          "components": {
            "schemas": {
              "Category": {
                "type": "object",
                "properties": {
                  "subCategories": {
                    "type": "array",
                    "items": { "$ref": "#/components/schemas/Category" }
                  }
                }
              }
            }
          }
        }
        """;

        OpenAPI openAPI = new OpenAPIParser().readContents(openApiJson, null, null).getOpenAPI();
        SAMResultDTO result = securityScanner.auditOpenApi(openAPI);

        assertNotNull(result);
        boolean hasCycleAlert = result.getAlerts().stream()
                .anyMatch(a -> "PERF-CYCLE-001".equals(a.getCheckId()));
        assertTrue(hasCycleAlert, "Should detect circular schema reference in Category");
    }
}
