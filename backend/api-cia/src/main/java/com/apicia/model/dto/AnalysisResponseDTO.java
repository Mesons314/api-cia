package com.apicia.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponseDTO {
    private Long reportId;
    private String oldVersion;
    private String newVersion;
    private SGMResultDTO sgm;

    private ImpactScoreDTO impactScore;
    private BlastRadiusDTO blastRadius;

    private Long oldSpecId;
    private String oldSpecTimestamp;
    private Long newSpecId;
    private String newSpecTimestamp;
}
