package com.apicia.service.scoring;

import static org.junit.jupiter.api.Assertions.*;

import com.apicia.model.dto.ImpactScoreDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class ImpactScoringServiceTest {

    private ImpactScoringService scoringService;

    @BeforeEach
    public void setUp() {
        scoringService = new ImpactScoringService();
        ReflectionTestUtils.setField(scoringService, "w1", 0.70);
        ReflectionTestUtils.setField(scoringService, "w2", 0.30);
        ReflectionTestUtils.setField(scoringService, "maxConsumers", 10);
        ReflectionTestUtils.setField(scoringService, "thresholdMedium", 0.25);
        ReflectionTestUtils.setField(scoringService, "thresholdHigh", 0.50);
        ReflectionTestUtils.setField(scoringService, "thresholdCritical", 0.75);
    }

    @Test
    public void testMARSMFormula_CombinedScore() {
        // dStruct = 0.5, blastRadius = 5 consumers -> dBlast = 5/10 = 0.5
        // expected sTotal = (0.70 * 0.5) + (0.30 * 0.5) = 0.35 + 0.15 = 0.50 -> HIGH
        ImpactScoreDTO result = scoringService.calculate(0.5, 5);

        assertNotNull(result);
        assertEquals(0.5, result.getDStruct(), 0.001);
        assertEquals(0.5, result.getDBlast(), 0.001);
        assertEquals(0.50, result.getSTotal(), 0.001);
        assertEquals("HIGH", result.getRiskLevel());
        assertEquals(0.35, result.getBreakdown().get("w1_dStruct"), 0.001);
        assertEquals(0.15, result.getBreakdown().get("w2_dBlast"), 0.001);
    }

    @Test
    public void testSafetyClamp_DoesNotExceedOne() {
        // dStruct = 1.0, blastRadius = 20 consumers -> dBlast = min(1.0, 20/10) = 1.0
        // sTotal = min(1.0, 0.7 + 0.3) = 1.0 -> CRITICAL
        ImpactScoreDTO result = scoringService.calculate(1.0, 20);

        assertEquals(1.0, result.getDBlast(), 0.001);
        assertEquals(1.0, result.getSTotal(), 0.001);
        assertEquals("CRITICAL", result.getRiskLevel());
    }

    @Test
    public void testThresholds_LowRisk() {
        // dStruct = 0.1, blastRadius = 0 -> sTotal = 0.07 -> LOW
        ImpactScoreDTO result = scoringService.calculate(0.1, 0);
        assertEquals("LOW", result.getRiskLevel());
    }

    @Test
    public void testThresholds_MediumRisk() {
        // dStruct = 0.4, blastRadius = 2 -> sTotal = 0.28 + 0.06 = 0.34 -> MEDIUM
        ImpactScoreDTO result = scoringService.calculate(0.4, 2);
        assertEquals("MEDIUM", result.getRiskLevel());
    }

    @Test
    public void testSingleArgOverload_BackwardCompatibility() {
        // Calling single arg calculate(0.5) assumes blastRadius = 0
        // sTotal = 0.70 * 0.5 = 0.35 -> MEDIUM
        ImpactScoreDTO result = scoringService.calculate(0.5);

        assertEquals(0.5, result.getDStruct(), 0.001);
        assertEquals(0.0, result.getDBlast(), 0.001);
        assertEquals(0.35, result.getSTotal(), 0.001);
        assertEquals("MEDIUM", result.getRiskLevel());
    }

    @Test
    public void testZeroWeightBlast_OldBehavior() {
        ReflectionTestUtils.setField(scoringService, "w1", 1.00);
        ReflectionTestUtils.setField(scoringService, "w2", 0.00);

        ImpactScoreDTO result = scoringService.calculate(0.6, 10);
        assertEquals(0.60, result.getSTotal(), 0.001);
        assertEquals("HIGH", result.getRiskLevel());
    }
}
