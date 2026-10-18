import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";

import {
  EditorFrame,
  Field,
  ItemCard,
  ToggleChips,
  requirementOptions,
  selectClass,
  toggle,
  useKeys,
} from "@/components/design/editor-kit";
import { FormError } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  errorMessage,
  type ApiDesignContent,
  type ApiEndpoint,
  type ApiField,
  type ApiParameter,
  type ApiSchema,
  type DatabaseDesignContent,
  type FieldType,
  type HttpMethod,
  type RequirementRef,
} from "@/lib/api";

const METHODS: HttpMethod[] = ["GET", "POST", "PUT", "PATCH", "DELETE"];
const FIELD_TYPES: FieldType[] = ["STRING", "INTEGER", "NUMBER", "BOOLEAN", "UUID", "DATE", "DATE_TIME", "EMAIL", "OBJECT"];
const SCALAR_TYPES = FIELD_TYPES.filter((t) => t !== "OBJECT");
const SUCCESS_STATUSES = [200, 201, 202, 204];
const SCHEMA_NAME = /^[A-Z][A-Za-z0-9]{0,62}$/;
const FIELD_NAME = /^[a-z][A-Za-z0-9_]{0,62}$/;
const PATH = /^\/[A-Za-z0-9/{}_.-]*$/;

function placeholders(path: string): string[] {
  return [...path.matchAll(/\{([^{}]*)\}/g)].map((m) => m[1]);
}

/** Shows an inline "Delete …" action whose failure (e.g. the item is still referenced) is explained. */
function DeleteAction({ label, onDelete }: { label: string; onDelete: () => Promise<void> }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <div className="space-y-2 border-t pt-3">
      <FormError message={error} />
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="text-destructive hover:text-destructive"
        disabled={busy}
        onClick={async () => {
          setError(null);
          setBusy(true);
          try {
            await onDelete();
          } catch (err) {
            setError(errorMessage(err));
            setBusy(false);
          }
        }}
      >
        <Trash2 />
        {busy ? "Deleting…" : label}
      </Button>
    </div>
  );
}

// --- Conventions and error model ---------------------------------------------

interface ErrorDraft {
  key: number;
  status: string;
  code: string;
  description: string;
}

export function ConventionsEditor({
  content,
  onSave,
  onCancel,
}: {
  content: ApiDesignContent;
  onSave: (content: ApiDesignContent) => Promise<void>;
  onCancel: () => void;
}) {
  const newKey = useKeys();
  const [summary, setSummary] = useState(content.summary);
  const [basePath, setBasePath] = useState(content.basePath);
  const [authentication, setAuthentication] = useState(content.authentication);
  const [errorFormat, setErrorFormat] = useState(content.errorFormat);
  const [errors, setErrors] = useState<ErrorDraft[]>(() =>
    content.errors.map((e) => ({ key: newKey(), status: String(e.status), code: e.code, description: e.description })),
  );

  const update = (key: number, change: Partial<ErrorDraft>) =>
    setErrors((list) => list.map((e) => (e.key === key ? { ...e, ...change } : e)));

  function validate() {
    const problems: string[] = [];
    if (!summary.trim()) problems.push("Describe what the API offers.");
    if (!PATH.test(basePath.trim()) || basePath.includes("{")) problems.push("The base path must start with / and contain no placeholders.");
    if (!authentication.trim()) problems.push("Describe how clients authenticate.");
    if (!errorFormat.trim()) problems.push("Describe the error format.");
    const seen = new Set<number>();
    errors.forEach((e, i) => {
      const status = Number(e.status);
      if (!/^\d{3}$/.test(e.status.trim()) || status < 400 || status > 599) problems.push(`Error ${i + 1}: the status must be 400–599.`);
      else if (seen.has(status)) problems.push(`Status ${status} is listed twice.`);
      seen.add(status);
      if (!e.code.trim() || !e.description.trim()) problems.push(`Error ${i + 1} needs a code and a description.`);
    });
    return problems;
  }

  return (
    <EditorFrame
      title="Conventions"
      validate={validate}
      onCancel={onCancel}
      submit={() =>
        onSave({
          ...content,
          summary: summary.trim(),
          basePath: basePath.trim(),
          authentication: authentication.trim(),
          errorFormat: errorFormat.trim(),
          errors: errors.map((e) => ({ status: Number(e.status), code: e.code.trim(), description: e.description.trim() })),
        })
      }
    >
      <Field id="api-summary" label="Summary">
        <Textarea id="api-summary" rows={3} maxLength={4_000} value={summary} onChange={(e) => setSummary(e.target.value)} autoFocus />
      </Field>
      <div className="grid gap-3 sm:grid-cols-[12rem_1fr]">
        <Field id="api-base" label="Base path">
          <Input id="api-base" maxLength={100} className="font-mono" value={basePath} onChange={(e) => setBasePath(e.target.value)} />
        </Field>
        <Field id="api-auth" label="Authentication">
          <Input id="api-auth" maxLength={2_000} value={authentication} onChange={(e) => setAuthentication(e.target.value)} />
        </Field>
      </div>
      <Field id="api-error-format" label="Error format">
        <Textarea id="api-error-format" rows={2} maxLength={2_000} value={errorFormat} onChange={(e) => setErrorFormat(e.target.value)} />
      </Field>
      <div className="space-y-2">
        <p className="text-sm font-medium">Error model</p>
        <ul className="space-y-2">
          {errors.map((e) => (
            <li key={e.key} className="grid items-end gap-2 sm:grid-cols-[5rem_11rem_1fr_auto]">
              <Field id={`err-status-${e.key}`} label="Status">
                <Input id={`err-status-${e.key}`} inputMode="numeric" maxLength={3} value={e.status} onChange={(ev) => update(e.key, { status: ev.target.value })} />
              </Field>
              <Field id={`err-code-${e.key}`} label="Code">
                <Input id={`err-code-${e.key}`} maxLength={60} className="font-mono" value={e.code} onChange={(ev) => update(e.key, { code: ev.target.value })} />
              </Field>
              <Field id={`err-description-${e.key}`} label="When">
                <Input id={`err-description-${e.key}`} maxLength={500} value={e.description} onChange={(ev) => update(e.key, { description: ev.target.value })} />
              </Field>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label="Remove error"
                onClick={() => setErrors((list) => list.filter((x) => x.key !== e.key))}
              >
                <Trash2 />
              </Button>
            </li>
          ))}
        </ul>
        {errors.length < 20 && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setErrors((list) => [...list, { key: newKey(), status: "", code: "", description: "" }])}
          >
            <Plus />
            Add error status
          </Button>
        )}
      </div>
    </EditorFrame>
  );
}

// --- Endpoints -----------------------------------------------------------------

interface ParameterDraft {
  key: number;
  name: string;
  type: FieldType;
  required: boolean;
  description: string;
}

export function EndpointEditor({
  content,
  index,
  requirements,
  database,
  onSave,
  onCancel,
}: {
  content: ApiDesignContent;
  index: number | null;
  requirements: RequirementRef[];
  database: DatabaseDesignContent | null;
  onSave: (content: ApiDesignContent) => Promise<void>;
  onCancel: () => void;
}) {
  const newKey = useKeys();
  const original: ApiEndpoint | null = index === null ? null : content.endpoints[index];
  const [method, setMethod] = useState<HttpMethod>(original?.method ?? "GET");
  const [path, setPath] = useState(original?.path ?? "/");
  const [summary, setSummary] = useState(original?.summary ?? "");
  const [description, setDescription] = useState(original?.description ?? "");
  const [authenticated, setAuthenticated] = useState(original?.authenticated ?? true);
  const [entity, setEntity] = useState(original?.entity ?? "");
  const [requestSchema, setRequestSchema] = useState(original?.requestSchema ?? "");
  const [responseSchema, setResponseSchema] = useState(original?.responseSchema ?? "");
  const [responseList, setResponseList] = useState(original?.responseList ?? false);
  const [successStatus, setSuccessStatus] = useState(original?.successStatus ?? 200);
  /** Path parameter details by name; rows follow the {placeholders} in the path. */
  const [pathDetails, setPathDetails] = useState<Record<string, { type: FieldType; description: string }>>(() =>
    Object.fromEntries((original?.pathParameters ?? []).map((p) => [p.name, { type: p.type, description: p.description ?? "" }])),
  );
  const [query, setQuery] = useState<ParameterDraft[]>(() =>
    (original?.queryParameters ?? []).map((p) => ({ ...p, key: newKey(), description: p.description ?? "" })),
  );
  const [errorStatuses, setErrorStatuses] = useState<number[]>(original?.errorStatuses ?? []);
  const [codes, setCodes] = useState<string[]>(original?.requirements ?? []);

  const names = [...new Set(placeholders(path))];
  const detail = (name: string) => pathDetails[name] ?? { type: /id$/i.test(name) ? "UUID" : "STRING", description: "" };
  const hasBody = method !== "GET" && method !== "DELETE";
  const schemaNames = content.schemas.map((s) => s.name);

  const updateQuery = (key: number, change: Partial<ParameterDraft>) =>
    setQuery((list) => list.map((q) => (q.key === key ? { ...q, ...change } : q)));

  function validate() {
    const problems: string[] = [];
    const p = path.trim();
    if (!PATH.test(p)) problems.push("The path must start with / and contain only letters, digits, /, {}, _, . and -.");
    names.forEach((n) => {
      if (!FIELD_NAME.test(n)) problems.push(`The path parameter {${n}} must be camelCase.`);
    });
    const route = `${method} ${p.replace(/\{[^{}]*\}/g, "{}").replace(/\/+$/, "") || "/"}`;
    if (
      content.endpoints.some(
        (e, i) => i !== index && `${e.method} ${e.path.replace(/\{[^{}]*\}/g, "{}")}` === route,
      )
    ) {
      problems.push(`${method} ${p} already exists.`);
    }
    if (!summary.trim()) problems.push("Give the endpoint a summary.");
    const seen = new Set<string>();
    query.forEach((q, i) => {
      if (!FIELD_NAME.test(q.name.trim())) problems.push(`Query parameter ${i + 1}: the name must be camelCase.`);
      else if (seen.has(q.name.trim()) || names.includes(q.name.trim())) problems.push(`The parameter ${q.name.trim()} is defined twice.`);
      seen.add(q.name.trim());
    });
    if (successStatus === 204 && responseSchema) problems.push("A 204 response has no body; choose no response schema.");
    return problems;
  }

  function submit() {
    const endpoint: ApiEndpoint = {
      method,
      path: path.trim(),
      summary: summary.trim(),
      description: description.trim() || null,
      authenticated,
      entity: entity || null,
      requestSchema: hasBody && requestSchema ? requestSchema : null,
      responseSchema: responseSchema || null,
      responseList: Boolean(responseSchema) && responseList,
      successStatus,
      pathParameters: names.map<ApiParameter>((name) => ({
        name,
        type: detail(name).type,
        required: true,
        description: detail(name).description.trim() || null,
      })),
      queryParameters: query.map<ApiParameter>((q) => ({
        name: q.name.trim(),
        type: q.type,
        required: q.required,
        description: q.description.trim() || null,
      })),
      errorStatuses: [...errorStatuses].sort((a, b) => a - b),
      requirements: codes,
    };
    const endpoints = index === null ? [...content.endpoints, endpoint] : content.endpoints.map((e, i) => (i === index ? endpoint : e));
    return onSave({ ...content, endpoints });
  }

  return (
    <EditorFrame title={original ? `${original.method} ${original.path}` : "New endpoint"} validate={validate} submit={submit} onCancel={onCancel}>
      <div className="grid gap-3 sm:grid-cols-[8rem_1fr]">
        <Field id="ep-method" label="Method">
          <select id="ep-method" className={selectClass} value={method} onChange={(e) => setMethod(e.target.value as HttpMethod)}>
            {METHODS.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </Field>
        <Field id="ep-path" label="Path" hint={`Relative to ${content.basePath}; placeholders like {invoiceId}`}>
          <Input id="ep-path" maxLength={200} className="font-mono" value={path} onChange={(e) => setPath(e.target.value)} autoFocus />
        </Field>
      </div>
      <Field id="ep-summary" label="Summary">
        <Input id="ep-summary" maxLength={200} value={summary} onChange={(e) => setSummary(e.target.value)} />
      </Field>
      <Field id="ep-description" label="Description">
        <Textarea id="ep-description" rows={2} maxLength={2_000} value={description} onChange={(e) => setDescription(e.target.value)} />
      </Field>
      <div className="grid gap-3 sm:grid-cols-3">
        <Field id="ep-entity" label="Entity">
          <select id="ep-entity" className={selectClass} value={entity} onChange={(e) => setEntity(e.target.value)}>
            <option value="">— none —</option>
            {(database?.entities ?? []).map((e) => (
              <option key={e.name} value={e.name}>
                {e.name}
              </option>
            ))}
            {entity && !database?.entities.some((e) => e.name === entity) && <option value={entity}>{entity}</option>}
          </select>
        </Field>
        <Field id="ep-request" label="Request body">
          <select
            id="ep-request"
            className={selectClass}
            value={hasBody ? requestSchema : ""}
            disabled={!hasBody}
            onChange={(e) => setRequestSchema(e.target.value)}
          >
            <option value="">— none —</option>
            {schemaNames.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </Field>
        <Field id="ep-success" label="Success status">
          <select id="ep-success" className={selectClass} value={successStatus} onChange={(e) => setSuccessStatus(Number(e.target.value))}>
            {SUCCESS_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div className="grid items-end gap-3 sm:grid-cols-[1fr_auto]">
        <Field id="ep-response" label="Response body">
          <select
            id="ep-response"
            className={selectClass}
            value={successStatus === 204 ? "" : responseSchema}
            disabled={successStatus === 204}
            onChange={(e) => setResponseSchema(e.target.value)}
          >
            <option value="">— none —</option>
            {schemaNames.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </Field>
        <div className="flex gap-4 pb-2 text-sm">
          <label className="inline-flex items-center gap-2">
            <input type="checkbox" className="size-4" checked={responseList} disabled={!responseSchema} onChange={(e) => setResponseList(e.target.checked)} />
            Returns a list
          </label>
          <label className="inline-flex items-center gap-2">
            <input type="checkbox" className="size-4" checked={authenticated} onChange={(e) => setAuthenticated(e.target.checked)} />
            Requires sign-in
          </label>
        </div>
      </div>

      {names.length > 0 && (
        <div className="space-y-2">
          <p className="text-sm font-medium">Path parameters</p>
          {names.map((name) => (
            <div key={name} className="grid items-end gap-2 sm:grid-cols-[10rem_9rem_1fr]">
              <p className="pb-2 font-mono text-sm">{`{${name}}`}</p>
              <Field id={`pp-type-${name}`} label="Type">
                <select
                  id={`pp-type-${name}`}
                  className={selectClass}
                  value={detail(name).type}
                  onChange={(e) => setPathDetails((d) => ({ ...d, [name]: { ...detail(name), type: e.target.value as FieldType } }))}
                >
                  {SCALAR_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </Field>
              <Field id={`pp-description-${name}`} label="Description">
                <Input
                  id={`pp-description-${name}`}
                  maxLength={300}
                  value={detail(name).description}
                  onChange={(e) => setPathDetails((d) => ({ ...d, [name]: { ...detail(name), description: e.target.value } }))}
                />
              </Field>
            </div>
          ))}
        </div>
      )}

      <div className="space-y-2">
        <p className="text-sm font-medium">Query parameters</p>
        <ul className="space-y-2">
          {query.map((q) => (
            <li key={q.key} className="grid items-end gap-2 sm:grid-cols-[10rem_9rem_auto_1fr_auto]">
              <Field id={`qp-name-${q.key}`} label="Name">
                <Input id={`qp-name-${q.key}`} maxLength={63} className="font-mono" value={q.name} onChange={(e) => updateQuery(q.key, { name: e.target.value })} />
              </Field>
              <Field id={`qp-type-${q.key}`} label="Type">
                <select id={`qp-type-${q.key}`} className={selectClass} value={q.type} onChange={(e) => updateQuery(q.key, { type: e.target.value as FieldType })}>
                  {SCALAR_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </Field>
              <label className="inline-flex items-center gap-2 pb-2 text-sm">
                <input type="checkbox" className="size-4" checked={q.required} onChange={(e) => updateQuery(q.key, { required: e.target.checked })} />
                Required
              </label>
              <Field id={`qp-description-${q.key}`} label="Description">
                <Input id={`qp-description-${q.key}`} maxLength={300} value={q.description} onChange={(e) => updateQuery(q.key, { description: e.target.value })} />
              </Field>
              <Button type="button" variant="ghost" size="icon" aria-label="Remove parameter" onClick={() => setQuery((list) => list.filter((x) => x.key !== q.key))}>
                <Trash2 />
              </Button>
            </li>
          ))}
        </ul>
        {query.length < 15 && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setQuery((list) => [...list, { key: newKey(), name: "", type: "STRING", required: false, description: "" }])}
          >
            <Plus />
            Add query parameter
          </Button>
        )}
      </div>

      <ToggleChips
        label="Error statuses"
        empty="The error model has no statuses yet; add them under Conventions."
        options={content.errors.map((e) => ({ value: e.status, label: String(e.status), title: e.description }))}
        selected={errorStatuses}
        onToggle={(status) => setErrorStatuses((list) => toggle(list, status))}
      />
      <ToggleChips
        label="Requirements served"
        empty="There are no approved requirements."
        options={requirementOptions(requirements, codes)}
        selected={codes}
        onToggle={(code) => setCodes((list) => toggle(list, code))}
      />

      {original && (
        <DeleteAction
          label="Delete endpoint"
          onDelete={() => onSave({ ...content, endpoints: content.endpoints.filter((_, i) => i !== index) })}
        />
      )}
    </EditorFrame>
  );
}

// --- Schemas -------------------------------------------------------------------

interface FieldDraft {
  key: number;
  name: string;
  type: FieldType;
  ref: string;
  array: boolean;
  required: boolean;
  description: string;
}

/** Edits one schema (or adds one when {@code index} is null). Renaming it updates every reference. */
export function SchemaEditor({
  content,
  index,
  database,
  onSave,
  onCancel,
}: {
  content: ApiDesignContent;
  index: number | null;
  database: DatabaseDesignContent | null;
  onSave: (content: ApiDesignContent) => Promise<void>;
  onCancel: () => void;
}) {
  const newKey = useKeys();
  const original: ApiSchema | null = index === null ? null : content.schemas[index];
  const [name, setName] = useState(original?.name ?? "");
  const [description, setDescription] = useState(original?.description ?? "");
  const [entity, setEntity] = useState(original?.entity ?? "");
  const [fields, setFields] = useState<FieldDraft[]>(() =>
    (original?.fields ?? []).map((f) => ({ ...f, key: newKey(), ref: f.ref ?? "", description: f.description ?? "" })),
  );

  const update = (key: number, change: Partial<FieldDraft>) =>
    setFields((list) => list.map((f) => (f.key === key ? { ...f, ...change } : f)));
  const otherSchemas = content.schemas.filter((_, i) => i !== index).map((s) => s.name);
  const refOptions = [...otherSchemas, ...(name.trim() ? [name.trim()] : [])];

  function validate() {
    const problems: string[] = [];
    const schemaName = name.trim();
    if (!SCHEMA_NAME.test(schemaName)) problems.push("The schema name must be PascalCase, e.g. InvoiceResponse.");
    else if (otherSchemas.includes(schemaName)) problems.push(`There already is a schema called ${schemaName}.`);
    if (fields.length === 0) problems.push("Add at least one field.");
    const seen = new Set<string>();
    fields.forEach((f, i) => {
      if (!FIELD_NAME.test(f.name.trim())) problems.push(`Field ${i + 1}: the name must be camelCase.`);
      else if (seen.has(f.name.trim())) problems.push(`Two fields are called ${f.name.trim()}.`);
      seen.add(f.name.trim());
      if (f.type === "OBJECT" && !f.ref) problems.push(`${f.name.trim() || `Field ${i + 1}`} is an object: choose its schema.`);
    });
    return problems;
  }

  function submit() {
    const schemaName = name.trim();
    const oldName = original?.name ?? null;
    const rename = (ref: string | null) => (ref !== null && ref === oldName ? schemaName : ref);
    const schema: ApiSchema = {
      name: schemaName,
      description: description.trim() || null,
      entity: entity || null,
      fields: fields.map<ApiField>((f) => ({
        name: f.name.trim(),
        type: f.type,
        ref: f.type === "OBJECT" ? rename(f.ref) : null,
        array: f.array,
        required: f.required,
        description: f.description.trim() || null,
      })),
    };
    const schemas =
      index === null
        ? [...content.schemas, schema]
        : content.schemas.map((s, i) =>
            i === index ? schema : { ...s, fields: s.fields.map((f) => ({ ...f, ref: rename(f.ref) })) },
          );
    const endpoints = content.endpoints.map((e) => ({
      ...e,
      requestSchema: rename(e.requestSchema),
      responseSchema: rename(e.responseSchema),
    }));
    return onSave({ ...content, schemas, endpoints });
  }

  return (
    <EditorFrame title={original ? `Schema ${original.name}` : "New schema"} validate={validate} submit={submit} onCancel={onCancel}>
      <div className="grid gap-3 sm:grid-cols-[1fr_12rem]">
        <Field id="schema-name" label="Name" hint="PascalCase">
          <Input id="schema-name" maxLength={63} className="font-mono" value={name} onChange={(e) => setName(e.target.value)} autoFocus />
        </Field>
        <Field id="schema-entity" label="Represents entity">
          <select id="schema-entity" className={selectClass} value={entity} onChange={(e) => setEntity(e.target.value)}>
            <option value="">— none —</option>
            {(database?.entities ?? []).map((e) => (
              <option key={e.name} value={e.name}>
                {e.name}
              </option>
            ))}
            {entity && !database?.entities.some((e) => e.name === entity) && <option value={entity}>{entity}</option>}
          </select>
        </Field>
      </div>
      <Field id="schema-description" label="Description">
        <Input id="schema-description" maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
      </Field>
      <div className="space-y-2">
        <p className="text-sm font-medium">Fields</p>
        <ul className="space-y-3">
          {fields.map((f, i) => (
            <ItemCard key={f.key} heading={f.name.trim() || `Field ${i + 1}`} onRemove={() => setFields((list) => list.filter((x) => x.key !== f.key))}>
              <div className="grid gap-3 sm:grid-cols-[1fr_9rem_12rem]">
                <Field id={`field-name-${f.key}`} label="Name">
                  <Input id={`field-name-${f.key}`} maxLength={63} className="font-mono" value={f.name} onChange={(e) => update(f.key, { name: e.target.value })} />
                </Field>
                <Field id={`field-type-${f.key}`} label="Type">
                  <select id={`field-type-${f.key}`} className={selectClass} value={f.type} onChange={(e) => update(f.key, { type: e.target.value as FieldType })}>
                    {FIELD_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </Field>
                {f.type === "OBJECT" && (
                  <Field id={`field-ref-${f.key}`} label="Schema">
                    <select id={`field-ref-${f.key}`} className={selectClass} value={f.ref} onChange={(e) => update(f.key, { ref: e.target.value })}>
                      <option value="">—</option>
                      {refOptions.map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                  </Field>
                )}
              </div>
              <div className="flex flex-wrap gap-5 text-sm">
                <label className="inline-flex items-center gap-2">
                  <input type="checkbox" className="size-4" checked={f.array} onChange={(e) => update(f.key, { array: e.target.checked })} />
                  List
                </label>
                <label className="inline-flex items-center gap-2">
                  <input type="checkbox" className="size-4" checked={f.required} onChange={(e) => update(f.key, { required: e.target.checked })} />
                  Required
                </label>
              </div>
              <Field id={`field-description-${f.key}`} label="Description">
                <Input id={`field-description-${f.key}`} maxLength={500} value={f.description} onChange={(e) => update(f.key, { description: e.target.value })} />
              </Field>
            </ItemCard>
          ))}
        </ul>
        {fields.length < 60 && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() =>
              setFields((list) => [...list, { key: newKey(), name: "", type: "STRING", ref: "", array: false, required: false, description: "" }])
            }
          >
            <Plus />
            Add field
          </Button>
        )}
      </div>
      {original && (
        <DeleteAction
          label={`Delete ${original.name}`}
          onDelete={() => onSave({ ...content, schemas: content.schemas.filter((_, i) => i !== index) })}
        />
      )}
    </EditorFrame>
  );
}
