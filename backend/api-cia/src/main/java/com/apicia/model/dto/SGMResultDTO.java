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
public class SGMResultDTO {
    private Integer totalViolations;
    private Integer breakingCount;
    private Integer warningCount;
    private Integer infoCount;
    private Double dStruct;
    private List<ViolationDTO> violations;
}
