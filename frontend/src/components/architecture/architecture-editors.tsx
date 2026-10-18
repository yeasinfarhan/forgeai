import { useRef, useState, type FormEvent, type ReactNode } from "react";
import { Plus, Trash2 } from "lucide-react";

import { KIND_LABEL, decisionCode } from "@/components/architecture/architecture-view";
import { MermaidDiagram } from "@/components/architecture/mermaid-diagram";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  errorDetails,
  type ArchitectureContent,
  type ArchitectureDecision,
  type ArchitectureFlow,
  type ComponentKind,
  type RequirementRef,
} from "@/lib/api";
import { cn } from "@/lib/utils";

const LIMITS = { components: 30, decisions: 20, flows: 6 };

export interface SectionEditorProps {
  content: ArchitectureContent;
  requirements: RequirementRef[];
  onSave: (content: ArchitectureContent) => Promise<void>;
  onCancel: () => void;
}

// --- Shared pieces -------------------------------------------------------------

/** Form shell for one section: validation messages, server errors, Save / Cancel. */
function EditorFrame({
  title,
  validate,
  submit,
  onCancel,
  children,
}: {
  title: string;
  validate: () => string[];
  submit: () => Promise<void>;
  onCancel: () => void;
  children: ReactNode;
}) {
  const [errors, setErrors] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problems = validate();
    setErrors(problems);
    if (problems.length > 0) return;
    setSaving(true);
    try {
      await submit(); // on success the parent closes the editor
    } catch (error) {
      setErrors(errorDetails(error));
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4 rounded-xl border border-primary/40 bg-card p-5">
      <h2 className="font-semibold">Edit {title.toLowerCase()}</h2>
      {children}
      {errors.length > 0 && (
        <ul role="alert" className="space-y-1 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {errors.map((error, index) => (
            <li key={index}>{error}</li>
          ))}
        </ul>
      )}
      <div className="flex gap-2">
        <Button type="submit" disabled={saving}>
          {saving ? "Saving…" : "Save changes"}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

function Field({ id, label, hint, children }: { id: string; label: string; hint?: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

interface ChipOption<T> {
  value: T;
  label: string;
  title?: string;
}

/** A row of toggle buttons for picking several values (dependencies, requirement codes). */
function ToggleChips<T extends string | number>({
  label,
  options,
  selected,
  onToggle,
  empty,
}: {
  label: string;
  options: ChipOption<T>[];
  selected: T[];
  onToggle: (value: T) => void;
  empty: string;
}) {
  return (
    <fieldset className="space-y-1.5">
      <legend className="text-sm font-medium">{label}</legend>
      {options.length === 0 ? (
        <p className="text-xs text-muted-foreground">{empty}</p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {options.map((option) => {
            const on = selected.includes(option.value);
            return (
              <button
                key={String(option.value)}
                type="button"
                aria-pressed={on}
                title={option.title}
                onClick={() => onToggle(option.value)}
                className={cn(
                  "rounded-md border px-2 py-1 text-xs transition-colors",
                  on ? "border-primary bg-primary/10 text-primary" : "text-muted-foreground hover:bg-accent",
                )}
              >
                {option.label}
              </button>
            );
          })}
        </div>
      )}
    </fieldset>
  );
}

function toggle<T>(list: T[], value: T): T[] {
  return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
}

/** Approved requirements, plus any code already referenced that is not (so it can be removed). */
function requirementOptions(requirements: RequirementRef[], selected: string[]): ChipOption<string>[] {
  const options: ChipOption<string>[] = requirements.map((r) => ({ value: r.code, label: r.code, title: r.title }));
  for (const code of selected) {
    if (!requirements.some((r) => r.code === code)) {
      options.push({ value: code, label: code, title: "Not an approved requirement" });
    }
  }
  return options;
}

function ItemCard({ heading, onRemove, children }: { heading: string; onRemove: () => void; children: ReactNode }) {
  return (
    <li className="space-y-3 rounded-lg border bg-background/40 p-4">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-semibold tracking-wider text-muted-foreground uppercase">{heading}</span>
        <Button type="button" variant="ghost" size="sm" className="text-destructive hover:text-destructive" onClick={onRemove}>
          <Trash2 />
          Remove
        </Button>
      </div>
      {children}
    </li>
  );
}

/** Stable keys for list items that have no id of their own. */
function useKeys() {
  const next = useRef(0);
  return () => next.current++;
}

const selectClass =
  "flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 dark:bg-input/30";

// --- Overview --------------------------------------------------------------------

export function OverviewEditor({ content, onSave, onCancel }: SectionEditorProps) {
  const [style, setStyle] = useState(content.style);
  const [styleRationale, setStyleRationale] = useState(content.styleRationale);
  const [overview, setOverview] = useState(content.overview);

  return (
    <EditorFrame
      title="Overview"
      onCancel={onCancel}
      validate={() =>
        [
          !style.trim() && "Name the architectural style.",
          !styleRationale.trim() && "Explain why the style fits.",
          !overview.trim() && "Describe the system as a whole.",
        ].filter((message): message is string => Boolean(message))
      }
      submit={() => onSave({ ...content, style: style.trim(), styleRationale: styleRationale.trim(), overview: overview.trim() })}
    >
      <Field id="arch-style" label="Architectural style">
        <Input id="arch-style" maxLength={100} value={style} onChange={(e) => setStyle(e.target.value)} autoFocus />
      </Field>
      <Field id="arch-rationale" label="Why this style">
        <Textarea id="arch-rationale" rows={3} maxLength={2_000} value={styleRationale} onChange={(e) => setStyleRationale(e.target.value)} />
      </Field>
      <Field id="arch-overview" label="Overview">
        <Textarea id="arch-overview" rows={6} maxLength={6_000} value={overview} onChange={(e) => setOverview(e.target.value)} />
      </Field>
    </EditorFrame>
  );
}

// --- Components ------------------------------------------------------------------

interface ComponentDraft {
  key: number;
  name: string;
  kind: ComponentKind;
  responsibility: string;
  technology: string;
  /** Keys of the components this one depends on, so renaming a component keeps its links. */
  dependsOn: number[];
  requirements: string[];
}

export function ComponentsEditor({ content, requirements, onSave, onCancel }: SectionEditorProps) {
  const newKey = useKeys();
  const [items, setItems] = useState<ComponentDraft[]>(() => {
    const keyed = content.components.map((c) => ({ component: c, key: newKey() }));
    const keyByName = new Map(keyed.map(({ component, key }) => [component.name.trim().toLowerCase(), key]));
    return keyed.map(({ component, key }) => ({
      key,
      name: component.name,
      kind: component.kind,
      responsibility: component.responsibility,
      technology: component.technology ?? "",
      dependsOn: component.dependsOn.flatMap((name) => {
        const target = keyByName.get(name.trim().toLowerCase());
        return target === undefined ? [] : [target];
      }),
      requirements: component.requirements,
    }));
  });

  const update = (key: number, change: Partial<ComponentDraft>) =>
    setItems((list) => list.map((item) => (item.key === key ? { ...item, ...change } : item)));

  const remove = (key: number) =>
    setItems((list) =>
      list.filter((item) => item.key !== key).map((item) => ({ ...item, dependsOn: item.dependsOn.filter((k) => k !== key) })),
    );

  function validate() {
    const problems: string[] = [];
    if (items.length === 0) problems.push("Keep at least one component.");
    const seen = new Set<string>();
    items.forEach((item, index) => {
      const name = item.name.trim();
      if (!name) problems.push(`Component ${index + 1} needs a name.`);
      else if (seen.has(name.toLowerCase())) problems.push(`Two components are called "${name}".`);
      seen.add(name.toLowerCase());
      if (!item.responsibility.trim()) problems.push(`Describe what ${name || `component ${index + 1}`} is responsible for.`);
    });
    return problems;
  }

  function submit() {
    const nameByKey = new Map(items.map((item) => [item.key, item.name.trim()]));
    return onSave({
      ...content,
      components: items.map((item) => ({
        name: item.name.trim(),
        kind: item.kind,
        responsibility: item.responsibility.trim(),
        technology: item.technology.trim() || null,
        dependsOn: item.dependsOn.flatMap((key) => {
          const name = nameByKey.get(key);
          return name ? [name] : [];
        }),
        requirements: item.requirements,
      })),
    });
  }

  return (
    <EditorFrame title="Components" validate={validate} submit={submit} onCancel={onCancel}>
      <ul className="space-y-3">
        {items.map((item, index) => (
          <ItemCard key={item.key} heading={item.name.trim() || `Component ${index + 1}`} onRemove={() => remove(item.key)}>
            <div className="grid gap-3 sm:grid-cols-[1fr_11rem]">
              <Field id={`comp-name-${item.key}`} label="Name">
                <Input
                  id={`comp-name-${item.key}`}
                  maxLength={80}
                  value={item.name}
                  onChange={(e) => update(item.key, { name: e.target.value })}
                />
              </Field>
              <Field id={`comp-kind-${item.key}`} label="Kind">
                <select
                  id={`comp-kind-${item.key}`}
                  className={selectClass}
                  value={item.kind}
                  onChange={(e) => update(item.key, { kind: e.target.value as ComponentKind })}
                >
                  {(Object.keys(KIND_LABEL) as ComponentKind[]).map((kind) => (
                    <option key={kind} value={kind}>
                      {KIND_LABEL[kind]}
                    </option>
                  ))}
                </select>
              </Field>
            </div>
            <Field id={`comp-tech-${item.key}`} label="Technology">
              <Input
                id={`comp-tech-${item.key}`}
                maxLength={200}
                value={item.technology}
                placeholder="e.g. Spring Boot, PostgreSQL 17"
                onChange={(e) => update(item.key, { technology: e.target.value })}
              />
            </Field>
            <Field id={`comp-resp-${item.key}`} label="Responsibility">
              <Textarea
                id={`comp-resp-${item.key}`}
                rows={2}
                maxLength={1_000}
                value={item.responsibility}
                onChange={(e) => update(item.key, { responsibility: e.target.value })}
              />
            </Field>
            <ToggleChips
              label="Depends on"
              empty="Add another component to link them."
              options={items
                .filter((other) => other.key !== item.key)
                .map((other) => ({ value: other.key, label: other.name.trim() || "(unnamed)" }))}
              selected={item.dependsOn}
              onToggle={(key) => update(item.key, { dependsOn: toggle(item.dependsOn, key) })}
            />
            <ToggleChips
              label="Requirements addressed"
              empty="There are no approved requirements."
              options={requirementOptions(requirements, item.requirements)}
              selected={item.requirements}
              onToggle={(code) => update(item.key, { requirements: toggle(item.requirements, code) })}
            />
          </ItemCard>
        ))}
      </ul>
      {items.length < LIMITS.components && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() =>
            setItems((list) => [
              ...list,
              { key: newKey(), name: "", kind: "SERVICE", responsibility: "", technology: "", dependsOn: [], requirements: [] },
            ])
          }
        >
          <Plus />
          Add component
        </Button>
      )}
    </EditorFrame>
  );
}

// --- Decisions -------------------------------------------------------------------

interface DecisionDraft extends Omit<ArchitectureDecision, "alternatives"> {
  key: number;
  /** One alternative per line. */
  alternatives: string;
}

export function DecisionsEditor({ content, requirements, onSave, onCancel }: SectionEditorProps) {
  const newKey = useKeys();
  const [items, setItems] = useState<DecisionDraft[]>(() =>
    content.decisions.map((d) => ({ ...d, key: newKey(), alternatives: d.alternatives.join("\n") })),
  );

  const update = (key: number, change: Partial<DecisionDraft>) =>
    setItems((list) => list.map((item) => (item.key === key ? { ...item, ...change } : item)));

  function validate() {
    const problems: string[] = [];
    items.forEach((item, index) => {
      const code = decisionCode(index);
      if (!item.title.trim()) problems.push(`${code} needs a title.`);
      if (!item.context.trim()) problems.push(`${code} needs a context.`);
      if (!item.decision.trim()) problems.push(`${code} needs the decision itself.`);
      if (!item.consequences.trim()) problems.push(`${code} needs its consequences.`);
      if (lines(item.alternatives).length > 8) problems.push(`${code}: list at most 8 alternatives.`);
    });
    return problems;
  }

  return (
    <EditorFrame
      title="Decisions"
      validate={validate}
      onCancel={onCancel}
      submit={() =>
        onSave({
          ...content,
          decisions: items.map((item) => ({
            title: item.title.trim(),
            context: item.context.trim(),
            decision: item.decision.trim(),
            alternatives: lines(item.alternatives),
            consequences: item.consequences.trim(),
            requirements: item.requirements,
          })),
        })
      }
    >
      {items.length === 0 && <p className="text-sm text-muted-foreground">No decisions yet.</p>}
      <ul className="space-y-3">
        {items.map((item, index) => (
          <ItemCard
            key={item.key}
            heading={decisionCode(index)}
            onRemove={() => setItems((list) => list.filter((d) => d.key !== item.key))}
          >
            <Field id={`adr-title-${item.key}`} label="Title">
              <Input id={`adr-title-${item.key}`} maxLength={200} value={item.title} onChange={(e) => update(item.key, { title: e.target.value })} />
            </Field>
            <Field id={`adr-context-${item.key}`} label="Context">
              <Textarea id={`adr-context-${item.key}`} rows={2} maxLength={3_000} value={item.context} onChange={(e) => update(item.key, { context: e.target.value })} />
            </Field>
            <Field id={`adr-decision-${item.key}`} label="Decision">
              <Textarea id={`adr-decision-${item.key}`} rows={2} maxLength={3_000} value={item.decision} onChange={(e) => update(item.key, { decision: e.target.value })} />
            </Field>
            <Field id={`adr-alternatives-${item.key}`} label="Alternatives considered" hint="One per line, with why it was not chosen.">
              <Textarea
                id={`adr-alternatives-${item.key}`}
                rows={3}
                value={item.alternatives}
                onChange={(e) => update(item.key, { alternatives: e.target.value })}
              />
            </Field>
            <Field id={`adr-consequences-${item.key}`} label="Consequences and trade-offs">
              <Textarea
                id={`adr-consequences-${item.key}`}
                rows={2}
                maxLength={3_000}
                value={item.consequences}
                onChange={(e) => update(item.key, { consequences: e.target.value })}
              />
            </Field>
            <ToggleChips
              label="Requirements addressed"
              empty="There are no approved requirements."
              options={requirementOptions(requirements, item.requirements)}
              selected={item.requirements}
              onToggle={(code) => update(item.key, { requirements: toggle(item.requirements, code) })}
            />
          </ItemCard>
        ))}
      </ul>
      {items.length < LIMITS.decisions && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() =>
            setItems((list) => [
              ...list,
              { key: newKey(), title: "", context: "", decision: "", alternatives: "", consequences: "", requirements: [] },
            ])
          }
        >
          <Plus />
          Add decision
        </Button>
      )}
    </EditorFrame>
  );
}

function lines(text: string) {
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
}

// --- Flows -----------------------------------------------------------------------

interface FlowDraft extends Omit<ArchitectureFlow, "description"> {
  key: number;
  description: string;
}

export function FlowsEditor({ content, onSave, onCancel }: SectionEditorProps) {
  const newKey = useKeys();
  const [items, setItems] = useState<FlowDraft[]>(() =>
    content.flows.map((f) => ({ ...f, key: newKey(), description: f.description ?? "" })),
  );

  const update = (key: number, change: Partial<FlowDraft>) =>
    setItems((list) => list.map((item) => (item.key === key ? { ...item, ...change } : item)));

  function validate() {
    const problems: string[] = [];
    items.forEach((item, index) => {
      if (!item.title.trim()) problems.push(`Flow ${index + 1} needs a title.`);
      if (!item.diagram.trim()) problems.push(`Flow ${index + 1} needs a diagram.`);
    });
    return problems;
  }

  return (
    <EditorFrame
      title="Key flows"
      validate={validate}
      onCancel={onCancel}
      submit={() =>
        onSave({
          ...content,
          flows: items.map((item) => ({
            title: item.title.trim(),
            description: item.description.trim() || null,
            diagram: item.diagram.trim(),
          })),
        })
      }
    >
      <p className="text-xs text-muted-foreground">
        Diagrams use{" "}
        <a
          className="underline hover:text-foreground"
          href="https://mermaid.js.org/syntax/sequenceDiagram.html"
          target="_blank"
          rel="noreferrer"
        >
          Mermaid sequence diagram syntax
        </a>
        . The preview updates as you type.
      </p>
      {items.length === 0 && <p className="text-sm text-muted-foreground">No flows yet.</p>}
      <ul className="space-y-3">
        {items.map((item, index) => (
          <ItemCard
            key={item.key}
            heading={item.title.trim() || `Flow ${index + 1}`}
            onRemove={() => setItems((list) => list.filter((f) => f.key !== item.key))}
          >
            <Field id={`flow-title-${item.key}`} label="Title">
              <Input id={`flow-title-${item.key}`} maxLength={200} value={item.title} onChange={(e) => update(item.key, { title: e.target.value })} />
            </Field>
            <Field id={`flow-description-${item.key}`} label="Description">
              <Input
                id={`flow-description-${item.key}`}
                maxLength={1_000}
                value={item.description}
                onChange={(e) => update(item.key, { description: e.target.value })}
              />
            </Field>
            <div className="grid gap-3 lg:grid-cols-2">
              <Field id={`flow-diagram-${item.key}`} label="Diagram source">
                <Textarea
                  id={`flow-diagram-${item.key}`}
                  rows={10}
                  maxLength={10_000}
                  spellCheck={false}
                  className="font-mono text-xs"
                  value={item.diagram}
                  onChange={(e) => update(item.key, { diagram: e.target.value })}
                />
              </Field>
              <div className="space-y-1.5">
                <p className="text-sm font-medium">Preview</p>
                {item.diagram.trim() ? (
                  <MermaidDiagram source={item.diagram} label={`Preview: ${item.title}`} debounceMs={500} />
                ) : (
                  <p className="text-xs text-muted-foreground">Write a diagram to see it here.</p>
                )}
              </div>
            </div>
          </ItemCard>
        ))}
      </ul>
      {items.length < LIMITS.flows && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() =>
            setItems((list) => [...list, { key: newKey(), title: "", description: "", diagram: "sequenceDiagram\n  actor User\n" }])
          }
        >
          <Plus />
          Add flow
        </Button>
      )}
    </EditorFrame>
  );
}
