package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Order(1) // Run before RemovedEndpointRule
public class EndpointRenameRule implements DesignRule {

    @Override
    public String getRuleId() {
        return "SGM-009";
    }

    @Override
    public String getRuleName() {
        return "Endpoint Rename Check";
    }

    @Override
    public List<ViolationDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec) {
        if (oldSpec == null || oldSpec.getPaths() == null || newSpec == null || newSpec.getPaths() == null) {
            return Collections.emptyList();
        }

        List<ViolationDTO> violations = new ArrayList<>();

        List<EndpointInfo> removedEndpoints = new ArrayList<>();
        for (Map.Entry<String, PathItem> entry : oldSpec.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            if (pathItem == null) continue;
            
            addEndpointIfRemoved(removedEndpoints, path, "GET", pathItem.getGet(), newSpec);
            addEndpointIfRemoved(removedEndpoints, path, "POST", pathItem.getPost(), newSpec);
            addEndpointIfRemoved(removedEndpoints, path, "PUT", pathItem.getPut(), newSpec);
            addEndpointIfRemoved(removedEndpoints, path, "DELETE", pathItem.getDelete(), newSpec);
            addEndpointIfRemoved(removedEndpoints, path, "PATCH", pathItem.getPatch(), newSpec);
        }

        List<EndpointInfo> addedEndpoints = new ArrayList<>();
        for (Map.Entry<String, PathItem> entry : newSpec.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            if (pathItem == null) continue;
            
            addEndpointIfAdded(addedEndpoints, path, "GET", pathItem.getGet(), oldSpec);
            addEndpointIfAdded(addedEndpoints, path, "POST", pathItem.getPost(), oldSpec);
            addEndpointIfAdded(addedEndpoints, path, "PUT", pathItem.getPut(), oldSpec);
            addEndpointIfAdded(addedEndpoints, path, "DELETE", pathItem.getDelete(), oldSpec);
            addEndpointIfAdded(addedEndpoints, path, "PATCH", pathItem.getPatch(), oldSpec);
        }

        List<EndpointInfo> matchedRemoved = new ArrayList<>();
        List<EndpointInfo> matchedAdded = new ArrayList<>();

        for (EndpointInfo rem : removedEndpoints) {
            for (EndpointInfo add : addedEndpoints) {
                if (matchedAdded.contains(add)) {
                    continue;
                }
                
                if (rem.controllerClass != null && rem.controllerClass.equals(add.controllerClass) &&
                    rem.controllerMethod != null && rem.controllerMethod.equals(add.controllerMethod) &&
                    rem.httpMethod.equals(add.httpMethod)) {
                    
                    if (areParametersSimilar(rem.operation.getParameters(), add.operation.getParameters()) &&
                        areRequestBodiesSimilar(rem.operation.getRequestBody(), add.operation.getRequestBody())) {
                        
                        matchedRemoved.add(rem);
                        matchedAdded.add(add);
                        
                        String controllerSimpleName = getSimpleName(rem.controllerClass);
                        String message = "Endpoint path renamed from " + rem.path + " to " + add.path + 
                                         ". Existing clients using the old endpoint will receive 404 until updated.";
                        
                        violations.add(ViolationDTO.builder()
                                .ruleId(getRuleId())
                                .severity("BREAKING")
                                .endpoint(add.path)
                                .message(message)
                                .oldValue(rem.path)
                                .newValue(add.path)
                                .changeType("ENDPOINT_RENAMED")
                                .oldPath(rem.path)
                                .newPath(add.path)
                                .controller(controllerSimpleName)
                                .method(rem.controllerMethod)
                                .build());
                        
                        removeOperation(oldSpec, rem.path, rem.httpMethod);
                        removeOperation(newSpec, add.path, add.httpMethod);
                        
                        break; 
                    }
                }
            }
        }

        return violations;
    }

    private void addEndpointIfRemoved(List<EndpointInfo> list, String path, String method, Operation op, OpenAPI newSpec) {
        if (op == null) return;
        
        boolean exists = false;
        if (newSpec.getPaths().containsKey(path)) {
            PathItem pathItem = newSpec.getPaths().get(path);
            if (pathItem != null) {
                Operation newOp = getOperation(pathItem, method);
                if (newOp != null) {
                    exists = true;
                }
            }
        }
        
        if (!exists) {
            list.add(new EndpointInfo(path, method, op));
        }
    }

    private void addEndpointIfAdded(List<EndpointInfo> list, String path, String method, Operation op, OpenAPI oldSpec) {
        if (op == null) return;
        
        boolean exists = false;
        if (oldSpec.getPaths().containsKey(path)) {
            PathItem pathItem = oldSpec.getPaths().get(path);
            if (pathItem != null) {
                Operation oldOp = getOperation(pathItem, method);
                if (oldOp != null) {
                    exists = true;
                }
            }
        }
        
        if (!exists) {
            list.add(new EndpointInfo(path, method, op));
        }
    }

    private Operation getOperation(PathItem pathItem, String method) {
        switch (method) {
            case "GET": return pathItem.getGet();
            case "POST": return pathItem.getPost();
            case "PUT": return pathItem.getPut();
            case "DELETE": return pathItem.getDelete();
            case "PATCH": return pathItem.getPatch();
        }
        return null;
    }

    private void removeOperation(OpenAPI spec, String path, String method) {
        PathItem pathItem = spec.getPaths().get(path);
        if (pathItem != null) {
            switch (method) {
                case "GET": pathItem.setGet(null); break;
                case "POST": pathItem.setPost(null); break;
                case "PUT": pathItem.setPut(null); break;
                case "DELETE": pathItem.setDelete(null); break;
                case "PATCH": pathItem.setPatch(null); break;
            }
            if (pathItem.getGet() == null && pathItem.getPost() == null && pathItem.getPut() == null && 
                pathItem.getDelete() == null && pathItem.getPatch() == null) {
                spec.getPaths().remove(path);
            }
        }
    }

    private String getSimpleName(String qualifiedName) {
        if (qualifiedName == null) return null;
        int idx = qualifiedName.lastIndexOf('.');
        return idx >= 0 ? qualifiedName.substring(idx + 1) : qualifiedName;
    }

    private boolean areParametersSimilar(List<io.swagger.v3.oas.models.parameters.Parameter> params1, 
                                        List<io.swagger.v3.oas.models.parameters.Parameter> params2) {
        if (params1 == null && params2 == null) return true;
        if (params1 == null || params2 == null) return params1 == null ? params2.isEmpty() : params1.isEmpty();
        if (params1.size() != params2.size()) return false;
        
        for (int i = 0; i < params1.size(); i++) {
            io.swagger.v3.oas.models.parameters.Parameter p1 = params1.get(i);
            io.swagger.v3.oas.models.parameters.Parameter p2 = params2.get(i);
            
            if (p1.getIn() != null && !p1.getIn().equals(p2.getIn())) {
                return false;
            }
            
            String t1 = p1.getSchema() != null ? p1.getSchema().getType() : null;
            String t2 = p2.getSchema() != null ? p2.getSchema().getType() : null;
            if (t1 != null && !t1.equals(t2)) {
                return false;
            }
        }
        return true;
    }

    private boolean areRequestBodiesSimilar(io.swagger.v3.oas.models.parameters.RequestBody b1, 
                                           io.swagger.v3.oas.models.parameters.RequestBody b2) {
        if (b1 == null && b2 == null) return true;
        if (b1 == null || b2 == null) return false;
        
        if (b1.getContent() != null && b2.getContent() != null) {
            return b1.getContent().keySet().equals(b2.getContent().keySet());
        }
        return b1.getContent() == null && b2.getContent() == null;
    }

    private static class EndpointInfo {
        private final String path;
        private final String httpMethod;
        private final Operation operation;
        private final String controllerClass;
        private final String controllerMethod;

        private EndpointInfo(String path, String httpMethod, Operation operation) {
            this.path = path;
            this.httpMethod = httpMethod;
            this.operation = operation;
            
            String cClass = null;
            String cMethod = null;
            if (operation.getExtensions() != null) {
                cClass = (String) operation.getExtensions().get("x-controller-class");
                cMethod = (String) operation.getExtensions().get("x-controller-method");
            }
            this.controllerClass = cClass;
            this.controllerMethod = cMethod;
        }
    }
}
