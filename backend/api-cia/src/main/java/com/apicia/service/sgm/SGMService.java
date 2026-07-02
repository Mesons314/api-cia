package com.apicia.service.sgm;

import com.apicia.model.dto.SGMResultDTO;
import com.apicia.model.dto.ViolationDTO;
import com.apicia.service.sgm.rules.DesignRule;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
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
