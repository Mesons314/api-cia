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
public class SPMResultDTO {
    private List<String> addedEndpoints;
    private List<String> removedEndpoints;
    private List<String> changedEndpoints;
    private Boolean flowChanged;
    private Double dApi;
}
