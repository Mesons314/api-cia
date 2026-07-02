package com.apicia.service.spm;

import com.apicia.model.dto.SPMResultDTO;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.stereotype.Service;

@Service
public class SPMService {

    private final GraphDiffEngine graphDiffEngine;

    public SPMService(GraphDiffEngine graphDiffEngine) {
        this.graphDiffEngine = graphDiffEngine;
    }

    public SPMResultDTO analyze(OpenAPI oldSpec, OpenAPI newSpec) {
        ApiUsageGraph g1 = ApiUsageGraph.buildFrom(oldSpec);
        ApiUsageGraph g2 = ApiUsageGraph.buildFrom(newSpec);

        DiffResult diff = graphDiffEngine.compare(g1, g2);

        return SPMResultDTO.builder()
                .addedEndpoints(diff.getAddedNodes())
                .removedEndpoints(diff.getRemovedNodes())
                .changedEndpoints(diff.getChangedNodes())
                .flowChanged(diff.getFlowChanged())
                .dApi(diff.getDApi())
                .build();
    }
}
