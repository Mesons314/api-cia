package com.apicia.service.spm;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiUsageGraph {
    private Map<String, ApiNode> nodes;
    private List<ApiEdge> edges;

    public static ApiUsageGraph buildFrom(OpenAPI spec) {
        Map<String, ApiNode> nodes = new LinkedHashMap<>();
        List<ApiEdge> edges = new ArrayList<>();

        if (spec == null || spec.getPaths() == null) {
            return ApiUsageGraph.builder().nodes(nodes).edges(edges).build();
        }

        // Step 1 - Build nodes
        for (Map.Entry<String, PathItem> entry : spec.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            if (pathItem == null)
                continue;

            addNodeIfPresent(nodes, path, "GET", pathItem.getGet());
            addNodeIfPresent(nodes, path, "POST", pathItem.getPost());
            addNodeIfPresent(nodes, path, "PUT", pathItem.getPut());
            addNodeIfPresent(nodes, path, "DELETE", pathItem.getDelete());
            addNodeIfPresent(nodes, path, "PATCH", pathItem.getPatch());
        }

        // Step 2 - Build edges
        for (ApiNode nodeA : nodes.values()) {
            for (ApiNode nodeB : nodes.values()) {
                if (nodeA.getNodeId().equals(nodeB.getNodeId())) {
                    continue;
                }

                // Check SHARED_PARAM
                boolean sharesParam = false;
                String sharedParamName = null;
                if (nodeA.getParameterNames() != null && nodeB.getParameterNames() != null) {
                    for (String paramA : nodeA.getParameterNames()) {
                        if (nodeB.getParameterNames().contains(paramA)) {
                            sharesParam = true;
                            sharedParamName = paramA;
                            break;
                        }
                    }
                }
                if (sharesParam) {
                    edges.add(ApiEdge.builder()
                            .fromNodeId(nodeA.getNodeId())
                            .toNodeId(nodeB.getNodeId())
                            .edgeType("SHARED_PARAM")
                            .sharedElement(sharedParamName)
                            .build());
                }

                // Check DATA_DEPENDENCY
                if (nodeB.getRequestBodyRef() != null && nodeA.getResponseRefs() != null) {
                    if (nodeA.getResponseRefs().contains(nodeB.getRequestBodyRef())) {
                        edges.add(ApiEdge.builder()
                                .fromNodeId(nodeA.getNodeId())
                                .toNodeId(nodeB.getNodeId())
                                .edgeType("DATA_DEPENDENCY")
                                .sharedElement(nodeB.getRequestBodyRef())
                                .build());
                    }
                }
            }
        }

        return ApiUsageGraph.builder().nodes(nodes).edges(edges).build();
    }

    private static void addNodeIfPresent(Map<String, ApiNode> nodes, String path, String method, Operation operation) {
        if (operation == null)
            return;

        String nodeId = method + ":" + path;
        List<String> parameterNames = getParameterNames(operation);
        String requestBodyRef = getRequestBodyRef(operation.getRequestBody());
        List<String> responseRefs = getResponseRefs(operation.getResponses());
        boolean requiresAuth = operation.getSecurity() != null && !operation.getSecurity().isEmpty();

        ApiNode node = ApiNode.builder()
                .nodeId(nodeId)
                .path(path)
                .httpMethod(method)
                .parameterNames(parameterNames)
                .requestBodyRef(requestBodyRef)
                .responseRefs(responseRefs)
                .requiresAuth(requiresAuth)
                .build();

        nodes.put(nodeId, node);
    }

    private static List<String> getParameterNames(Operation operation) {
        List<String> names = new ArrayList<>();
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                if (param != null && param.getName() != null) {
                    names.add(param.getName());
                }
            }
        }
        return names;
    }

    private static String getRequestBodyRef(RequestBody requestBody) {
        if (requestBody == null) {
            return null;
        }
        if (requestBody.get$ref() != null) {
            return requestBody.get$ref();
        }
        if (requestBody.getContent() != null) {
            for (MediaType mediaType : requestBody.getContent().values()) {
                if (mediaType != null && mediaType.getSchema() != null) {
                    String ref = mediaType.getSchema().get$ref();
                    if (ref != null) {
                        return ref;
                    }
                }
            }
        }
        return null;
    }

    private static List<String> getResponseRefs(ApiResponses responses) {
        List<String> refs = new ArrayList<>();
        if (responses == null) {
            return refs;
        }
        for (ApiResponse response : responses.values()) {
            if (response == null)
                continue;
            if (response.get$ref() != null) {
                refs.add(response.get$ref());
            }
            if (response.getContent() != null) {
                for (MediaType mediaType : response.getContent().values()) {
                    if (mediaType != null && mediaType.getSchema() != null) {
                        String ref = mediaType.getSchema().get$ref();
                        if (ref != null) {
                            refs.add(ref);
                        }
                    }
                }
            }
        }
        return refs;
    }
}
