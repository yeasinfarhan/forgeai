import { Database, Pencil, Plus } from "lucide-react";
import { useParams } from "react-router";

import { Section } from "@/components/architecture/architecture-view";
import { EntityEditor, SummaryEditor } from "@/components/design/database-editors";
import { DatabaseView, EntityCard, ErDiagramSection, SummarySection } from "@/components/design/database-view";
import { DesignStagePage, type StageSectionsProps } from "@/components/design/design-stage-page";
import { Button } from "@/components/ui/button";
import type { DatabaseDesignContent, DatabaseDesignOverview } from "@/lib/api";

export function DatabaseDesignPage() {
  const { projectId = "" } = useParams();
  return (
    <DesignStagePage<DatabaseDesignContent, DatabaseDesignOverview>
      stage="database-design"
      title="Database design"
      description="Entities, columns, keys and relationships, designed from the approved architecture and requirements."
      icon={Database}
      emptyText="The architecture is approved. Generate the data model, then review the entities, edit them and approve it."
      busyText="Designing the data model…"
      previousStage={{ label: "Go to architecture", path: `/projects/${projectId}/architecture` }}
      exportTitle="SQL schema"
      exportDescription="A PostgreSQL script that creates the tables, keys, constraints and indexes."
      sections={(props) => <DatabaseSections {...props} />}
      revision={(content, overview) => <DatabaseView content={content} requirements={overview.requirements} />}
    />
  );
}

function DatabaseSections({
  overview,
  design,
  editable,
  editing,
  setEditing,
  save,
}: StageSectionsProps<DatabaseDesignContent, DatabaseDesignOverview>) {
  const content = design.content;
  const canEdit = editable && editing === null;
  const editButton = (section: string, label = "Edit") =>
    canEdit ? (
      <Button variant="ghost" size="sm" onClick={() => setEditing(section)}>
        <Pencil />
        {label}
      </Button>
    ) : undefined;

  return (
    <>
      {editing === "summary" ? (
        <SummaryEditor content={content} onSave={save} onCancel={() => setEditing(null)} />
      ) : (
        <SummarySection content={content} action={editButton("summary")} />
      )}

      <ErDiagramSection content={content} />

      <Section
        title="Entities"
        description={`${content.entities.length} ${content.entities.length === 1 ? "entity" : "entities"}`}
        action={
          canEdit ? (
            <Button variant="outline" size="sm" onClick={() => setEditing("entity:new")}>
              <Plus />
              Add entity
            </Button>
          ) : undefined
        }
      >
        {editing === "entity:new" && (
          <EntityEditor content={content} index={null} requirements={overview.requirements} onSave={save} onCancel={() => setEditing(null)} />
        )}
        <ul className="space-y-3">
          {content.entities.map((entity, index) =>
            editing === `entity:${index}` ? (
              <li key={entity.name}>
                <EntityEditor
                  content={content}
                  index={index}
                  requirements={overview.requirements}
                  onSave={save}
                  onCancel={() => setEditing(null)}
                />
              </li>
            ) : (
              <EntityCard key={entity.name} entity={entity} requirements={overview.requirements} action={editButton(`entity:${index}`)} />
            ),
          )}
        </ul>
      </Section>
    </>
  );
}
