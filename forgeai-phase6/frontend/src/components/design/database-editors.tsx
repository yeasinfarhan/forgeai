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
  type ColumnType,
  type DatabaseDesignContent,
  type DbColumn,
  type DbEntity,
  type OnDelete,
  type RequirementRef,
} from "@/lib/api";

const IDENTIFIER = /^[a-z][a-z0-9_]{0,62}$/;
const COLUMN_TYPES: ColumnType[] = ["UUID", "TEXT", "VARCHAR", "INTEGER", "BIGINT", "DECIMAL", "BOOLEAN", "DATE", "TIMESTAMP", "JSON"];
const ON_DELETE: OnDelete[] = ["CASCADE", "RESTRICT", "SET_NULL"];

const DEFAULT_HINT: Record<ColumnType, string> = {
  UUID: "random",
  TEXT: "any text",
  VARCHAR: "any text",
  INTEGER: "a whole number",
  BIGINT: "a whole number",
  DECIMAL: "a number",
  BOOLEAN: "true or false",
  DATE: "today",
  TIMESTAMP: "now",
  JSON: "{} or []",
};

export function SummaryEditor({
  content,
  onSave,
  onCancel,
}: {
  content: DatabaseDesignContent;
  onSave: (content: DatabaseDesignContent) => Promise<void>;
  onCancel: () => void;
}) {
  const [summary, setSummary] = useState(content.summary);
  return (
    <EditorFrame
      title="Summary"
      onCancel={onCancel}
      validate={() => (summary.trim() ? [] : ["Describe how the data is organised."])}
      submit={() => onSave({ ...content, summary: summary.trim() })}
    >
      <Field id="db-summary" label="Summary">
        <Textarea id="db-summary" rows={4} maxLength={4_000} value={summary} onChange={(e) => setSummary(e.target.value)} autoFocus />
      </Field>
    </EditorFrame>
  );
}

interface ColumnDraft {
  key: number;
  /** The name when editing started, so references from other entities can follow a rename. */
  originalName: string | null;
  name: string;
  type: ColumnType;
  length: string;
  precision: string;
  scale: string;
  nullable: boolean;
  primaryKey: boolean;
  unique: boolean;
  defaultValue: string;
  fkEntity: string;
  fkColumn: string;
  onDelete: OnDelete;
  /** Comma separated. */
  allowedValues: string;
  description: string;
}

interface IndexDraft {
  key: number;
  /** Keys of the columns, so renaming a column keeps the index. */
  columns: number[];
  unique: boolean;
}

/**
 * Edits one entity (or adds a new one when {@code index} is null). Renaming the entity or a
 * column also updates foreign keys elsewhere that point at it.
 */
export function EntityEditor({
  content,
  index,
  requirements,
  onSave,
  onCancel,
}: {
  content: DatabaseDesignContent;
  index: number | null;
  requirements: RequirementRef[];
  onSave: (content: DatabaseDesignContent) => Promise<void>;
  onCancel: () => void;
}) {
  const newKey = useKeys();
  const original: DbEntity | null = index === null ? null : content.entities[index];
  const [name, setName] = useState(original?.name ?? "");
  const [description, setDescription] = useState(original?.description ?? "");
  const [codes, setCodes] = useState<string[]>(original?.requirements ?? []);
  const [columns, setColumns] = useState<ColumnDraft[]>(() =>
    original
      ? original.columns.map((c) => toDraft(c, newKey()))
      : [
          {
            ...toDraft(
              {
                name: "id",
                type: "UUID",
                length: null,
                precision: null,
                scale: null,
                nullable: false,
                primaryKey: true,
                unique: false,
                defaultValue: "random",
                references: null,
                allowedValues: [],
                description: null,
              },
              newKey(),
            ),
            originalName: null,
          },
        ],
  );
  const [indexes, setIndexes] = useState<IndexDraft[]>(() =>
    (original?.indexes ?? []).map((i) => ({
      key: newKey(),
      unique: i.unique,
      columns: i.columns.flatMap((n) => {
        const column = columns.find((c) => c.name === n);
        return column ? [column.key] : [];
      }),
    })),
  );
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  const update = (key: number, change: Partial<ColumnDraft>) =>
    setColumns((list) => list.map((c) => (c.key === key ? { ...c, ...change } : c)));

  const otherEntities = content.entities.filter((_, i) => i !== index);

  function validate() {
    const problems: string[] = [];
    const entityName = name.trim();
    if (!IDENTIFIER.test(entityName)) problems.push("The entity name must be lower-case snake_case, e.g. invoice_lines.");
    else if (otherEntities.some((e) => e.name === entityName)) problems.push(`There already is an entity called ${entityName}.`);
    if (columns.length === 0) problems.push("Keep at least one column.");
    const seen = new Set<string>();
    columns.forEach((c, i) => {
      const columnName = c.name.trim() || `column ${i + 1}`;
      if (!IDENTIFIER.test(c.name.trim())) problems.push(`${columnName}: the name must be lower-case snake_case.`);
      else if (seen.has(c.name.trim())) problems.push(`Two columns are called ${c.name.trim()}.`);
      seen.add(c.name.trim());
      if (c.type === "VARCHAR" && !positive(c.length)) problems.push(`${columnName} is VARCHAR and needs a length.`);
      if (c.type === "DECIMAL" && !positive(c.precision)) problems.push(`${columnName} is DECIMAL and needs a precision.`);
      if ((c.fkEntity === "") !== (c.fkColumn === "")) problems.push(`${columnName}: choose both the entity and the column it references.`);
    });
    return problems;
  }

  function submit() {
    const entityName = name.trim();
    const oldEntityName = original?.name ?? null;
    const renamedColumns = new Map<string, string>();
    columns.forEach((c) => {
      if (c.originalName && c.originalName !== c.name.trim()) renamedColumns.set(c.originalName, c.name.trim());
    });
    const nameByKey = new Map(columns.map((c) => [c.key, c.name.trim()]));

    const entity: DbEntity = {
      name: entityName,
      description: description.trim() || null,
      requirements: codes,
      columns: columns.map((c) => fromDraft(c, oldEntityName, entityName, renamedColumns)),
      indexes: indexes
        .map((i) => ({ unique: i.unique, columns: i.columns.flatMap((k) => (nameByKey.has(k) ? [nameByKey.get(k)!] : [])) }))
        .filter((i) => i.columns.length > 0),
    };

    // Foreign keys elsewhere follow renames of this entity and its columns.
    const follow = (column: DbColumn): DbColumn => {
      const fk = column.references;
      if (!fk || oldEntityName === null || fk.entity !== oldEntityName) return column;
      return { ...column, references: { ...fk, entity: entityName, column: renamedColumns.get(fk.column) ?? fk.column } };
    };
    const entities =
      index === null
        ? [...content.entities, entity]
        : content.entities.map((e, i) => (i === index ? entity : { ...e, columns: e.columns.map(follow) }));
    return onSave({ ...content, entities });
  }

  async function remove() {
    if (index === null) return;
    setDeleteError(null);
    setDeleting(true);
    try {
      await onSave({ ...content, entities: content.entities.filter((_, i) => i !== index) });
    } catch (error) {
      setDeleteError(errorMessage(error));
      setDeleting(false);
    }
  }

  return (
    <EditorFrame title={original ? `Entity ${original.name}` : "New entity"} validate={validate} submit={submit} onCancel={onCancel}>
      <div className="grid gap-3 sm:grid-cols-[16rem_1fr]">
        <Field id="entity-name" label="Name" hint="snake_case, plural">
          <Input id="entity-name" maxLength={63} value={name} onChange={(e) => setName(e.target.value)} className="font-mono" autoFocus />
        </Field>
        <Field id="entity-description" label="Description">
          <Input id="entity-description" maxLength={1_000} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
      </div>

      <div className="space-y-2">
        <p className="text-sm font-medium">Columns</p>
        <ul className="space-y-3">
          {columns.map((c, i) => {
            const targetColumns =
              c.fkEntity === ""
                ? []
                : c.fkEntity === (original?.name ?? name.trim())
                  ? columns.map((x) => x.name.trim()).filter(Boolean)
                  : (content.entities.find((e) => e.name === c.fkEntity)?.columns.map((x) => x.name) ?? []);
            return (
              <ItemCard
                key={c.key}
                heading={c.name.trim() || `Column ${i + 1}`}
                onRemove={() => {
                  setColumns((list) => list.filter((x) => x.key !== c.key));
                  setIndexes((list) => list.map((x) => ({ ...x, columns: x.columns.filter((k) => k !== c.key) })));
                }}
              >
                <div className="grid gap-3 sm:grid-cols-[1fr_9rem_6rem_6rem_6rem]">
                  <Field id={`col-name-${c.key}`} label="Name">
                    <Input id={`col-name-${c.key}`} maxLength={63} className="font-mono" value={c.name} onChange={(e) => update(c.key, { name: e.target.value })} />
                  </Field>
                  <Field id={`col-type-${c.key}`} label="Type">
                    <select id={`col-type-${c.key}`} className={selectClass} value={c.type} onChange={(e) => update(c.key, { type: e.target.value as ColumnType })}>
                      {COLUMN_TYPES.map((t) => (
                        <option key={t} value={t}>
                          {t}
                        </option>
                      ))}
                    </select>
                  </Field>
                  {c.type === "VARCHAR" && (
                    <Field id={`col-length-${c.key}`} label="Length">
                      <Input id={`col-length-${c.key}`} type="number" min={1} value={c.length} onChange={(e) => update(c.key, { length: e.target.value })} />
                    </Field>
                  )}
                  {c.type === "DECIMAL" && (
                    <>
                      <Field id={`col-precision-${c.key}`} label="Precision">
                        <Input id={`col-precision-${c.key}`} type="number" min={1} value={c.precision} onChange={(e) => update(c.key, { precision: e.target.value })} />
                      </Field>
                      <Field id={`col-scale-${c.key}`} label="Scale">
                        <Input id={`col-scale-${c.key}`} type="number" min={0} value={c.scale} onChange={(e) => update(c.key, { scale: e.target.value })} />
                      </Field>
                    </>
                  )}
                </div>
                <div className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
                  <Check label="Primary key" checked={c.primaryKey} onChange={(v) => update(c.key, { primaryKey: v, nullable: v ? false : c.nullable })} />
                  <Check label="Nullable" checked={c.nullable} disabled={c.primaryKey} onChange={(v) => update(c.key, { nullable: v })} />
                  <Check label="Unique" checked={c.unique} disabled={c.primaryKey} onChange={(v) => update(c.key, { unique: v })} />
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <Field id={`col-default-${c.key}`} label="Default" hint={`Optional: ${DEFAULT_HINT[c.type]}`}>
                    <Input id={`col-default-${c.key}`} maxLength={200} value={c.defaultValue} onChange={(e) => update(c.key, { defaultValue: e.target.value })} />
                  </Field>
                  {(c.type === "TEXT" || c.type === "VARCHAR") && (
                    <Field id={`col-allowed-${c.key}`} label="Allowed values" hint="Comma separated, e.g. DRAFT, SENT, PAID">
                      <Input id={`col-allowed-${c.key}`} value={c.allowedValues} onChange={(e) => update(c.key, { allowedValues: e.target.value })} />
                    </Field>
                  )}
                </div>
                <div className="grid gap-3 sm:grid-cols-3">
                  <Field id={`col-fk-entity-${c.key}`} label="References entity">
                    <select
                      id={`col-fk-entity-${c.key}`}
                      className={selectClass}
                      value={c.fkEntity}
                      onChange={(e) => update(c.key, { fkEntity: e.target.value, fkColumn: "" })}
                    >
                      <option value="">— none —</option>
                      {(original ? content.entities : [...content.entities, { name: name.trim() } as DbEntity])
                        .filter((e) => e.name)
                        .map((e) => (
                          <option key={e.name} value={e.name}>
                            {e.name}
                          </option>
                        ))}
                    </select>
                  </Field>
                  <Field id={`col-fk-column-${c.key}`} label="Column">
                    <select
                      id={`col-fk-column-${c.key}`}
                      className={selectClass}
                      value={c.fkColumn}
                      disabled={c.fkEntity === ""}
                      onChange={(e) => update(c.key, { fkColumn: e.target.value })}
                    >
                      <option value="">—</option>
                      {targetColumns.map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <Field id={`col-fk-delete-${c.key}`} label="On delete">
                    <select
                      id={`col-fk-delete-${c.key}`}
                      className={selectClass}
                      value={c.onDelete}
                      disabled={c.fkEntity === ""}
                      onChange={(e) => update(c.key, { onDelete: e.target.value as OnDelete })}
                    >
                      {ON_DELETE.map((o) => (
                        <option key={o} value={o}>
                          {o.replace("_", " ")}
                        </option>
                      ))}
                    </select>
                  </Field>
                </div>
                <Field id={`col-description-${c.key}`} label="Description">
                  <Input id={`col-description-${c.key}`} maxLength={500} value={c.description} onChange={(e) => update(c.key, { description: e.target.value })} />
                </Field>
              </ItemCard>
            );
          })}
        </ul>
        {columns.length < 80 && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() =>
              setColumns((list) => [
                ...list,
                {
                  ...toDraft(
                    {
                      name: "",
                      type: "TEXT",
                      length: null,
                      precision: null,
                      scale: null,
                      nullable: true,
                      primaryKey: false,
                      unique: false,
                      defaultValue: null,
                      references: null,
                      allowedValues: [],
                      description: null,
                    },
                    newKey(),
                  ),
                  originalName: null,
                },
              ])
            }
          >
            <Plus />
            Add column
          </Button>
        )}
      </div>

      <div className="space-y-2">
        <p className="text-sm font-medium">Indexes</p>
        {indexes.length === 0 && <p className="text-xs text-muted-foreground">No indexes. Primary keys and unique columns need none.</p>}
        <ul className="space-y-2">
          {indexes.map((idx) => (
            <li key={idx.key} className="flex flex-wrap items-center gap-3 rounded-lg border bg-background/40 p-3">
              <ToggleChips
                label="Columns"
                empty="Add columns first."
                options={columns.filter((c) => c.name.trim()).map((c) => ({ value: c.key, label: c.name.trim() }))}
                selected={idx.columns}
                onToggle={(key) =>
                  setIndexes((list) => list.map((x) => (x.key === idx.key ? { ...x, columns: toggle(x.columns, key) } : x)))
                }
              />
              <Check
                label="Unique"
                checked={idx.unique}
                onChange={(v) => setIndexes((list) => list.map((x) => (x.key === idx.key ? { ...x, unique: v } : x)))}
              />
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="ml-auto text-destructive hover:text-destructive"
                onClick={() => setIndexes((list) => list.filter((x) => x.key !== idx.key))}
              >
                <Trash2 />
                Remove
              </Button>
            </li>
          ))}
        </ul>
        {indexes.length < 30 && (
          <Button type="button" variant="outline" size="sm" onClick={() => setIndexes((list) => [...list, { key: newKey(), columns: [], unique: false }])}>
            <Plus />
            Add index
          </Button>
        )}
      </div>

      <ToggleChips
        label="Requirements served"
        empty="There are no approved requirements."
        options={requirementOptions(requirements, codes)}
        selected={codes}
        onToggle={(code) => setCodes((list) => toggle(list, code))}
      />

      {original && (
        <div className="space-y-2 border-t pt-3">
          <FormError message={deleteError} />
          <Button type="button" variant="ghost" size="sm" className="text-destructive hover:text-destructive" disabled={deleting} onClick={remove}>
            <Trash2 />
            {deleting ? "Deleting…" : `Delete ${original.name}`}
          </Button>
        </div>
      )}
    </EditorFrame>
  );
}

function Check({
  label,
  checked,
  disabled,
  onChange,
}: {
  label: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <label className="inline-flex items-center gap-2 text-sm">
      <input
        type="checkbox"
        className="size-4 accent-[var(--primary)]"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
      />
      {label}
    </label>
  );
}

function toDraft(c: DbColumn, key: number): ColumnDraft {
  return {
    key,
    originalName: c.name,
    name: c.name,
    type: c.type,
    length: c.length?.toString() ?? "",
    precision: c.precision?.toString() ?? "",
    scale: c.scale?.toString() ?? "",
    nullable: c.nullable,
    primaryKey: c.primaryKey,
    unique: c.unique,
    defaultValue: c.defaultValue ?? "",
    fkEntity: c.references?.entity ?? "",
    fkColumn: c.references?.column ?? "",
    onDelete: c.references?.onDelete ?? "RESTRICT",
    allowedValues: c.allowedValues.join(", "),
    description: c.description ?? "",
  };
}

/** A self-reference follows renames of the entity and of its columns. */
function fromDraft(c: ColumnDraft, oldEntityName: string | null, entityName: string, renamedColumns: Map<string, string>): DbColumn {
  const text = c.type === "TEXT" || c.type === "VARCHAR";
  const self = c.fkEntity !== "" && c.fkEntity === oldEntityName;
  const fkEntity = self ? entityName : c.fkEntity;
  const fkColumn = self ? (renamedColumns.get(c.fkColumn) ?? c.fkColumn) : c.fkColumn;
  return {
    name: c.name.trim(),
    type: c.type,
    length: c.type === "VARCHAR" ? Number(c.length) : null,
    precision: c.type === "DECIMAL" ? Number(c.precision) : null,
    scale: c.type === "DECIMAL" && c.scale.trim() !== "" ? Number(c.scale) : null,
    nullable: !c.primaryKey && c.nullable,
    primaryKey: c.primaryKey,
    unique: c.unique && !c.primaryKey,
    defaultValue: c.defaultValue.trim() || null,
    references: fkEntity && fkColumn ? { entity: fkEntity, column: fkColumn, onDelete: c.onDelete } : null,
    allowedValues: text
      ? c.allowedValues
          .split(",")
          .map((v) => v.trim())
          .filter(Boolean)
      : [],
    description: c.description.trim() || null,
  };
}

function positive(value: string) {
  return /^\d+$/.test(value.trim()) && Number(value) > 0;
}
