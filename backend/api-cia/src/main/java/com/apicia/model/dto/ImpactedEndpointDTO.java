package com.apicia.model.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactedEndpointDTO {
    private String endpoint;
    private String method;
    private List<String> violations;
    private List<ImpactedConsumerDTO> consumers;
}
