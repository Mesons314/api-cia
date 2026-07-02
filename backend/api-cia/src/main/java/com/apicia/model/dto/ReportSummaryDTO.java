package com.apicia.model.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSummaryDTO {
    private Long id;
    private String oldVersion;
    private String newVersion;
    private Double sTotal;
    private String riskLevel;
    private LocalDateTime createdAt;
}
