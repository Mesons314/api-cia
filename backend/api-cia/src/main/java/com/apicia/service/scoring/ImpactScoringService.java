package com.apicia.service.scoring;

import com.apicia.model.dto.ImpactScoreDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImpactScoringService {

    @Value("${cia.weights.w1:0.70}")
    private double w1;

    @Value("${cia.weights.w2:0.30}")
    private double w2;

    @Value("${cia.blast.maxConsumers:10}")
    private int maxConsumers;

    @Value("${cia.risk.threshold.medium:0.25}")
    private double thresholdMedium;

    @Value("${cia.risk.threshold.high:0.50}")
    private double thresholdHigh;

    @Value("${cia.risk.threshold.critical:0.75}")
    private double thresholdCritical;

    public ImpactScoreDTO calculate(double dStruct) {
        return calculate(dStruct, 0);
    }

    public ImpactScoreDTO calculate(double dStruct, int blastRadius) {
        int effectiveMaxConsumers = maxConsumers > 0 ? maxConsumers : 10;
        double dBlast = Math.min(1.0, (double) blastRadius / effectiveMaxConsumers);
        double sTotal = Math.min(1.0, (w1 * dStruct) + (w2 * dBlast));

        String riskLevel;
        if (sTotal < thresholdMedium) {
            riskLevel = "LOW";
        } else if (sTotal < thresholdHigh) {
            riskLevel = "MEDIUM";
        } else if (sTotal < thresholdCritical) {
            riskLevel = "HIGH";
        } else {
            riskLevel = "CRITICAL";
        }

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("w1_dStruct", w1 * dStruct);
        breakdown.put("w2_dBlast", w2 * dBlast);

        return ImpactScoreDTO.builder()
                .dStruct(dStruct)
                .dBlast(dBlast)
                .sTotal(sTotal)
                .riskLevel(riskLevel)
                .breakdown(breakdown)
                .build();
    }
}

