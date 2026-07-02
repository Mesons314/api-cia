package com.apicia.service.spm;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffResult {
    private List<String> addedNodes;
    private List<String> removedNodes;
    private List<String> changedNodes;
    private List<String> addedEdges;
    private List<String> removedEdges;
    private Boolean flowChanged;
    private Double dApi;
}
