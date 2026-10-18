import type { ReactNode } from "react";
import { Lock } from "lucide-react";

import { Section } from "@/components/architecture/architecture-view";
import { RequirementChips } from "@/components/design/database-view";
import { Badge } from "@/components/ui/badge";
import type { ApiDesignContent, ApiEndpoint, ApiField, ApiParameter, ApiSchema, HttpMethod, RequirementRef } from "@/lib/api";
import { cn } from "@/lib/utils";

const METHOD_CLASS: Record<HttpMethod, string> = {
  GET: "border-sky-500/40 bg-sky-500/10 text-sky-400",
  POST: "border-emerald-500/40 bg-emerald-500/10 text-emerald-400",
  PUT: "border-amber-500/40 bg-amber-500/10 text-amber-400",
  PATCH: "border-amber-500/40 bg-amber-500/10 text-amber-400",
  DELETE: "border-red-500/40 bg-red-500/10 text-red-400",
};

export function MethodBadge({ method }: { method: HttpMethod }) {
  return (
    <span className={cn("inline-block w-16 rounded border px-1.5 py-0.5 text-center font-mono text-[11px] font-semibold", METHOD_CLASS[method])}>
      {method}
    </span>
  );
}

export function fieldTypeLabel(field: Pick<ApiField, "type" | "ref" | "array">) {
  const base = field.type === "OBJECT" ? (field.ref ?? "object") : field.type.toLowerCase().replace("_", "-");
  return field.array ? `${base}[]` : base;
}

export function ConventionsSection({ content, action }: { content: ApiDesignContent; action?: ReactNode }) {
  return (
    <Section title="Conventions" action={action}>
      <p className="text-sm leading-relaxed whitespace-pre-wrap">{content.summary}</p>
      <dl className="grid gap-3 text-sm sm:grid-cols-[10rem_1fr]">
        <dt className="text-muted-foreground">Base path</dt>
        <dd className="font-mono">{content.basePath}</dd>
        <dt className="text-muted-foreground">Authentication</dt>
        <dd className="whitespace-pre-wrap">{content.authentication}</dd>
        <dt className="text-muted-foreground">Error format</dt>
        <dd className="whitespace-pre-wrap">{content.errorFormat}</dd>
      </dl>
      <div>
        <p className="mb-1 text-xs font-semibold tracking-wider text-muted-foreground uppercase">Error model</p>
        {content.errors.length === 0 ? (
          <p className="text-sm text-muted-foreground italic">No error statuses defined.</p>
        ) : (
          <table className="w-full text-sm">
            <tbody>
              {content.errors.map((error) => (
                <tr key={error.status} className="border-b align-top last:border-0">
                  <td className="w-14 py-1.5 font-mono">{error.status}</td>
                  <td className="w-44 py-1.5 font-mono text-xs text-primary">{error.code}</td>
                  <td className="py-1.5 text-muted-foreground">{error.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </Section>
  );
}

function ParameterTable({ title, parameters }: { title: string; parameters: ApiParameter[] }) {
  if (parameters.length === 0) return null;
  return (
    <div>
      <p className="mb-1 text-xs font-semibold tracking-wider text-muted-foreground uppercase">{title}</p>
      <ul className="space-y-0.5 text-sm">
        {parameters.map((p) => (
          <li key={p.name}>
            <span className="font-mono">{p.name}</span>{" "}
            <span className="font-mono text-xs text-primary">{fieldTypeLabel({ type: p.type, ref: null, array: false })}</span>
            {p.required && <span className="text-xs text-muted-foreground"> · required</span>}
            {p.description && <span className="text-muted-foreground"> — {p.description}</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}

export function EndpointItem({
  endpoint,
  basePath,
  requirements,
  action,
}: {
  endpoint: ApiEndpoint;
  basePath: string;
  requirements: RequirementRef[];
  action?: ReactNode;
}) {
  const response = endpoint.responseSchema
    ? `${endpoint.successStatus} · ${endpoint.responseList ? `list of ${endpoint.responseSchema}` : endpoint.responseSchema}`
    : `${endpoint.successStatus} · no body`;
  return (
    <li className="rounded-lg border bg-background/40">
      <details className="group">
        <summary className="flex cursor-pointer list-none flex-wrap items-center gap-2 p-3">
          <MethodBadge method={endpoint.method} />
          <span className="font-mono text-sm break-all">
            <span className="text-muted-foreground">{basePath === "/" ? "" : basePath}</span>
            {endpoint.path}
          </span>
          {endpoint.authenticated && <Lock className="size-3.5 text-muted-foreground" aria-label="Requires authentication" />}
          <span className="ml-auto text-sm text-muted-foreground">{endpoint.summary}</span>
        </summary>
        <div className="space-y-3 border-t px-4 pt-3 pb-4">
          {endpoint.description && <p className="text-sm whitespace-pre-wrap">{endpoint.description}</p>}
          <ParameterTable title="Path parameters" parameters={endpoint.pathParameters} />
          <ParameterTable title="Query parameters" parameters={endpoint.queryParameters} />
          <dl className="grid gap-x-3 gap-y-1 text-sm sm:grid-cols-[8rem_1fr]">
            <dt className="text-muted-foreground">Request body</dt>
            <dd className="font-mono text-xs">{endpoint.requestSchema ?? "—"}</dd>
            <dt className="text-muted-foreground">Response</dt>
            <dd className="font-mono text-xs">{response}</dd>
            <dt className="text-muted-foreground">Errors</dt>
            <dd className="font-mono text-xs">{endpoint.errorStatuses.length ? endpoint.errorStatuses.join(", ") : "—"}</dd>
            <dt className="text-muted-foreground">Entity</dt>
            <dd className="font-mono text-xs">{endpoint.entity ?? "—"}</dd>
          </dl>
          <RequirementChips codes={endpoint.requirements} requirements={requirements} />
          {action}
        </div>
      </details>
    </li>
  );
}

export function EndpointsSection({
  content,
  requirements,
  action,
  item,
}: {
  content: ApiDesignContent;
  requirements: RequirementRef[];
  action?: ReactNode;
  /** Renders one endpoint (lets the page swap in an editor); defaults to the read-only item. */
  item?: (endpoint: ApiEndpoint, index: number) => ReactNode;
}) {
  const groups = new Map<string, { endpoint: ApiEndpoint; index: number }[]>();
  content.endpoints.forEach((endpoint, index) => {
    const tag = endpoint.entity ?? "general";
    groups.set(tag, [...(groups.get(tag) ?? []), { endpoint, index }]);
  });
  return (
    <Section title="Endpoints" description={`${content.endpoints.length} endpoints, grouped by entity.`} action={action}>
      <div className="space-y-4">
        {[...groups.entries()].map(([tag, endpoints]) => (
          <div key={tag} className="space-y-2">
            <p className="font-mono text-xs text-muted-foreground">{tag}</p>
            <ul className="space-y-2">
              {endpoints.map(({ endpoint, index }) =>
                item ? (
                  item(endpoint, index)
                ) : (
                  <EndpointItem
                    key={`${endpoint.method} ${endpoint.path}`}
                    endpoint={endpoint}
                    basePath={content.basePath}
                    requirements={requirements}
                  />
                ),
              )}
            </ul>
          </div>
        ))}
      </div>
    </Section>
  );
}

export function SchemaCard({ schema, action }: { schema: ApiSchema; action?: ReactNode }) {
  return (
    <li className="space-y-2 rounded-lg border bg-background/40 p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-mono font-medium">{schema.name}</p>
          {schema.description && <p className="text-sm text-muted-foreground">{schema.description}</p>}
        </div>
        <div className="flex items-center gap-2">
          {schema.entity && (
            <Badge variant="outline" className="font-mono">
              {schema.entity}
            </Badge>
          )}
          {action}
        </div>
      </div>
      <table className="w-full text-sm">
        <tbody>
          {schema.fields.map((field) => (
            <tr key={field.name} className="border-b align-top last:border-0">
              <td className="py-1 pr-3 font-mono text-xs">
                {field.name}
                {field.required && <span className="text-destructive">*</span>}
              </td>
              <td className="py-1 pr-3 font-mono text-xs text-primary">{fieldTypeLabel(field)}</td>
              <td className="py-1 text-xs text-muted-foreground">{field.description}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </li>
  );
}

export function SchemasSection({
  content,
  action,
  item,
}: {
  content: ApiDesignContent;
  action?: ReactNode;
  item?: (schema: ApiSchema, index: number) => ReactNode;
}) {
  return (
    <Section title="Schemas" description="Request and response bodies. * marks required fields." action={action}>
      {content.schemas.length === 0 ? (
        <p className="text-sm text-muted-foreground italic">No schemas.</p>
      ) : (
        <ul className="grid gap-3 lg:grid-cols-2">
          {content.schemas.map((schema, index) => (item ? item(schema, index) : <SchemaCard key={schema.name} schema={schema} />))}
        </ul>
      )}
    </Section>
  );
}

/** The whole API, read-only (used for earlier revisions). */
export function ApiView({ content, requirements }: { content: ApiDesignContent; requirements: RequirementRef[] }) {
  return (
    <div className="space-y-4">
      <ConventionsSection content={content} />
      <EndpointsSection content={content} requirements={requirements} />
      <SchemasSection content={content} />
    </div>
  );
}
