package com.apicia.service.sam;

import com.apicia.model.dto.SAMResultDTO;
import com.apicia.model.dto.SecurityAlertDTO;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SAMService {

    private final List<SecurityCheck> checks;

    public SAMService(List<SecurityCheck> checks) {
        this.checks = checks;
    }

    public SAMResultDTO analyze(OpenAPI oldSpec, OpenAPI newSpec) {
        List<SecurityAlertDTO> alerts = new ArrayList<>();

        if (checks != null) {
            for (SecurityCheck check : checks) {
                try {
                    List<SecurityAlertDTO> checkAlerts = check.evaluate(oldSpec, newSpec);
                    if (checkAlerts != null) {
                        alerts.addAll(checkAlerts);
                    }
                } catch (Exception e) {
                    // Ignore exceptions during check evaluation
                }
            }
        }

        int criticalCount = 0;
        int highCount = 0;

        for (SecurityAlertDTO alert : alerts) {
            if (alert != null && alert.getSeverity() != null) {
                if ("CRITICAL".equalsIgnoreCase(alert.getSeverity())) {
                    criticalCount++;
                } else if ("HIGH".equalsIgnoreCase(alert.getSeverity())) {
                    highCount++;
                }
            }
        }

        double aSec = Math.min(1.0, criticalCount * 0.4 + highCount * 0.2);

        return SAMResultDTO.builder()
                .totalAlerts(alerts.size())
                .criticalCount(criticalCount)
                .highCount(highCount)
                .aSec(aSec)
                .alerts(alerts)
                .build();
    }
}
