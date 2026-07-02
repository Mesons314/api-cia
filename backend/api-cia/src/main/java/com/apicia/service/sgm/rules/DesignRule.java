package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.List;

public interface DesignRule {
    String getRuleId();
    String getRuleName();
    List<ViolationDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec);
}
