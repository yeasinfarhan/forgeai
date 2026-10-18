package com.forgeai.docs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.forgeai.architecture.ArchitectureContent;
import com.forgeai.architecture.ComponentKind;
import com.forgeai.design.api.ApiDesignContent;
import com.forgeai.design.api.FieldType;
import com.forgeai.design.database.DatabaseDesignContent;
import com.forgeai.requirement.RequirementDtos.RequirementResponse;
import com.forgeai.requirement.RequirementType;
import com.forgeai.task.TaskService.CodeTask;
import com.forgeai.task.TaskStatus;

/**
 * Markdown documents rendered directly from the approved design, without the AI, so they always
 * match it exactly. Diagrams are Mermaid code blocks (rendered by GitHub, GitLab and most editors).
 */
final class DesignMarkdown {

    private DesignMarkdown() {
    }

    // --- Requirements ----------------------------------------------------------------

    static String requirements(String projectName, List<RequirementResponse> approved) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(projectName).append(" — Requirements\n\n");
        md.append("The approved requirements of the project, as recorded in ForgeAI.\n");
        for (RequirementType type : RequirementType.values()) {
            List<RequirementResponse> list = approved.stream().filter(r -> r.type() == type).toList();
            if (list.isEmpty()) continue;
            md.append("\n## ").append(type == RequirementType.FUNCTIONAL ? "Functional requirements" : "Non-functional requirements").append('\n');
            for (RequirementResponse r : list) {
                md.append("\n### ").append(r.code()).append(" · ").append(line(r.title())).append("\n\n");
                md.append("**Priority:** ").append(label(r.priority().name()));
                if (r.category() != null && !r.category().isBlank()) md.append(" · **Category:** ").append(line(r.category()));
                md.append("\n\n").append(r.description().strip()).append('\n');
                if (!r.acceptanceCriteria().isEmpty()) {
                    md.append("\nAcceptance criteria:\n\n");
                    r.acceptanceCriteria().forEach(c -> md.append("- ").append(line(c)).append('\n'));
                }
            }
        }
        if (approved.isEmpty()) md.append("\nNo requirements are approved yet.\n");
        return md.toString();
    }

    // --- Architecture ----------------------------------------------------------------

    static String architecture(String projectName, ArchitectureContent a) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(projectName).append(" — Architecture\n\n");
        md.append("**Style:** ").append(line(a.style())).append("\n\n").append(a.styleRationale().strip()).append("\n\n");
        md.append("## Overview\n\n").append(a.overview().strip()).append("\n\n");

        md.append("## Components\n\n");
        md.append("```mermaid\n").append(componentDiagram(a.components())).append("\n```\n\n");
        md.append("| Component | Kind | Technology | Responsibility | Depends on | Requirements |\n");
        md.append("| --- | --- | --- | --- | --- | --- |\n");
        for (ArchitectureContent.Component c : a.components()) {
            md.append("| ").append(cell(c.name()))
                    .append(" | ").append(label(c.kind().name()))
                    .append(" | ").append(cell(c.technology()))
                    .append(" | ").append(cell(c.responsibility()))
                    .append(" | ").append(cell(String.join(", ", c.dependsOn())))
                    .append(" | ").append(cell(String.join(", ", c.requirements())))
                    .append(" |\n");
        }

        if (!a.decisions().isEmpty()) {
            md.append("\n## Decisions\n");
            int n = 1;
            for (ArchitectureContent.Decision d : a.decisions()) {
                md.append("\n### ").append(n++).append(". ").append(line(d.title())).append("\n\n");
                md.append("**Context.** ").append(d.context().strip()).append("\n\n");
                md.append("**Decision.** ").append(d.decision().strip()).append("\n\n");
                if (!d.alternatives().isEmpty()) {
                    md.append("**Alternatives considered:**\n\n");
                    d.alternatives().forEach(alt -> md.append("- ").append(line(alt)).append('\n'));
                    md.append('\n');
                }
                md.append("**Consequences.** ").append(d.consequences().strip()).append('\n');
                if (!d.requirements().isEmpty()) {
                    md.append("\nRequirements: ").append(String.join(", ", d.requirements())).append('\n');
                }
            }
        }

        if (!a.flows().isEmpty()) {
            md.append("\n## Key flows\n");
            for (ArchitectureContent.Flow f : a.flows()) {
                md.append("\n### ").append(line(f.title())).append("\n\n");
                if (f.description() != null && !f.description().isBlank()) md.append(f.description().strip()).append("\n\n");
                md.append("```mermaid\n").append(f.diagram().strip()).append("\n```\n");
            }
        }
        return md.toString();
    }

    /** The same flowchart the architecture page draws: one node per component, an arrow per dependency. */
    static String componentDiagram(List<ArchitectureContent.Component> components) {
        Map<String, String> ids = new HashMap<>();
        for (int i = 0; i < components.size(); i++) ids.put(key(components.get(i).name()), "c" + i);
        List<String> lines = new ArrayList<>();
        lines.add(components.size() > 6 ? "flowchart TB" : "flowchart LR");
        for (int i = 0; i < components.size(); i++) {
            ArchitectureContent.Component c = components.get(i);
            String[] shape = shape(c.kind());
            String label = c.technology() != null && !c.technology().isBlank()
                    ? mermaidLabel(c.name()) + "<br/><small>" + mermaidLabel(c.technology()) + "</small>"
                    : mermaidLabel(c.name());
            lines.add("  c" + i + shape[0] + "\"" + label + "\"" + shape[1]);
        }
        List<String> external = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            ArchitectureContent.Component c = components.get(i);
            for (String dependency : c.dependsOn()) {
                String target = ids.get(key(dependency));
                if (target != null && !target.equals("c" + i)) lines.add("  c" + i + " --> " + target);
            }
            if (c.kind() == ComponentKind.EXTERNAL) external.add("c" + i);
        }
        if (!external.isEmpty()) {
            lines.add("  classDef external stroke-dasharray: 5 5");
            lines.add("  class " + String.join(",", external) + " external");
        }
        return String.join("\n", lines);
    }

    private static String[] shape(ComponentKind kind) {
        return switch (kind) {
            case CLIENT -> new String[] {"([", "])"};
            case SERVICE -> new String[] {"[", "]"};
            case DATA_STORE -> new String[] {"[(", ")]"};
            case EXTERNAL -> new String[] {"{{", "}}"};
            case INFRASTRUCTURE -> new String[] {"[[", "]]"};
        };
    }

    /** Mermaid entity codes keep quotes, brackets and "#" in names from breaking the syntax. */
    private static String mermaidLabel(String text) {
        return text.replaceAll("\\s+", " ").strip()
                .replace("#", "#35;")
                .replace("\"", "#quot;")
                .replace("<", "#lt;")
                .replace(">", "#gt;");
    }

    // --- Data model ------------------------------------------------------------------

    static String dataModel(String projectName, DatabaseDesignContent db) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(projectName).append(" — Data model\n\n");
        md.append(db.summary().strip()).append("\n\n");
        md.append("The PostgreSQL schema is in [database.sql](database.sql).\n\n");
        md.append("```mermaid\n").append(erDiagram(db)).append("\n```\n");
        for (DatabaseDesignContent.Entity e : db.entities()) {
            md.append("\n## ").append(e.name()).append("\n\n");
            if (e.description() != null && !e.description().isBlank()) md.append(e.description().strip()).append("\n\n");
            md.append("| Column | Type | Null | Key | Default | References | Description |\n");
            md.append("| --- | --- | --- | --- | --- | --- | --- |\n");
            for (DatabaseDesignContent.Column c : e.columns()) {
                List<String> keys = new ArrayList<>();
                if (c.primaryKey()) keys.add("PK");
                if (c.unique()) keys.add("unique");
                String description = c.description() == null ? "" : c.description();
                if (!c.allowedValues().isEmpty()) {
                    description = (description.isBlank() ? "" : description.strip() + " ") + "One of: " + String.join(", ", c.allowedValues()) + ".";
                }
                md.append("| `").append(c.name()).append("` | ").append(columnType(c))
                        .append(" | ").append(c.nullable() ? "yes" : "no")
                        .append(" | ").append(String.join(", ", keys))
                        .append(" | ").append(c.defaultValue() == null ? "" : "`" + cell(c.defaultValue()) + "`")
                        .append(" | ").append(c.references() == null ? ""
                                : "`" + c.references().entity() + "." + c.references().column() + "` (on delete "
                                        + c.references().onDelete().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ")")
                        .append(" | ").append(cell(description))
                        .append(" |\n");
            }
            if (!e.indexes().isEmpty()) {
                md.append("\nIndexes:\n\n");
                e.indexes().forEach(i -> md.append("- ").append(i.unique() ? "unique " : "").append("(")
                        .append(String.join(", ", i.columns())).append(")\n"));
            }
            if (!e.requirements().isEmpty()) md.append("\nRequirements: ").append(String.join(", ", e.requirements())).append('\n');
        }
        return md.toString();
    }

    /** The same ER diagram the database design page draws: relationships are the foreign keys. */
    static String erDiagram(DatabaseDesignContent db) {
        List<String> lines = new ArrayList<>();
        lines.add("erDiagram");
        for (DatabaseDesignContent.Entity e : db.entities()) {
            lines.add("  " + e.name() + " {");
            for (DatabaseDesignContent.Column c : e.columns()) {
                List<String> keys = new ArrayList<>();
                if (c.primaryKey()) keys.add("PK");
                if (c.references() != null) keys.add("FK");
                if (c.unique()) keys.add("UK");
                lines.add("    " + columnType(c) + " " + c.name() + (keys.isEmpty() ? "" : " " + String.join(", ", keys)));
            }
            lines.add("  }");
        }
        Set<String> names = new HashSet<>();
        db.entities().forEach(e -> names.add(e.name()));
        for (DatabaseDesignContent.Entity e : db.entities()) {
            for (DatabaseDesignContent.Column c : e.columns()) {
                var fk = c.references();
                if (fk == null || !names.contains(fk.entity())) continue;
                String parent = c.nullable() ? "|o" : "||";
                String child = c.unique() ? "o|" : "o{";
                lines.add("  " + fk.entity() + " " + parent + "--" + child + " " + e.name() + " : \"" + c.name() + "\"");
            }
        }
        return String.join("\n", lines);
    }

    static String columnType(DatabaseDesignContent.Column c) {
        return switch (c.type()) {
            case VARCHAR -> "VARCHAR(" + (c.length() == null ? "" : c.length()) + ")";
            case DECIMAL -> c.scale() == null
                    ? "DECIMAL(" + (c.precision() == null ? "" : c.precision()) + ")"
                    : "DECIMAL(" + (c.precision() == null ? "" : c.precision()) + "," + c.scale() + ")";
            default -> c.type().name();
        };
    }

    // --- API reference ---------------------------------------------------------------

    static String apiReference(String projectName, ApiDesignContent api) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(projectName).append(" — API reference\n\n");
        md.append(api.summary().strip()).append("\n\n");
        md.append("**Base path:** `").append(api.basePath()).append("` · The OpenAPI 3.1 document is in [openapi.json](openapi.json).\n\n");
        md.append("## Authentication\n\n").append(api.authentication().strip()).append("\n\n");
        md.append("## Errors\n\n").append(api.errorFormat().strip()).append("\n\n");
        if (!api.errors().isEmpty()) {
            md.append("| Status | Code | Meaning |\n| --- | --- | --- |\n");
            api.errors().forEach(e -> md.append("| ").append(e.status()).append(" | `").append(cell(e.code())).append("` | ")
                    .append(cell(e.description())).append(" |\n"));
            md.append('\n');
        }

        md.append("## Endpoints\n\n| Method | Path | Summary | Authentication |\n| --- | --- | --- | --- |\n");
        for (ApiDesignContent.Endpoint e : api.endpoints()) {
            md.append("| ").append(e.method()).append(" | [`").append(fullPath(api, e)).append("`](#")
                    .append(anchor(e.method() + " " + fullPath(api, e))).append(") | ").append(cell(e.summary()))
                    .append(" | ").append(e.authenticated() ? "required" : "none").append(" |\n");
        }

        Map<Integer, ApiDesignContent.ErrorCase> errors = new HashMap<>();
        api.errors().forEach(e -> errors.put(e.status(), e));
        for (ApiDesignContent.Endpoint e : api.endpoints()) {
            md.append("\n### ").append(e.method()).append(' ').append(fullPath(api, e)).append("\n\n");
            md.append(line(e.summary())).append(".\n");
            if (e.description() != null && !e.description().isBlank()) md.append('\n').append(e.description().strip()).append('\n');
            md.append("\n- **Authentication:** ").append(e.authenticated() ? "required" : "none").append('\n');
            if (e.entity() != null && !e.entity().isBlank()) md.append("- **Entity:** `").append(e.entity()).append("`\n");
            if (e.requestSchema() != null && !e.requestSchema().isBlank()) {
                md.append("- **Request body:** ").append(schemaLink(e.requestSchema())).append('\n');
            }
            md.append("- **Response:** ").append(e.successStatus()).append(" — ");
            if (e.responseSchema() == null || e.responseSchema().isBlank()) {
                md.append("no body\n");
            } else {
                md.append(e.responseList() ? "list of " : "").append(schemaLink(e.responseSchema())).append('\n');
            }
            if (!e.requirements().isEmpty()) md.append("- **Requirements:** ").append(String.join(", ", e.requirements())).append('\n');
            parameters(md, "Path parameters", e.pathParameters(), true);
            parameters(md, "Query parameters", e.queryParameters(), false);
            if (!e.errorStatuses().isEmpty()) {
                md.append("\nErrors:\n\n");
                for (Integer status : e.errorStatuses()) {
                    ApiDesignContent.ErrorCase error = errors.get(status);
                    md.append("- ").append(status);
                    if (error != null) md.append(" `").append(error.code()).append("` — ").append(line(error.description()));
                    md.append('\n');
                }
            }
        }

        if (!api.schemas().isEmpty()) {
            md.append("\n## Schemas\n");
            for (ApiDesignContent.Schema s : api.schemas()) {
                md.append("\n### ").append(s.name()).append("\n\n");
                if (s.description() != null && !s.description().isBlank()) md.append(s.description().strip()).append("\n\n");
                if (s.entity() != null && !s.entity().isBlank()) md.append("Entity: `").append(s.entity()).append("`\n\n");
                md.append("| Field | Type | Required | Description |\n| --- | --- | --- | --- |\n");
                for (ApiDesignContent.Field f : s.fields()) {
                    md.append("| `").append(f.name()).append("` | ").append(fieldType(f.type(), f.ref(), f.array()))
                            .append(" | ").append(f.required() ? "yes" : "no")
                            .append(" | ").append(cell(f.description())).append(" |\n");
                }
            }
        }
        return md.toString();
    }

    private static void parameters(StringBuilder md, String title, List<ApiDesignContent.Parameter> list, boolean path) {
        if (list.isEmpty()) return;
        md.append('\n').append(title).append(":\n\n| Name | Type | Required | Description |\n| --- | --- | --- | --- |\n");
        for (ApiDesignContent.Parameter p : list) {
            md.append("| `").append(p.name()).append("` | ").append(fieldType(p.type(), null, false))
                    .append(" | ").append(path || p.required() ? "yes" : "no")
                    .append(" | ").append(cell(p.description())).append(" |\n");
        }
    }

    static String fullPath(ApiDesignContent api, ApiDesignContent.Endpoint e) {
        String base = api.basePath().endsWith("/") ? api.basePath().substring(0, api.basePath().length() - 1) : api.basePath();
        return base + e.path();
    }

    private static String fieldType(FieldType type, String ref, boolean array) {
        String name = type == FieldType.OBJECT && ref != null && !ref.isBlank()
                ? schemaLink(ref)
                : switch (type) {
                    case DATE_TIME -> "date-time";
                    case UUID -> "uuid";
                    default -> type.name().toLowerCase(Locale.ROOT);
                };
        return array ? "array of " + name : name;
    }

    private static String schemaLink(String schema) {
        return "[`" + schema + "`](#" + anchor(schema) + ")";
    }

    /** The heading anchor GitHub generates: lower case, punctuation removed, spaces as hyphens. */
    static String anchor(String heading) {
        return heading.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 _-]", "").replace(' ', '-');
    }

    // --- Backlog ---------------------------------------------------------------------

    static String backlog(String projectName, List<CodeTask> tasks) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(projectName).append(" — Development backlog\n\n");
        if (tasks.isEmpty()) return md.append("No tasks yet.\n").toString();
        long done = tasks.stream().filter(t -> t.status() == TaskStatus.DONE).count();
        md.append(tasks.size()).append(" tasks, ").append(done).append(" done.\n\n");
        md.append("| Task | Title | Type | Status | Depends on |\n| --- | --- | --- | --- | --- |\n");
        for (CodeTask t : tasks) {
            md.append("| ").append(t.code()).append(" | ").append(cell(t.title())).append(" | ").append(label(t.type().name()))
                    .append(" | ").append(status(t.status())).append(" | ").append(String.join(", ", t.details().dependsOn()))
                    .append(" |\n");
        }
        for (CodeTask t : tasks) {
            md.append("\n## ").append(t.code()).append(" · ").append(line(t.title())).append("\n\n");
            md.append("**Type:** ").append(label(t.type().name())).append(" · **Status:** ").append(status(t.status())).append("\n\n");
            md.append(t.description().strip()).append('\n');
            if (!t.details().acceptanceCriteria().isEmpty()) {
                md.append("\nAcceptance criteria:\n\n");
                t.details().acceptanceCriteria().forEach(c -> md.append("- ").append(line(c)).append('\n'));
            }
            List<String> links = new ArrayList<>();
            if (!t.details().requirements().isEmpty()) links.add("Requirements: " + String.join(", ", t.details().requirements()));
            if (!t.details().entities().isEmpty()) links.add("Entities: " + String.join(", ", t.details().entities()));
            if (!t.details().endpoints().isEmpty()) links.add("Endpoints: " + String.join(", ", t.details().endpoints()));
            if (!t.details().components().isEmpty()) links.add("Components: " + String.join(", ", t.details().components()));
            if (!links.isEmpty()) {
                md.append('\n');
                links.forEach(l -> md.append("- ").append(l).append('\n'));
            }
        }
        return md.toString();
    }

    private static String status(TaskStatus status) {
        return switch (status) {
            case TODO -> "To do";
            case IN_PROGRESS -> "In progress";
            case DONE -> "Done";
        };
    }

    // --- Helpers ---------------------------------------------------------------------

    /** "DATA_STORE" → "Data store". */
    static String label(String constant) {
        String text = constant.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Text on one line (headings, list items). */
    private static String line(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    /** Text safe inside a Markdown table cell. */
    static String cell(String text) {
        return line(text).replace("|", "\\|");
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
