package com.apicia.service.spm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEdge {
    private String fromNodeId;
    private String toNodeId;
    private String edgeType;
    private String sharedElement;
}
