import { useRef, useState, type FormEvent, type ReactNode } from "react";
import { Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { errorDetails, type RequirementRef } from "@/lib/api";
import { cn } from "@/lib/utils";

/*
 * Building blocks for the section editors of the architecture, database and API design pages.
 */

/** Form shell for one section: validation messages, server errors, Save / Cancel. */
export function EditorFrame({
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

export function Field({ id, label, hint, children }: { id: string; label: string; hint?: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

export interface ChipOption<T> {
  value: T;
  label: string;
  title?: string;
}

/** A row of toggle buttons for picking several values (dependencies, requirement codes). */
export function ToggleChips<T extends string | number>({
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

export function toggle<T>(list: T[], value: T): T[] {
  return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
}

/** Approved requirements, plus any code already referenced that is not (so it can be removed). */
export function requirementOptions(requirements: RequirementRef[], selected: string[]): ChipOption<string>[] {
  const options: ChipOption<string>[] = requirements.map((r) => ({ value: r.code, label: r.code, title: r.title }));
  for (const code of selected) {
    if (!requirements.some((r) => r.code === code)) {
      options.push({ value: code, label: code, title: "Not an approved requirement" });
    }
  }
  return options;
}

export function ItemCard({ heading, onRemove, children }: { heading: string; onRemove: () => void; children: ReactNode }) {
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
export function useKeys() {
  const next = useRef(0);
  return () => next.current++;
}

export const selectClass =
  "flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 dark:bg-input/30";

