import type { ReactNode } from "react";

import { Section } from "@/components/architecture/architecture-view";
import { MermaidDiagram } from "@/components/architecture/mermaid-diagram";
import { columnType, erDiagram } from "@/components/design/er-diagram";
import { Badge } from "@/components/ui/badge";
import type { DatabaseDesignContent, DbColumn, DbEntity, RequirementRef } from "@/lib/api";

export function RequirementChips({ codes, requirements }: { codes: string[]; requirements: RequirementRef[] }) {
  if (codes.length === 0) return null;
  const titles = new Map(requirements.map((r) => [r.code, r.title]));
  return (
    <div className="flex flex-wrap gap-1">
      {codes.map((code) => (
        <Badge key={code} variant="outline" className="font-mono" title={titles.get(code) ?? "Not an approved requirement"}>
          {code}
        </Badge>
      ))}
    </div>
  );
}

function constraints(column: DbColumn): string[] {
  const parts: string[] = [];
  if (column.primaryKey) parts.push("PK");
  if (column.references) {
    parts.push(
      `→ ${column.references.entity}.${column.references.column} (on delete ${column.references.onDelete.replace("_", " ").toLowerCase()})`,
    );
  }
  if (column.unique) parts.push("unique");
  parts.push(column.nullable ? "null" : "not null");
  if (column.defaultValue != null) parts.push(`default ${column.defaultValue}`);
  if (column.allowedValues.length > 0) parts.push(`one of ${column.allowedValues.join(", ")}`);
  return parts;
}

export function SummarySection({ content, action }: { content: DatabaseDesignContent; action?: ReactNode }) {
  return (
    <Section title="Summary" action={action}>
      <p className="text-sm leading-relaxed whitespace-pre-wrap">{content.summary}</p>
    </Section>
  );
}

export function ErDiagramSection({ content }: { content: DatabaseDesignContent }) {
  return (
    <Section title="Entity–relationship diagram" description="Drawn from the entities and their foreign keys.">
      <MermaidDiagram source={erDiagram(content)} label="Entity–relationship diagram" />
    </Section>
  );
}

export function EntityCard({
  entity,
  requirements,
  action,
}: {
  entity: DbEntity;
  requirements: RequirementRef[];
  action?: ReactNode;
}) {
  return (
    <li className="space-y-3 rounded-lg border bg-background/40 p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-mono font-medium">{entity.name}</p>
          {entity.description && <p className="text-sm text-muted-foreground">{entity.description}</p>}
        </div>
        {action}
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left text-xs text-muted-foreground">
              <th className="py-1.5 pr-3 font-medium">Column</th>
              <th className="py-1.5 pr-3 font-medium">Type</th>
              <th className="py-1.5 pr-3 font-medium">Constraints</th>
              <th className="py-1.5 font-medium">Description</th>
            </tr>
          </thead>
          <tbody>
            {entity.columns.map((column) => (
              <tr key={column.name} className="border-b align-top last:border-0">
                <td className="py-1.5 pr-3 font-mono text-xs">{column.name}</td>
                <td className="py-1.5 pr-3 font-mono text-xs text-primary">{columnType(column)}</td>
                <td className="py-1.5 pr-3 text-xs text-muted-foreground">{constraints(column).join(" · ")}</td>
                <td className="py-1.5 text-xs text-muted-foreground">{column.description}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {entity.indexes.length > 0 && (
        <p className="text-xs text-muted-foreground">
          Indexes:{" "}
          {entity.indexes.map((index, i) => (
            <span key={i} className="mr-3 font-mono">
              {index.unique ? "unique " : ""}({index.columns.join(", ")})
            </span>
          ))}
        </p>
      )}
      <RequirementChips codes={entity.requirements} requirements={requirements} />
    </li>
  );
}

/** The whole data model, read-only (used for earlier revisions). */
export function DatabaseView({ content, requirements }: { content: DatabaseDesignContent; requirements: RequirementRef[] }) {
  return (
    <div className="space-y-4">
      <SummarySection content={content} />
      <ErDiagramSection content={content} />
      <Section title="Entities">
        <ul className="space-y-3">
          {content.entities.map((entity) => (
            <EntityCard key={entity.name} entity={entity} requirements={requirements} />
          ))}
        </ul>
      </Section>
    </div>
  );
}
