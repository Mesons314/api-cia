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

    @Value("${cia.weights.w2}")
    private double w2;

    @Value("${cia.weights.w3}")
    private double w3;

    public ImpactScoreDTO calculate(double dStruct, double dApi, double aSec) {
        double sTotal = w1 * dStruct + w2 * dApi + w3 * aSec;

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
        breakdown.put("w2_dApi", w2 * dApi);
        breakdown.put("w3_aSec", w3 * aSec);

        return ImpactScoreDTO.builder()
                .sTotal(sTotal)
                .riskLevel(riskLevel)
                .breakdown(breakdown)
                .build();
    }
}
