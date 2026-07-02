package com.apicia.service.spm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GraphDiffEngine {

    public DiffResult compare(ApiUsageGraph g1, ApiUsageGraph g2) {
        List<String> addedNodes = new ArrayList<>();
        List<String> removedNodes = new ArrayList<>();
        List<String> changedNodes = new ArrayList<>();

        Map<String, ApiNode> nodes1 = g1.getNodes() != null ? g1.getNodes() : Collections.emptyMap();
        Map<String, ApiNode> nodes2 = g2.getNodes() != null ? g2.getNodes() : Collections.emptyMap();

        // Step 1 - Node diff
        for (Map.Entry<String, ApiNode> entry : nodes1.entrySet()) {
            String nodeId = entry.getKey();
            ApiNode node1 = entry.getValue();

            if (!nodes2.containsKey(nodeId)) {
                removedNodes.add(nodeId);
            } else {
                ApiNode node2 = nodes2.get(nodeId);
                if (isChanged(node1, node2)) {
                    changedNodes.add(nodeId);
                }
            }
        }

        for (String nodeId : nodes2.keySet()) {
            if (!nodes1.containsKey(nodeId)) {
                addedNodes.add(nodeId);
            }
        }

        // Step 2 - Edge diff
        List<ApiEdge> edges1 = g1.getEdges() != null ? g1.getEdges() : Collections.emptyList();
        List<ApiEdge> edges2 = g2.getEdges() != null ? g2.getEdges() : Collections.emptyList();

        Set<String> sigs1 = new HashSet<>();
        for (ApiEdge edge : edges1) {
            if (edge != null) {
                sigs1.add(edge.getEdgeType() + ":" + edge.getFromNodeId() + "→" + edge.getToNodeId());
            }
        }

        Set<String> sigs2 = new HashSet<>();
        for (ApiEdge edge : edges2) {
            if (edge != null) {
                sigs2.add(edge.getEdgeType() + ":" + edge.getFromNodeId() + "→" + edge.getToNodeId());
            }
        }

        List<String> addedEdges = new ArrayList<>();
        for (String sig : sigs2) {
            if (!sigs1.contains(sig)) {
                addedEdges.add(sig);
            }
        }

        List<String> removedEdges = new ArrayList<>();
        for (String sig : sigs1) {
            if (!sigs2.contains(sig)) {
                removedEdges.add(sig);
            }
        }

        boolean flowChanged = !addedEdges.isEmpty() || !removedEdges.isEmpty();

        // Step 3 - Calculate Dapi
        int total = nodes1.size();
        double dApi = 0.0;
        if (total > 0) {
            int impacted = removedNodes.size() + changedNodes.size();
            dApi = Math.min(1.0, (double) impacted / total);
        }

        return DiffResult.builder()
                .addedNodes(addedNodes)
                .removedNodes(removedNodes)
                .changedNodes(changedNodes)
                .addedEdges(addedEdges)
                .removedEdges(removedEdges)
                .flowChanged(flowChanged)
                .dApi(dApi)
                .build();
    }

    private boolean isChanged(ApiNode n1, ApiNode n2) {
        boolean paramsDiffer = !Objects.equals(n1.getParameterNames(), n2.getParameterNames());
        boolean authChanged = n1.isRequiresAuth() != n2.isRequiresAuth();
        boolean responsesDiffer = !Objects.equals(n1.getResponseRefs(), n2.getResponseRefs());
        boolean requestBodyDiffer = !Objects.equals(n1.getRequestBodyRef(), n2.getRequestBodyRef());

        return paramsDiffer || authChanged || responsesDiffer || requestBodyDiffer;
    }
}
