package com.apicia.controller;

import com.apicia.model.dto.PublicEndpointDTO;
import com.apicia.model.dto.SAMResultDTO;
import com.apicia.model.dto.SecurityAlertDTO;
import com.apicia.service.security.SecurityComplianceScannerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/security")
@CrossOrigin(origins = "*")
public class SecurityAuditController {

    private final SecurityComplianceScannerService securityScannerService;

    public SecurityAuditController(SecurityComplianceScannerService securityScannerService) {
        this.securityScannerService = securityScannerService;
    }

    @GetMapping("/audit")
    public SAMResultDTO fullAudit(
            @RequestParam(name = "sourceRoot", required = false) String sourceRoot
    ) {
        return securityScannerService.auditSourceCode(sourceRoot);
    }

    @GetMapping("/no-auth-endpoints")
    public List<PublicEndpointDTO> getNoAuthEndpoints(
            @RequestParam(name = "sourceRoot", required = false) String sourceRoot
    ) {
        return securityScannerService.auditSourceCode(sourceRoot).getPublicEndpoints();
    }

    @GetMapping("/cors-violations")
    public List<SecurityAlertDTO> getCorsViolations(
            @RequestParam(name = "sourceRoot", required = false) String sourceRoot
    ) {
        return securityScannerService.auditSourceCode(sourceRoot).getCorsViolations();
    }

    @GetMapping("/sensitive-data")
    public List<SecurityAlertDTO> getSensitiveDataAlerts(
            @RequestParam(name = "sourceRoot", required = false) String sourceRoot
    ) {
        return securityScannerService.auditSourceCode(sourceRoot).getSensitiveDataAlerts();
    }

    @PostMapping("/audit-spec")
    public ResponseEntity<SAMResultDTO> auditSpec(
            @RequestBody String rawOpenApiJson
    ) {
        if (rawOpenApiJson == null || rawOpenApiJson.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(securityScannerService.auditOpenApiJson(rawOpenApiJson));
    }
}
