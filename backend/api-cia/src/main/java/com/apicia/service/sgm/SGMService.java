package com.apicia.service.sgm;

import com.apicia.model.dto.SGMResultDTO;
import com.apicia.model.dto.ViolationDTO;
import com.apicia.service.sgm.rules.DesignRule;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SGMService {

    private final List<DesignRule> rules;

    public SGMService(List<DesignRule> rules) {
        this.rules = rules;
    }

    public SGMResultDTO analyze(OpenAPI oldSpec, OpenAPI newSpec) {
        List<ViolationDTO> violations = new ArrayList<>();

        if (rules != null) {
            for (DesignRule rule : rules) {
                try {
                    List<ViolationDTO> ruleViolations = rule.evaluate(oldSpec, newSpec);
                    if (ruleViolations != null) {
                        for (ViolationDTO violation : ruleViolations) {
                            enrichViolation(violation, oldSpec, newSpec, rule.getRuleId());
                        }
                        violations.addAll(ruleViolations);
                    }
                } catch (Exception e) {
                    // Ignore exceptions during rule evaluation to keep other rules running
                }
            }
        }

        int breakingCount = 0;
        int warningCount = 0;
        int infoCount = 0;

        for (ViolationDTO violation : violations) {
            if (violation != null && violation.getSeverity() != null) {
                switch (violation.getSeverity()) {
                    case "BREAKING":
                    case "CRITICAL":
                        breakingCount++;
                        break;
                    case "WARNING":
                        warningCount++;
                        break;
                    case "INFO":
                        infoCount++;
                        break;
                }
            }
        }

        int totalEndpoints = countEndpoints(newSpec);
        double dStruct = 0.0;
        if (totalEndpoints > 0) {
            double raw = (breakingCount * 1.0 + warningCount * 0.5 + infoCount * 0.1) / totalEndpoints;
            dStruct = Math.min(1.0, raw);
        }

        return SGMResultDTO.builder()
                .totalViolations(violations.size())
                .breakingCount(breakingCount)
                .warningCount(warningCount)
                .infoCount(infoCount)
                .dStruct(dStruct)
                .violations(violations)
                .build();
    }

    private void enrichViolation(ViolationDTO dto, OpenAPI oldSpec, OpenAPI newSpec, String ruleId) {
        if (dto.getChangeType() == null) {
            switch (ruleId) {
                case "SGM-001":
                    dto.setChangeType("URI_VERSIONING_VIOLATION");
                    break;
                case "SGM-002":
                    dto.setChangeType("NAMING_CONVENTION_VIOLATION");
                    break;
                case "SGM-003":
                    dto.setChangeType("ENDPOINT_REMOVED");
                    break;
                case "SGM-004":
                    dto.setChangeType("PARAMETER_TYPE_MUTATED");
                    break;
                case "SGM-005":
                    dto.setChangeType("REQUIRED_FIELD_MODIFIED");
                    break;
                case "SGM-006":
                    dto.setChangeType("HTTP_METHOD_CHANGED");
                    break;
                case "SGM-007":
                    dto.setChangeType("SECURITY_VIOLATION");
                    break;
                case "SGM-009":
                    dto.setChangeType("ENDPOINT_RENAMED");
                    break;
                default:
                    dto.setChangeType("API_VIOLATION");
            }
        }

        if (dto.getOldPath() == null) {
            dto.setOldPath(dto.getOldValue() != null && dto.getOldValue().startsWith("/") ? dto.getOldValue() : dto.getEndpoint());
        }
        if (dto.getNewPath() == null) {
            dto.setNewPath(dto.getNewValue() != null && dto.getNewValue().startsWith("/") ? dto.getNewValue() : dto.getEndpoint());
        }

        if (dto.getController() == null || dto.getMethod() == null) {
            Operation op = findOperationForPath(newSpec, dto.getEndpoint());
            if (op == null) {
                op = findOperationForPath(oldSpec, dto.getEndpoint());
            }
            if (op == null) {
                op = findOperationForPath(newSpec, dto.getNewPath());
            }
            if (op == null) {
                op = findOperationForPath(oldSpec, dto.getOldPath());
            }

            if (op != null && op.getExtensions() != null) {
                String cClass = (String) op.getExtensions().get("x-controller-class");
                String cMethod = (String) op.getExtensions().get("x-controller-method");
                if (cClass != null && dto.getController() == null) {
                    dto.setController(getSimpleName(cClass));
                }
                if (cMethod != null && dto.getMethod() == null) {
                    dto.setMethod(cMethod);
                }
            }
        }

        if (dto.getController() == null) {
            dto.setController("UnknownController");
        }
        if (dto.getMethod() == null) {
            dto.setMethod("unknownMethod");
        }
    }

    private Operation findOperationForPath(OpenAPI spec, String path) {
        if (spec == null || spec.getPaths() == null || path == null) {
            return null;
        }
        PathItem pathItem = spec.getPaths().get(path);
        if (pathItem != null) {
            if (pathItem.getGet() != null) return pathItem.getGet();
            if (pathItem.getPost() != null) return pathItem.getPost();
            if (pathItem.getPut() != null) return pathItem.getPut();
            if (pathItem.getDelete() != null) return pathItem.getDelete();
            if (pathItem.getPatch() != null) return pathItem.getPatch();
        }
        return null;
    }

    private String getSimpleName(String qualifiedName) {
        if (qualifiedName == null) return null;
        int idx = qualifiedName.lastIndexOf('.');
        return idx >= 0 ? qualifiedName.substring(idx + 1) : qualifiedName;
    }

    private int countEndpoints(OpenAPI spec) {
        if (spec == null || spec.getPaths() == null) {
            return 0;
        }
        int count = 0;
        for (PathItem pathItem : spec.getPaths().values()) {
            if (pathItem == null) continue;
            if (pathItem.getGet() != null) count++;
            if (pathItem.getPost() != null) count++;
            if (pathItem.getPut() != null) count++;
            if (pathItem.getDelete() != null) count++;
            if (pathItem.getPatch() != null) count++;
        }
        return count;
    }
}

