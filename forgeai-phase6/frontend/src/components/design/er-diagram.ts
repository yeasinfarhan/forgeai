import type { DatabaseDesignContent, DbColumn } from "@/lib/api";

/**
 * Builds a Mermaid ER diagram from the data model. Relationships are the foreign keys: a
 * non-null foreign key means every child has exactly one parent, a nullable one zero or one; a
 * unique foreign key makes the relationship one-to-one.
 */
export function erDiagram(content: DatabaseDesignContent): string {
  const lines = ["erDiagram"];
  for (const entity of content.entities) {
    lines.push(`  ${entity.name} {`);
    for (const column of entity.columns) {
      const keys = [column.primaryKey && "PK", column.references && "FK", column.unique && "UK"].filter(Boolean);
      lines.push(`    ${columnType(column)} ${column.name}${keys.length > 0 ? ` ${keys.join(", ")}` : ""}`);
    }
    lines.push("  }");
  }
  const names = new Set(content.entities.map((e) => e.name));
  for (const entity of content.entities) {
    for (const column of entity.columns) {
      const fk = column.references;
      if (!fk || !names.has(fk.entity)) continue;
      const parent = column.nullable ? "|o" : "||";
      const child = column.unique ? "o|" : "o{";
      lines.push(`  ${fk.entity} ${parent}--${child} ${entity.name} : "${column.name}"`);
    }
  }
  return lines.join("\n");
}

export function columnType(column: DbColumn): string {
  switch (column.type) {
    case "VARCHAR":
      return `VARCHAR(${column.length ?? ""})`;
    case "DECIMAL":
      return column.scale == null ? `DECIMAL(${column.precision ?? ""})` : `DECIMAL(${column.precision ?? ""},${column.scale})`;
    default:
      return column.type;
  }
}
