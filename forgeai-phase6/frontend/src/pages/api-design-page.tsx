import { Pencil, Plus, Waypoints } from "lucide-react";
import { useParams } from "react-router";

import { ConventionsEditor, EndpointEditor, SchemaEditor } from "@/components/design/api-editors";
import { ApiView, ConventionsSection, EndpointItem, EndpointsSection, SchemaCard, SchemasSection } from "@/components/design/api-view";
import { DesignStagePage, type StageSectionsProps } from "@/components/design/design-stage-page";
import { Button } from "@/components/ui/button";
import type { ApiDesignContent, ApiDesignOverview } from "@/lib/api";

export function ApiDesignPage() {
  const { projectId = "" } = useParams();
  return (
    <DesignStagePage<ApiDesignContent, ApiDesignOverview>
      stage="api-design"
      title="API design"
      description="Endpoints, request and response contracts and the error model, designed from the approved data model."
      icon={Waypoints}
      emptyText="The database design is approved. Generate the API, then review the endpoints and schemas, edit them and approve it."
      busyText="Designing the API…"
      previousStage={{ label: "Go to database design", path: `/projects/${projectId}/database-design` }}
      exportTitle="OpenAPI document"
      exportDescription="An OpenAPI 3.1 description of the endpoints, schemas and errors, for tools such as Swagger UI or client generators."
      sections={(props) => <ApiSections {...props} />}
      revision={(content, overview) => <ApiView content={content} requirements={overview.requirements} />}
    />
  );
}

function ApiSections({ overview, design, editable, editing, setEditing, save }: StageSectionsProps<ApiDesignContent, ApiDesignOverview>) {
  const content = design.content;
  const canEdit = editable && editing === null;
  const close = () => setEditing(null);

  return (
    <>
      {editing === "conventions" ? (
        <ConventionsEditor content={content} onSave={save} onCancel={close} />
      ) : (
        <ConventionsSection
          content={content}
          action={
            canEdit ? (
              <Button variant="ghost" size="sm" onClick={() => setEditing("conventions")}>
                <Pencil />
                Edit
              </Button>
            ) : undefined
          }
        />
      )}

      {editing === "endpoint:new" && (
        <EndpointEditor content={content} index={null} requirements={overview.requirements} database={overview.database} onSave={save} onCancel={close} />
      )}
      <EndpointsSection
        content={content}
        requirements={overview.requirements}
        action={
          canEdit ? (
            <Button variant="outline" size="sm" onClick={() => setEditing("endpoint:new")}>
              <Plus />
              Add endpoint
            </Button>
          ) : undefined
        }
        item={(endpoint, index) =>
          editing === `endpoint:${index}` ? (
            <li key={`${endpoint.method} ${endpoint.path}`}>
              <EndpointEditor
                content={content}
                index={index}
                requirements={overview.requirements}
                database={overview.database}
                onSave={save}
                onCancel={close}
              />
            </li>
          ) : (
            <EndpointItem
              key={`${endpoint.method} ${endpoint.path}`}
              endpoint={endpoint}
              basePath={content.basePath}
              requirements={overview.requirements}
              action={
                canEdit ? (
                  <Button variant="outline" size="sm" onClick={() => setEditing(`endpoint:${index}`)}>
                    <Pencil />
                    Edit endpoint
                  </Button>
                ) : undefined
              }
            />
          )
        }
      />

      {editing === "schema:new" && (
        <SchemaEditor content={content} index={null} database={overview.database} onSave={save} onCancel={close} />
      )}
      <SchemasSection
        content={content}
        action={
          canEdit ? (
            <Button variant="outline" size="sm" onClick={() => setEditing("schema:new")}>
              <Plus />
              Add schema
            </Button>
          ) : undefined
        }
        item={(schema, index) =>
          editing === `schema:${index}` ? (
            <li key={schema.name} className="lg:col-span-2">
              <SchemaEditor content={content} index={index} database={overview.database} onSave={save} onCancel={close} />
            </li>
          ) : (
            <SchemaCard
              key={schema.name}
              schema={schema}
              action={
                canEdit ? (
                  <Button variant="ghost" size="sm" onClick={() => setEditing(`schema:${index}`)} aria-label={`Edit ${schema.name}`}>
                    <Pencil />
                  </Button>
                ) : undefined
              }
            />
          )
        }
      />
    </>
  );
}
