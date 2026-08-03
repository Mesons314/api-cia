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
public class ExtractionSnapshotDTO {
    private Long specId;
    private String versionLabel;
    private Integer totalEndpoints;
    private boolean saved;
    private boolean analyzed;
    private Long analysisReportId;
    private LocalDateTime extractedAt;
}
