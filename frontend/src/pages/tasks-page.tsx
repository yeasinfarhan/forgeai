import { useEffect, useState } from "react";
import { ArrowLeft, SquareKanban, ListOrdered, Plus, Sparkles, TriangleAlert } from "lucide-react";
import { Link, useParams } from "react-router";

import { FindingsPanel } from "@/components/design/findings-panel";
import { FormError } from "@/components/form-field";
import { TaskBacklogList } from "@/components/tasks/task-backlog-list";
import { TaskBoard } from "@/components/tasks/task-board";
import { TaskDetailsPanel } from "@/components/tasks/task-details";
import { TaskForm } from "@/components/tasks/task-form";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  ApiError,
  changeTaskStatus,
  createTask,
  errorMessage,
  generateTasks,
  getBacklog,
  getProject,
  isAbortError,
  moveTask,
  type BacklogOverview,
  type Project,
} from "@/lib/api";
import { formatRelative } from "@/lib/format";
import { cn } from "@/lib/utils";
import { NotFoundPage } from "@/pages/not-found-page";

type LoadState =
  | { kind: "loading" }
  | { kind: "loaded"; project: Project; overview: BacklogOverview }
  | { kind: "not-found" }
  | { kind: "error"; message: string };

type View = "board" | "backlog";

export function TasksPage() {
  const { projectId = "" } = useParams();
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [view, setView] = useState<View>("board");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [confirmRegenerate, setConfirmRegenerate] = useState(false);
  const [busy, setBusy] = useState<{ message: string; slow: boolean } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([getProject(projectId, controller.signal), getBacklog(projectId, controller.signal)])
      .then(([project, overview]) => setState({ kind: "loaded", project, overview }))
      .catch((error: unknown) => {
        if (isAbortError(error) || controller.signal.aborted) return;
        if (error instanceof ApiError && (error.status === 404 || error.status === 400)) {
          setState({ kind: "not-found" });
        } else {
          setState({ kind: "error", message: errorMessage(error) });
        }
      });
    return () => controller.abort();
  }, [projectId]);

  if (state.kind === "not-found") return <NotFoundPage />;
  if (state.kind === "loading") {
    return <div aria-hidden="true" className="mx-auto h-96 max-w-6xl animate-pulse rounded-xl border bg-card" />;
  }
  if (state.kind === "error") {
    return (
      <div className="mx-auto max-w-6xl">
        <FormError message={state.message} />
      </div>
    );
  }

  const { project, overview } = state;
  const setOverview = (next: BacklogOverview) => setState({ kind: "loaded", project, overview: next });
  const selected = overview.tasks.find((t) => t.id === selectedId) ?? null;
  const { counts } = overview;
  const hasTasks = counts.total > 0;
  const percent = counts.points > 0 ? Math.round((counts.pointsDone / counts.points) * 100) : 0;

  async function run(message: string, action: () => Promise<BacklogOverview>, slow = false) {
    setActionError(null);
    setBusy({ message, slow });
    try {
      setOverview(await action());
    } catch (error) {
      setActionError(errorMessage(error));
      // The server may have finished or changed something anyway (e.g. a run that outlived the
      // browser's wait): show its current state alongside the error.
      await getBacklog(project.id).then(setOverview).catch(() => undefined);
    } finally {
      setBusy(null);
    }
  }

  const generate = () => {
    setConfirmRegenerate(false);
    run(hasTasks ? "Regenerating tasks…" : "Breaking the design into tasks…", () => generateTasks(project.id), true);
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <Link
        to={`/projects/${project.id}`}
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" />
        {project.name}
      </Link>

      <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Development tasks</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {overview.generatedAt
              ? `Generated ${formatRelative(overview.generatedAt)}${overview.model ? ` by ${overview.model}` : ""}`
              : "An ordered backlog from the approved design, linked to the requirements and design elements it implements."}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <Button variant="outline" size="sm" disabled={busy !== null || adding} onClick={() => setAdding(true)}>
            <Plus />
            Add task
          </Button>
          {overview.canGenerate && (
            <Button
              size="sm"
              disabled={busy !== null}
              onClick={() => (hasTasks ? setConfirmRegenerate(true) : generate())}
            >
              <Sparkles />
              {hasTasks ? "Regenerate" : "Generate"}
            </Button>
          )}
        </div>
      </section>

      {hasTasks && (
        <div className="space-y-2 rounded-xl border bg-card p-4">
          <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm">
            <span>
              <span className="font-semibold">{counts.total}</span> tasks
            </span>
            <span className="text-muted-foreground">{counts.todo} to do</span>
            <span className="text-primary">{counts.inProgress} in progress</span>
            <span className="text-success">{counts.done} done</span>
            <span className="ml-auto font-mono text-xs text-muted-foreground">
              {counts.pointsDone} / {counts.points} points
            </span>
          </div>
          <div
            className="h-1.5 overflow-hidden rounded-full bg-muted"
            role="progressbar"
            aria-label="Points done"
            aria-valuenow={percent}
            aria-valuemin={0}
            aria-valuemax={100}
          >
            <div className="h-full rounded-full bg-success transition-all" style={{ width: `${percent}%` }} />
          </div>
        </div>
      )}

      {!hasTasks && overview.blocker && (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-start gap-3 text-sm sm:flex-row sm:items-center">
            <p className="text-muted-foreground">
              Tasks are generated from the approved design. {overview.blocker} You can also add tasks by hand.
            </p>
            <Button asChild variant="outline" size="sm" className="sm:ml-auto">
              <Link to={`/projects/${project.id}/api-design`}>Go to API design</Link>
            </Button>
          </CardContent>
        </Card>
      )}

      {overview.inputsChanged && (
        <p className="flex items-start gap-2 rounded-lg border border-primary/30 bg-primary/10 px-4 py-3 text-sm">
          <TriangleAlert className="mt-0.5 size-4 shrink-0 text-primary" />
          <span>
            The approved design changed after these tasks were generated. Check the links below
            {overview.canGenerate ? ", or regenerate to replace the untouched tasks" : ""}.
          </span>
        </p>
      )}

      {confirmRegenerate && (
        <div role="alertdialog" className="flex flex-wrap items-center gap-2 rounded-lg border border-primary/30 bg-primary/10 p-3 text-sm">
          <span className="mr-auto">
            Regenerate? Only AI tasks that are still to do and that nobody edited are replaced. Started, finished, edited
            and hand-written tasks stay, and the AI is told about them.
          </span>
          <Button size="sm" onClick={generate}>
            Regenerate
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setConfirmRegenerate(false)}>
            Cancel
          </Button>
        </div>
      )}

      <FormError message={actionError} />
      {busy && (
        <div role="status" className="flex items-center gap-3 rounded-lg border border-primary/30 bg-primary/10 px-4 py-3 text-sm">
          <span className="size-2 animate-pulse rounded-full bg-primary" />
          {busy.message}
          {busy.slow && <span className="ml-auto text-xs text-muted-foreground">This can take up to a minute.</span>}
        </div>
      )}

      {adding && (
        <TaskForm
          title="New task"
          task={null}
          overview={overview}
          onCancel={() => setAdding(false)}
          onSubmit={async (input) => {
            const next = await createTask(project.id, input);
            setOverview(next);
            setAdding(false);
            setSelectedId(next.tasks[next.tasks.length - 1]?.id ?? null);
          }}
        />
      )}

      {!hasTasks && overview.canGenerate && !busy && (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-14 text-center">
          <span className="grid size-12 place-items-center rounded-full bg-primary/10 text-primary">
            <SquareKanban className="size-5" />
          </span>
          <p className="font-medium">No tasks yet</p>
          <p className="max-w-md text-sm text-muted-foreground">
            The design is approved. Generate an ordered backlog with acceptance criteria and dependencies, then track it on
            the board.
          </p>
        </div>
      )}

      {selected && (
        <TaskDetailsPanel
          key={selected.id}
          projectId={project.id}
          task={selected}
          overview={overview}
          onChanged={setOverview}
          onClose={() => setSelectedId(null)}
        />
      )}

      {hasTasks && (
        <>
          <div className="flex gap-1" role="tablist" aria-label="View">
            {(
              [
                ["board", "Board", SquareKanban],
                ["backlog", "Backlog order", ListOrdered],
              ] as const
            ).map(([value, label, Icon]) => (
              <button
                key={value}
                role="tab"
                aria-selected={view === value}
                onClick={() => setView(value)}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm transition-colors",
                  view === value ? "bg-accent font-medium" : "text-muted-foreground hover:bg-accent/60",
                )}
              >
                <Icon className="size-4" />
                {label}
              </button>
            ))}
          </div>

          {view === "board" ? (
            <TaskBoard
              tasks={overview.tasks}
              selectedId={selectedId}
              disabled={busy !== null}
              onSelect={(task) => setSelectedId(task.id)}
              onMove={(task, status) => run(`Moving ${task.code}…`, () => changeTaskStatus(project.id, task, status))}
            />
          ) : (
            <TaskBacklogList
              tasks={overview.tasks}
              selectedId={selectedId}
              disabled={busy !== null}
              onSelect={(task) => setSelectedId(task.id)}
              onReorder={(task, position) => run(`Moving ${task.code}…`, () => moveTask(project.id, task, position))}
            />
          )}

          <FindingsPanel
            findings={overview.findings}
            title="Coverage checks"
            emptyText="Every endpoint, entity and functional requirement is covered, and dependencies come first."
          />
        </>
      )}
    </div>
  );
}
