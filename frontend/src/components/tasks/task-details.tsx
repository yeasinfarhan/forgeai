import { useEffect, useState, type ReactNode } from "react";
import { Lock, Pencil, Trash2, X } from "lucide-react";

import { selectClass } from "@/components/design/editor-kit";
import { FormError } from "@/components/form-field";
import { TaskForm } from "@/components/tasks/task-form";
import { Estimate, STATUS_LABEL, TASK_STATUSES, TaskStatusBadge, TypeBadge } from "@/components/tasks/task-labels";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  changeTaskStatus,
  deleteTask,
  errorMessage,
  getTaskEvents,
  updateTask,
  type BacklogOverview,
  type Task,
  type TaskEvent,
  type TaskStatus,
} from "@/lib/api";
import { formatDateTime } from "@/lib/format";

function Chips({ label, values, titles }: { label: string; values: string[]; titles?: Map<string, string> }) {
  if (values.length === 0) return null;
  return (
    <div>
      <p className="mb-1 text-xs font-semibold tracking-wider text-muted-foreground uppercase">{label}</p>
      <div className="flex flex-wrap gap-1">
        {values.map((value) => (
          <Badge key={value} variant="outline" className="font-mono" title={titles?.get(value)}>
            {value}
          </Badge>
        ))}
      </div>
    </div>
  );
}

function Block({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <p className="mb-1 text-xs font-semibold tracking-wider text-muted-foreground uppercase">{label}</p>
      {children}
    </div>
  );
}

/** One task in full: content, links, status, activity, editing and deletion. */
export function TaskDetailsPanel({
  projectId,
  task,
  overview,
  onChanged,
  onClose,
}: {
  projectId: string;
  task: Task;
  overview: BacklogOverview;
  onChanged: (overview: BacklogOverview) => void;
  onClose: () => void;
}) {
  const [mode, setMode] = useState<"view" | "edit" | "confirm-delete">("view");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [events, setEvents] = useState<TaskEvent[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    setEvents(null);
    getTaskEvents(projectId, task)
      .then((list) => {
        if (!cancelled) setEvents(list);
      })
      .catch(() => {
        if (!cancelled) setEvents([]);
      });
    return () => {
      cancelled = true;
    };
    // Reload the activity whenever the task changes (its version moves on every change).
  }, [projectId, task, task.version]);

  async function run(action: () => Promise<BacklogOverview>) {
    setError(null);
    setBusy(true);
    try {
      onChanged(await action());
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  if (mode === "edit") {
    return (
      <TaskForm
        title={`${task.code}`}
        task={task}
        overview={overview}
        onCancel={() => setMode("view")}
        onSubmit={async (input) => {
          onChanged(await updateTask(projectId, task, input));
          setMode("view");
        }}
      />
    );
  }

  const titles = new Map(overview.tasks.map((t) => [t.code, `${t.title} (${STATUS_LABEL[t.status]})`]));
  const requirementTitles = new Map(overview.refs.requirements.map((r) => [r.code, r.title]));

  return (
    <section className="space-y-4 rounded-xl border border-primary/40 bg-card p-5" aria-label={`Task ${task.code}`}>
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs text-muted-foreground">{task.code}</span>
            <TypeBadge type={task.type} />
            <Estimate points={task.estimate} />
            <TaskStatusBadge status={task.status} />
            {task.source === "USER" && <Badge variant="outline">Added by you</Badge>}
            {task.source === "AI" && task.edited && <Badge variant="outline">Edited</Badge>}
          </div>
          <h2 className="text-lg font-semibold">{task.title}</h2>
        </div>
        <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close task">
          <X />
        </Button>
      </div>

      <p className="text-sm leading-relaxed whitespace-pre-wrap">{task.description}</p>

      {task.acceptanceCriteria.length > 0 && (
        <Block label="Acceptance criteria">
          <ul className="list-disc space-y-0.5 pl-5 text-sm marker:text-muted-foreground">
            {task.acceptanceCriteria.map((c, i) => (
              <li key={i}>{c}</li>
            ))}
          </ul>
        </Block>
      )}

      {task.blockedBy.length > 0 && (
        <p className="flex items-center gap-2 rounded-lg border border-primary/30 bg-primary/10 px-3 py-2 text-sm">
          <Lock className="size-4 text-primary" />
          Waiting for {task.blockedBy.join(", ")} to be done before it can start.
        </p>
      )}

      <div className="grid gap-3 sm:grid-cols-2">
        <Chips label="Depends on" values={task.dependsOn} titles={titles} />
        <Chips label="Requirements" values={task.requirements} titles={requirementTitles} />
        <Chips label="Entities" values={task.entities} />
        <Chips label="Endpoints" values={task.endpoints} />
        <Chips label="Components" values={task.components} />
      </div>

      <FormError message={error} />

      {mode === "confirm-delete" && (
        <div role="alertdialog" className="flex flex-wrap items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm">
          <span className="mr-auto">Delete {task.code}? Its activity is deleted too.</span>
          <Button
            size="sm"
            className="bg-destructive text-white hover:bg-destructive/90"
            disabled={busy}
            onClick={async () => {
              setError(null);
              setBusy(true);
              try {
                onChanged(await deleteTask(projectId, task));
                onClose();
              } catch (err) {
                setError(errorMessage(err));
                setBusy(false);
              }
            }}
          >
            Delete
          </Button>
          <Button size="sm" variant="ghost" disabled={busy} onClick={() => setMode("view")}>
            Cancel
          </Button>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2 border-t pt-3">
        <label htmlFor={`status-${task.id}`} className="text-sm text-muted-foreground">
          Status
        </label>
        <select
          id={`status-${task.id}`}
          className={`${selectClass} w-40`}
          value={task.status}
          disabled={busy}
          onChange={(e) => run(() => changeTaskStatus(projectId, task, e.target.value as TaskStatus))}
        >
          {TASK_STATUSES.map((s) => (
            <option key={s} value={s}>
              {STATUS_LABEL[s]}
            </option>
          ))}
        </select>
        <span className="mr-auto" />
        <Button variant="ghost" size="sm" disabled={busy} onClick={() => setMode("edit")}>
          <Pencil />
          Edit
        </Button>
        <Button
          variant="ghost"
          size="sm"
          className="text-destructive hover:text-destructive"
          disabled={busy}
          onClick={() => setMode("confirm-delete")}
        >
          <Trash2 />
          Delete
        </Button>
      </div>

      <Block label="Activity">
        {!events ? (
          <div aria-hidden="true" className="h-10 animate-pulse rounded-lg bg-muted" />
        ) : (
          <ol className="space-y-0.5 text-xs text-muted-foreground">
            {events.map((event, i) => (
              <li key={i}>
                {formatDateTime(event.createdAt)} ·{" "}
                {event.type === "CREATED"
                  ? "Created"
                  : event.type === "EDITED"
                    ? "Edited"
                    : `${STATUS_LABEL[event.fromStatus ?? "TODO"]} → ${STATUS_LABEL[event.toStatus ?? "TODO"]}`}
              </li>
            ))}
          </ol>
        )}
      </Block>
    </section>
  );
}
