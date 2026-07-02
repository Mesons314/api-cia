package com.apicia.model.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactScoreDTO {
    private Double sTotal;
    private String riskLevel;
    private Map<String, Double> breakdown;
}
