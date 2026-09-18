package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class VerbInPathRule implements DesignRule {

    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("^\\{.*\\}$");

    // Action verbs to detect in REST paths
    private static final List<String> ACTION_VERBS = List.of(
            "create", "add", "new", "insert", "save",
            "update", "modify", "edit", "patch",
            "delete", "remove", "destroy", "cancel",
            "get", "fetch", "find", "list", "search", "read", "query", "retrieve", "view",
            "send", "upload", "download", "generate", "process", "export", "import"
    );

    @Override
    public String getRuleId() {
        return "SGM-011";
    }

    @Override
    public String getRuleName() {
        return "Verb-in-Path Warning Check";
    }

    @Override
    public List<ViolationDTO> evaluate(OpenAPI oldSpec, OpenAPI newSpec) {
        if (newSpec == null || newSpec.getPaths() == null) {
            return Collections.emptyList();
        }

        List<ViolationDTO> violations = new ArrayList<>();

        for (String path : newSpec.getPaths().keySet()) {
            if (path == null || path.isBlank()) continue;

            String[] segments = path.split("/");
            for (String segment : segments) {
                if (segment == null || segment.isBlank()) continue;

                // Skip path parameters like {id}, {userId}
                if (PATH_VARIABLE_PATTERN.matcher(segment).matches()) {
                    continue;
                }

                String detectedVerb = detectVerbInSegment(segment);
                if (detectedVerb != null) {
                    violations.add(ViolationDTO.builder()
                            .ruleId(getRuleId())
                            .severity("WARNING")
                            .endpoint(path)
                            .message("Verb-in-Path Warning: Path '" + path + "' contains action verb '" + detectedVerb + "' in segment '" + segment + "'. HTTP methods (POST, PUT, DELETE, GET) should imply the action; paths should represent resources.")
                            .oldValue(segment)
                            .newValue("N/A")
                            .build());
                    break; // report once per path
                }
            }
        }

        return violations;
    }

    private String detectVerbInSegment(String segment) {
        String lower = segment.toLowerCase(Locale.ROOT);

        for (String verb : ACTION_VERBS) {
            // 1. Exact match (e.g. /createUser or /users/create or /create)
            if (lower.equals(verb)) {
                return verb;
            }

            // 2. Prefix in camelCase (e.g., createUser, updateClinic, deleteOrder, getItems)
            if (lower.startsWith(verb) && lower.length() > verb.length()) {
                char nextChar = segment.charAt(verb.length());
                if (Character.isUpperCase(nextChar) || nextChar == '-' || nextChar == '_') {
                    return verb;
                }
            }

            // 3. Suffix or kebab-case / snake_case compound (e.g., user-create, clinic-update, order_delete)
            if (lower.endsWith("-" + verb) || lower.endsWith("_" + verb) || lower.startsWith(verb + "-") || lower.startsWith(verb + "_")) {
                return verb;
            }
        }

        return null;
    }
}
