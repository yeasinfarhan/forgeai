import type { ArchitectureComponent, ComponentKind } from "@/lib/api";

/** Node shape per component kind, as Mermaid flowchart syntax around a quoted label. */
const SHAPES: Record<ComponentKind, [string, string]> = {
  CLIENT: ["([", "])"],
  SERVICE: ["[", "]"],
  DATA_STORE: ["[(", ")]"],
  EXTERNAL: ["{{", "}}"],
  INFRASTRUCTURE: ["[[", "]]"],
};

/**
 * Builds a Mermaid flowchart from the component list, so the picture always matches the
 * components (edits included) instead of being a separate drawing that can drift.
 */
export function componentDiagram(components: ArchitectureComponent[]): string {
  const ids = new Map<string, string>();
  components.forEach((component, index) => ids.set(key(component.name), `c${index}`));

  const lines = [components.length > 6 ? "flowchart TB" : "flowchart LR"];
  components.forEach((component, index) => {
    const [open, close] = SHAPES[component.kind];
    const label = component.technology
      ? `${escapeLabel(component.name)}<br/><small>${escapeLabel(component.technology)}</small>`
      : escapeLabel(component.name);
    lines.push(`  c${index}${open}"${label}"${close}`);
  });
  components.forEach((component, index) => {
    for (const dependency of component.dependsOn) {
      const target = ids.get(key(dependency));
      if (target && target !== `c${index}`) lines.push(`  c${index} --> ${target}`);
    }
  });
  const external = components.flatMap((c, i) => (c.kind === "EXTERNAL" ? [`c${i}`] : []));
  if (external.length > 0) {
    lines.push("  classDef external stroke-dasharray: 5 5");
    lines.push(`  class ${external.join(",")} external`);
  }
  return lines.join("\n");
}

function key(name: string) {
  return name.trim().toLowerCase();
}

/** Mermaid entity codes keep quotes, brackets and "#" in names from breaking the syntax. */
function escapeLabel(text: string) {
  return text
    .replace(/\s+/g, " ")
    .trim()
    .replace(/#/g, "#35;")
    .replace(/"/g, "#quot;")
    .replace(/</g, "#lt;")
    .replace(/>/g, "#gt;");
}
