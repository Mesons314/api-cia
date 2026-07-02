package com.apicia.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequestDTO {
    @NotNull
    private Long oldSpecId;

    @NotNull
    private Long newSpecId;
}
