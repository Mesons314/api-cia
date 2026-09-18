package com.apicia.service.sgm.rules;

import com.apicia.model.dto.ViolationDTO;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class RestPathStylingRule implements DesignRule {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^v[0-9]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("^\\{.*\\}$");

    // Standard non-resource / namespace words that are allowed as singular
    private static final Set<String> ALLOWED_SINGULAR_WORDS = Set.of(
            "api", "app", "auth", "login", "logout", "oauth", "oauth2", "token", "session",
            "health", "metrics", "info", "ping", "status", "version", "config", "data", "media",
            "swagger", "openapi", "docs", "actuator", "admin", "me", "self", "root", "public", "private", "internal"
    );

    // Common known singular to plural pairs for high-confidence detection
    private static final Map<String, String> COMMON_SINGULAR_TO_PLURAL = Map.ofEntries(
            Map.entry("user", "users"),
            Map.entry("account", "accounts"),
            Map.entry("clinic", "clinics"),
            Map.entry("doctor", "doctors"),
            Map.entry("patient", "patients"),
            Map.entry("hospital", "hospitals"),
            Map.entry("order", "orders"),
            Map.entry("item", "items"),
            Map.entry("product", "products"),
            Map.entry("customer", "customers"),
            Map.entry("service", "services"),
            Map.entry("spec", "specs"),
            Map.entry("endpoint", "endpoints"),
            Map.entry("report", "reports"),
            Map.entry("role", "roles"),
            Map.entry("group", "groups"),
            Map.entry("member", "members"),
            Map.entry("permission", "permissions"),
            Map.entry("notification", "notifications"),
            Map.entry("message", "messages"),
            Map.entry("comment", "comments"),
            Map.entry("post", "posts"),
            Map.entry("category", "categories"),
            Map.entry("company", "companies"),
            Map.entry("organization", "organizations"),
            Map.entry("department", "departments"),
            Map.entry("file", "files"),
            Map.entry("document", "documents"),
            Map.entry("image", "images"),
            Map.entry("device", "devices"),
            Map.entry("appointment", "appointments"),
            Map.entry("invoice", "invoices"),
            Map.entry("payment", "payments"),
            Map.entry("transaction", "transactions")
    );

    @Override
    public String getRuleId() {
        return "SGM-010";
    }

    @Override
    public String getRuleName() {
        return "REST Path Styling Check";
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

                // 1. Lowercase check: Flag segments containing uppercase letters (except inside path parameters)
                if (!segment.equals(segment.toLowerCase(Locale.ROOT))) {
                    violations.add(ViolationDTO.builder()
                            .ruleId(getRuleId())
                            .severity("WARNING")
                            .endpoint(path)
                            .message("REST Path Styling: Segment '" + segment + "' in path '" + path + "' contains uppercase characters. REST paths should be lowercase or kebab-case.")
                            .oldValue(segment)
                            .newValue(segment.toLowerCase(Locale.ROOT))
                            .build());
                }

                String cleanSegment = segment.toLowerCase(Locale.ROOT);

                // Skip version prefixes (v1, v2) and allowed non-resource words
                if (VERSION_PATTERN.matcher(cleanSegment).matches() || ALLOWED_SINGULAR_WORDS.contains(cleanSegment)) {
                    continue;
                }

                // 2. Plural Noun check: Flag resource segments using singular nouns
                if (COMMON_SINGULAR_TO_PLURAL.containsKey(cleanSegment)) {
                    String pluralForm = COMMON_SINGULAR_TO_PLURAL.get(cleanSegment);
                    violations.add(ViolationDTO.builder()
                            .ruleId(getRuleId())
                            .severity("WARNING")
                            .endpoint(path)
                            .message("REST Path Styling: Resource segment '" + segment + "' in path '" + path + "' should use plural noun form '/" + pluralForm + "'.")
                            .oldValue(segment)
                            .newValue(pluralForm)
                            .build());
                } else if (isLikelySingularNoun(cleanSegment)) {
                    String suggestedPlural = cleanSegment.endsWith("y") && !cleanSegment.endsWith("ay") && !cleanSegment.endsWith("ey")
                            ? cleanSegment.substring(0, cleanSegment.length() - 1) + "ies"
                            : cleanSegment + "s";
                    violations.add(ViolationDTO.builder()
                            .ruleId(getRuleId())
                            .severity("WARNING")
                            .endpoint(path)
                            .message("REST Path Styling: Resource segment '" + segment + "' in path '" + path + "' appears to be singular. Consider using plural noun '/" + suggestedPlural + "'.")
                            .oldValue(segment)
                            .newValue(suggestedPlural)
                            .build());
                }
            }
        }

        return violations;
    }

    private boolean isLikelySingularNoun(String word) {
        if (word.length() <= 3) return false;
        // If it already ends with 's', typically plural (users, accounts, processes)
        if (word.endsWith("s")) return false;
        // Avoid flagging compound actions or verbs ending in common suffixes
        if (word.endsWith("ing") || word.endsWith("ed")) return false;
        return false;
    }
}
