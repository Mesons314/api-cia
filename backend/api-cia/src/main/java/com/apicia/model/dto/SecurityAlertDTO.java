package com.apicia.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAlertDTO {
    private String checkId;
    private String severity;
    private String endpoint;
    private String description;
}
