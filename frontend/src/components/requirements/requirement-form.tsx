import { useState, type FormEvent, type ReactNode } from "react";
import { Plus, X } from "lucide-react";

import { FormError, FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, errorMessage, type RequirementInput, type RequirementPriority } from "@/lib/api";
import { cn } from "@/lib/utils";

const PRIORITIES: { value: RequirementPriority; label: string; hint: string }[] = [
  { value: "MUST", label: "Must", hint: "The first version cannot ship without it" },
  { value: "SHOULD", label: "Should", hint: "Important, but not critical" },
  { value: "COULD", label: "Could", hint: "Nice to have" },
];

interface RequirementFormProps {
  initial?: RequirementInput;
  submitLabel: string;
  onSubmit: (input: RequirementInput) => Promise<void>;
  onCancel: () => void;
  /** Extra controls rendered above the fields (e.g. the type selector when adding). */
  header?: ReactNode;
}

const EMPTY: RequirementInput = { category: "", title: "", description: "", acceptanceCriteria: [""], priority: "SHOULD" };

export function RequirementForm({ initial, submitLabel, onSubmit, onCancel, header }: RequirementFormProps) {
  const start = initial ?? EMPTY;
  const [category, setCategory] = useState(start.category);
  const [title, setTitle] = useState(start.title);
  const [description, setDescription] = useState(start.description);
  const [criteria, setCriteria] = useState<string[]>(start.acceptanceCriteria.length ? start.acceptanceCriteria : [""]);
  const [priority, setPriority] = useState<RequirementPriority>(start.priority);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const errors: Record<string, string> = {};
    if (!title.trim()) errors.title = "Give the requirement a title.";
    if (!description.trim()) errors.description = "Describe the requirement.";
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setIsSaving(true);
    try {
      await onSubmit({
        category: category.trim(),
        title: title.trim(),
        description: description.trim(),
        acceptanceCriteria: criteria.map((c) => c.trim()).filter(Boolean),
        priority,
      });
    } catch (err) {
      if (err instanceof ApiError && err.violations.length > 0) {
        setFieldErrors(Object.fromEntries(err.violations.map((v) => [v.field.split("[")[0], v.message])));
      } else {
        setError(errorMessage(err));
      }
      setIsSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      {header}
      <FormError message={error} />
      <div className="grid gap-4 sm:grid-cols-[1fr_12rem]">
        <FormField id="req-title" label="Title" error={fieldErrors.title}>
          <Input id="req-title" maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} autoFocus />
        </FormField>
        <FormField id="req-category" label="Category" error={fieldErrors.category} hint="e.g. Invoices, Security">
          <Input id="req-category" maxLength={60} value={category} onChange={(e) => setCategory(e.target.value)} />
        </FormField>
      </div>
      <FormField id="req-description" label="Description" error={fieldErrors.description}>
        <Textarea
          id="req-description"
          rows={3}
          maxLength={4000}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </FormField>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Acceptance criteria</legend>
        {criteria.map((criterion, index) => (
          <div key={index} className="flex items-center gap-2">
            <Input
              aria-label={`Acceptance criterion ${index + 1}`}
              maxLength={500}
              value={criterion}
              placeholder="Given …, when …, then …"
              onChange={(e) => setCriteria((list) => list.map((c, i) => (i === index ? e.target.value : c)))}
            />
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label="Remove criterion"
              disabled={criteria.length === 1}
              onClick={() => setCriteria((list) => list.filter((_, i) => i !== index))}
            >
              <X />
            </Button>
          </div>
        ))}
        {criteria.length < 15 && (
          <Button type="button" variant="ghost" size="sm" onClick={() => setCriteria((list) => [...list, ""])}>
            <Plus />
            Add criterion
          </Button>
        )}
      </fieldset>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Priority</legend>
        <div className="flex flex-wrap gap-2" role="radiogroup">
          {PRIORITIES.map((option) => (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={priority === option.value}
              title={option.hint}
              onClick={() => setPriority(option.value)}
              className={cn(
                "rounded-md border px-3 py-1.5 text-sm transition-colors",
                priority === option.value
                  ? "border-primary bg-primary/10 text-primary"
                  : "text-muted-foreground hover:bg-accent",
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
      </fieldset>

      <div className="flex gap-2">
        <Button type="submit" disabled={isSaving}>
          {isSaving ? "Saving…" : submitLabel}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel} disabled={isSaving}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
