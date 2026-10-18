import { useState } from "react";
import { Plus, X } from "lucide-react";

import {
  EditorFrame,
  Field,
  ToggleChips,
  requirementOptions,
  selectClass,
  toggle,
  type ChipOption,
} from "@/components/design/editor-kit";
import { ESTIMATES, TASK_TYPES, TYPE_LABEL } from "@/components/tasks/task-labels";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import type { BacklogOverview, Task, TaskInput, TaskType } from "@/lib/api";

const EMPTY: TaskInput = {
  title: "",
  description: "",
  type: "BACKEND",
  estimate: null,
  acceptanceCriteria: [],
  dependsOn: [],
  requirements: [],
  entities: [],
  endpoints: [],
  components: [],
};

/** Options for a list of names, plus values already chosen that no longer exist (so they can be removed). */
function nameOptions(names: string[], selected: string[]) {
  const options: ChipOption<string>[] = names.map((n) => ({ value: n, label: n }));
  for (const value of selected) {
    if (!names.includes(value)) options.push({ value, label: value, title: "No longer in the design" });
  }
  return options;
}

export function TaskForm({
  title,
  task,
  overview,
  onSubmit,
  onCancel,
}: {
  title: string;
  /** The task being edited, or null to create one. */
  task: Task | null;
  overview: BacklogOverview;
  onSubmit: (input: TaskInput) => Promise<void>;
  onCancel: () => void;
}) {
  const start: TaskInput = task ?? EMPTY;
  const [name, setName] = useState(start.title);
  const [description, setDescription] = useState(start.description);
  const [type, setType] = useState<TaskType>(start.type);
  const [estimate, setEstimate] = useState<number | null>(start.estimate);
  const [criteria, setCriteria] = useState<string[]>(start.acceptanceCriteria.length ? start.acceptanceCriteria : [""]);
  const [dependsOn, setDependsOn] = useState(start.dependsOn);
  const [requirements, setRequirements] = useState(start.requirements);
  const [entities, setEntities] = useState(start.entities);
  const [endpoints, setEndpoints] = useState(start.endpoints);
  const [components, setComponents] = useState(start.components);

  const others = overview.tasks.filter((t) => t.id !== task?.id);

  return (
    <EditorFrame
      title={title}
      onCancel={onCancel}
      validate={() =>
        [!name.trim() && "Give the task a title.", !description.trim() && "Describe the task."].filter(
          (m): m is string => Boolean(m),
        )
      }
      submit={() =>
        onSubmit({
          title: name.trim(),
          description: description.trim(),
          type,
          estimate,
          acceptanceCriteria: criteria.map((c) => c.trim()).filter(Boolean),
          dependsOn,
          requirements,
          entities,
          endpoints,
          components,
        })
      }
    >
      <div className="grid gap-3 sm:grid-cols-[1fr_10rem_8rem]">
        <Field id="task-title" label="Title">
          <Input id="task-title" maxLength={200} value={name} onChange={(e) => setName(e.target.value)} autoFocus />
        </Field>
        <Field id="task-type" label="Type">
          <select id="task-type" className={selectClass} value={type} onChange={(e) => setType(e.target.value as TaskType)}>
            {TASK_TYPES.map((t) => (
              <option key={t} value={t}>
                {TYPE_LABEL[t]}
              </option>
            ))}
          </select>
        </Field>
        <Field id="task-estimate" label="Estimate">
          <select
            id="task-estimate"
            className={selectClass}
            value={estimate ?? ""}
            onChange={(e) => setEstimate(e.target.value === "" ? null : Number(e.target.value))}
          >
            <option value="">—</option>
            {ESTIMATES.map((p) => (
              <option key={p} value={p}>
                {p} {p === 1 ? "point" : "points"}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <Field id="task-description" label="Description">
        <Textarea id="task-description" rows={3} maxLength={4_000} value={description} onChange={(e) => setDescription(e.target.value)} />
      </Field>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Acceptance criteria</legend>
        {criteria.map((criterion, index) => (
          <div key={index} className="flex items-center gap-2">
            <Input
              aria-label={`Acceptance criterion ${index + 1}`}
              maxLength={500}
              value={criterion}
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
        {criteria.length < 10 && (
          <Button type="button" variant="ghost" size="sm" onClick={() => setCriteria((list) => [...list, ""])}>
            <Plus />
            Add criterion
          </Button>
        )}
      </fieldset>

      <ToggleChips
        label="Depends on"
        empty="There are no other tasks."
        options={others.map((t) => ({ value: t.code, label: t.code, title: t.title }))}
        selected={dependsOn}
        onToggle={(code) => setDependsOn((list) => toggle(list, code))}
      />
      <ToggleChips
        label="Requirements"
        empty="There are no approved requirements."
        options={requirementOptions(overview.refs.requirements, requirements)}
        selected={requirements}
        onToggle={(code) => setRequirements((list) => toggle(list, code))}
      />
      <ToggleChips
        label="Entities"
        empty="The database design has no entities."
        options={nameOptions(overview.refs.entities, entities)}
        selected={entities}
        onToggle={(n) => setEntities((list) => toggle(list, n))}
      />
      <ToggleChips
        label="Endpoints"
        empty="The API design has no endpoints."
        options={nameOptions(overview.refs.endpoints, endpoints)}
        selected={endpoints}
        onToggle={(n) => setEndpoints((list) => toggle(list, n))}
      />
      <ToggleChips
        label="Components"
        empty="The architecture has no components."
        options={nameOptions(overview.refs.components, components)}
        selected={components}
        onToggle={(n) => setComponents((list) => toggle(list, n))}
      />
    </EditorFrame>
  );
}
