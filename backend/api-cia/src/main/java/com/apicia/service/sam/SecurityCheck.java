package com.apicia.service.sam;

import com.apicia.model.dto.SecurityAlertDTO;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.List;

public interface SecurityCheck {
    String getCheckId();
    List<SecurityAlertDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec);
}
