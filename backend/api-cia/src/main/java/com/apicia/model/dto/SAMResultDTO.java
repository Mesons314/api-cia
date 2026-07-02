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
public class SAMResultDTO {
    private Integer totalAlerts;
    private Integer criticalCount;
    private Integer highCount;
    private Double aSec;
    private List<SecurityAlertDTO> alerts;
}
