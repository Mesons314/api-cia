package com.apicia.service.sam;

import com.apicia.model.dto.SecurityAlertDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HttpsDowngradeCheck implements SecurityCheck {

    @Override
    public String getCheckId() {
        return "SAM-003";
    }

    @Override
    public List<SecurityAlertDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec) {
        if (oldSpec == null || newSpec == null) {
            return Collections.emptyList();
        }

        List<Server> oldServers = oldSpec.getServers();
        List<Server> newServers = newSpec.getServers();

        boolean oldHasHttps = false;
        if (oldServers != null) {
            for (Server s : oldServers) {
                if (s != null && s.getUrl() != null && s.getUrl().toLowerCase().startsWith("https://")) {
                    oldHasHttps = true;
                    break;
                }
            }
        }

        boolean newHasHttp = false;
        if (newServers != null) {
            for (Server s : newServers) {
                if (s != null && s.getUrl() != null && s.getUrl().toLowerCase().startsWith("http://")) {
                    newHasHttp = true;
                    break;
                }
            }
        }

        List<SecurityAlertDTO> alerts = new ArrayList<>();
        if (oldHasHttps && newHasHttp) {
            alerts.add(SecurityAlertDTO.builder()
                    .checkId(getCheckId())
                    .severity("CRITICAL")
                    .endpoint("SERVER CONFIG")
                    .description("Server URL downgraded from HTTPS to HTTP. All API traffic is now transmitted unencrypted. This is a critical security risk")
                    .build());
        }

        return alerts;
    }
}
