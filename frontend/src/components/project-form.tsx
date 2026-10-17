import { useState, type FormEvent } from "react";

import { FormError, FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, errorMessage, type ProjectInput } from "@/lib/api";

const NAME_MAX = 120;
const DESCRIPTION_MAX = 2000;

interface ProjectFormProps {
  initialValues?: ProjectInput;
  submitLabel: string;
  submittingLabel: string;
  onSubmit: (input: ProjectInput) => Promise<void>;
  onCancel?: () => void;
}

type FieldErrors = Partial<Record<keyof ProjectInput, string>>;

export function ProjectForm({ initialValues, submitLabel, submittingLabel, onSubmit, onCancel }: ProjectFormProps) {
  const [name, setName] = useState(initialValues?.name ?? "");
  const [description, setDescription] = useState(initialValues?.description ?? "");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    if (!name.trim()) {
      setFieldErrors({ name: "Give your project a name." });
      return;
    }
    setFieldErrors({});
    setIsSubmitting(true);

    try {
      await onSubmit({ name: name.trim(), description: description.trim() });
    } catch (err) {
      if (err instanceof ApiError && err.violations.length > 0) {
        const serverErrors: FieldErrors = {};
        for (const violation of err.violations) {
          if (violation.field === "name" || violation.field === "description") {
            serverErrors[violation.field] = violation.message;
          }
        }
        setFieldErrors(serverErrors);
      } else {
        setError(errorMessage(err));
      }
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5" noValidate>
      <FormError message={error} />
      <FormField id="project-name" label="Name" error={fieldErrors.name}>
        <Input
          id="project-name"
          autoFocus
          maxLength={NAME_MAX}
          placeholder="e.g. Invoice tracker"
          value={name}
          aria-invalid={Boolean(fieldErrors.name)}
          aria-describedby={fieldErrors.name ? "project-name-error" : undefined}
          onChange={(event) => setName(event.target.value)}
        />
      </FormField>
      <FormField
        id="project-description"
        label="Description"
        error={fieldErrors.description}
        hint={`${description.length}/${DESCRIPTION_MAX} · Optional. What should this software do, and for whom?`}
      >
        <Textarea
          id="project-description"
          rows={5}
          maxLength={DESCRIPTION_MAX}
          placeholder="A web app for freelancers to send invoices and track payments…"
          value={description}
          aria-invalid={Boolean(fieldErrors.description)}
          aria-describedby={fieldErrors.description ? "project-description-error" : undefined}
          onChange={(event) => setDescription(event.target.value)}
        />
      </FormField>
      <div className="flex items-center gap-2">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? submittingLabel : submitLabel}
        </Button>
        {onCancel && (
          <Button type="button" variant="ghost" onClick={onCancel} disabled={isSubmitting}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  );
}
