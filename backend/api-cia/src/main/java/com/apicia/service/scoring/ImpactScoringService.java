package com.apicia.service.scoring;

import com.apicia.model.dto.ImpactScoreDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImpactScoringService {

    @Value("${cia.weights.w1}")
    private double w1;

    public ImpactScoreDTO calculate(double dStruct) {
        double sTotal = w1 * dStruct;

        String riskLevel;
        if (sTotal < 0.25) {
            riskLevel = "LOW";
        } else if (sTotal < 0.50) {
            riskLevel = "MEDIUM";
        } else if (sTotal < 0.75) {
            riskLevel = "HIGH";
        } else {
            riskLevel = "CRITICAL";
        }

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("w1_dStruct", w1 * dStruct);

        return ImpactScoreDTO.builder()
                .sTotal(sTotal)
                .riskLevel(riskLevel)
                .breakdown(breakdown)
                .build();
    }
}

