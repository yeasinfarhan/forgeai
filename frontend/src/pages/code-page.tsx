import { useEffect, useState } from "react";
import { ArrowLeft, Code2, Download, FlaskConical, Lock, Sparkles } from "lucide-react";
import { Link, useParams, useSearchParams } from "react-router";

import { ChangesetReview } from "@/components/code/changeset-review";
import { FileBrowser } from "@/components/code/file-browser";
import { FormError } from "@/components/form-field";
import { STATUS_LABEL } from "@/components/tasks/task-labels";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  ApiError,
  codeExportPath,
  downloadBinary,
  errorMessage,
  generateCode,
  generateTests,
  getChangeset,
  getCode,
  getProject,
  isAbortError,
  type Changeset,
  type CodeOverview,
  type CodeTaskItem,
  type Project,
} from "@/lib/api";
import { formatRelative } from "@/lib/format";
import { cn } from "@/lib/utils";
import { NotFoundPage } from "@/pages/not-found-page";

type LoadState =
  | { kind: "loading" }
  | { kind: "loaded"; project: Project; overview: CodeOverview }
  | { kind: "not-found" }
  | { kind: "error"; message: string };

export function CodePage() {
  const { projectId = "" } = useParams();
  const [searchParams] = useSearchParams();
  const focusTask = searchParams.get("task");
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [changeset, setChangeset] = useState<Changeset | null>(null);
  const [generating, setGenerating] = useState<{ task: CodeTaskItem; kind: "code" | "tests" } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([getProject(projectId, controller.signal), getCode(projectId, controller.signal)])
      .then(([project, overview]) => {
        setState({ kind: "loaded", project, overview });
        // Open the proposal waiting for review: the focused task's, or the newest one.
        const focused = overview.tasks.find((t) => t.id === focusTask);
        const pending =
          focused?.pendingChangesetId ??
          focused?.pendingTestChangesetId ??
          overview.changesets.find((c) => c.status === "PENDING")?.id;
        if (pending) {
          getChangeset(projectId, pending, controller.signal)
            .then(setChangeset)
            .catch(() => undefined);
        }
      })
      .catch((err: unknown) => {
        if (isAbortError(err) || controller.signal.aborted) return;
        if (err instanceof ApiError && (err.status === 404 || err.status === 400)) {
          setState({ kind: "not-found" });
        } else {
          setState({ kind: "error", message: errorMessage(err) });
        }
      });
    return () => controller.abort();
  }, [projectId, focusTask]);

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
  const pendingReviews = overview.changesets.filter((c) => c.status === "PENDING").length;

  async function refresh() {
    try {
      setState({ kind: "loaded", project, overview: await getCode(project.id) });
    } catch {
      // The page keeps what it has; the next action reloads again.
    }
  }

  async function generate(task: CodeTaskItem, kind: "code" | "tests") {
    setError(null);
    setGenerating({ task, kind });
    try {
      setChangeset(await (kind === "code" ? generateCode(project.id, task.id) : generateTests(project.id, task.id)));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setGenerating(null);
      await refresh();
    }
  }

  async function open(changesetId: string) {
    setError(null);
    try {
      setChangeset(await getChangeset(project.id, changesetId));
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function exportZip() {
    setError(null);
    setExporting(true);
    try {
      const file = await downloadBinary(codeExportPath(project.id));
      const url = URL.createObjectURL(file.blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = file.fileName;
      link.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setExporting(false);
    }
  }

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
          <h1 className="text-2xl font-semibold tracking-tight">Code</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Generate the code task by task, review every file as a diff, and accept what goes into the project.
          </p>
        </div>
        <div className="flex shrink-0 gap-2">
          <Button variant="outline" size="sm" disabled={exporting || overview.files.length === 0} onClick={exportZip}>
            <Download />
            {exporting ? "Preparing…" : "Download ZIP"}
          </Button>
        </div>
      </section>

      <div className="flex flex-wrap gap-x-5 gap-y-1 rounded-xl border bg-card px-4 py-3 text-sm">
        <span>
          <span className="font-semibold">{overview.files.length}</span> accepted files
        </span>
        <span className="text-muted-foreground">{overview.files.reduce((n, f) => n + f.lines, 0)} lines</span>
        <span className={cn(pendingReviews > 0 ? "text-primary" : "text-muted-foreground")}>
          {pendingReviews} {pendingReviews === 1 ? "proposal" : "proposals"} waiting for review
        </span>
      </div>

      {overview.blocker && (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-start gap-3 text-sm sm:flex-row sm:items-center">
            <p className="text-muted-foreground">Code is generated per task from the approved design. {overview.blocker}</p>
            <Button asChild variant="outline" size="sm" className="sm:ml-auto">
              <Link to={`/projects/${project.id}/tasks`}>Go to development tasks</Link>
            </Button>
          </CardContent>
        </Card>
      )}

      <FormError message={error} />
      {generating && (
        <div role="status" className="flex items-center gap-3 rounded-lg border border-primary/30 bg-primary/10 px-4 py-3 text-sm">
          <span className="size-2 animate-pulse rounded-full bg-primary" />
          Writing the {generating.kind} for {generating.task.code}…
          <span className="ml-auto text-xs text-muted-foreground">This can take a few minutes.</span>
        </div>
      )}

      {changeset && (
        <ChangesetReview
          key={changeset.id}
          projectId={project.id}
          changeset={changeset}
          onChanged={(next) => {
            setChangeset(next);
            void refresh();
          }}
        />
      )}

      {overview.tasks.length > 0 && (
        <section className="space-y-3 rounded-xl border bg-card p-5">
          <div>
            <h2 className="font-semibold">Tasks</h2>
            <p className="mt-0.5 text-sm text-muted-foreground">
              A task's code can be generated once the tasks it depends on are done, and its tests once its code is
              accepted. Generating again replaces a proposal that is still waiting for review. ForgeAI writes the tests
              but does not run them: run them in your project after downloading the code.
            </p>
          </div>
          <ol className="divide-y rounded-lg border">
            {overview.tasks.map((task) => (
              <li
                key={task.id}
                className={cn("flex flex-wrap items-center gap-2 px-3 py-2", task.id === focusTask && "bg-accent/60")}
              >
                <span className="font-mono text-xs text-muted-foreground">{task.code}</span>
                <span className="min-w-0 flex-1 text-sm">{task.title}</span>
                <Badge variant="outline">{STATUS_LABEL[task.status]}</Badge>
                {task.blockedBy.length > 0 && (
                  <span className="flex items-center gap-1 text-xs text-muted-foreground">
                    <Lock className="size-3" />
                    after {task.blockedBy.join(", ")}
                  </span>
                )}
                {task.pendingChangesetId && (
                  <Button size="sm" variant="outline" onClick={() => open(task.pendingChangesetId!)}>
                    Review code
                  </Button>
                )}
                <Button
                  size="sm"
                  variant={task.pendingChangesetId || task.hasCode ? "ghost" : "default"}
                  disabled={!overview.canGenerate || generating !== null || task.blockedBy.length > 0}
                  onClick={() => generate(task, "code")}
                >
                  <Sparkles />
                  {task.pendingChangesetId || task.hasCode ? "Regenerate code" : "Generate code"}
                </Button>
                {task.pendingTestChangesetId && (
                  <Button size="sm" variant="outline" onClick={() => open(task.pendingTestChangesetId!)}>
                    Review tests
                  </Button>
                )}
                {task.hasCode && (
                  <Button
                    size="sm"
                    variant={task.pendingTestChangesetId ? "ghost" : "outline"}
                    disabled={!overview.canGenerate || generating !== null}
                    onClick={() => generate(task, "tests")}
                  >
                    <FlaskConical />
                    {task.pendingTestChangesetId ? "Regenerate tests" : "Generate tests"}
                  </Button>
                )}
              </li>
            ))}
          </ol>
        </section>
      )}

      {overview.changesets.length > 0 && (
        <section className="space-y-3 rounded-xl border bg-card p-5">
          <h2 className="font-semibold">Proposals</h2>
          <ul className="divide-y rounded-lg border">
            {overview.changesets.map((c) => (
              <li key={c.id}>
                <button
                  type="button"
                  onClick={() => open(c.id)}
                  className={cn(
                    "flex w-full flex-wrap items-center gap-2 px-3 py-2 text-left text-sm hover:bg-accent/60",
                    changeset?.id === c.id && "bg-accent/60",
                  )}
                >
                  <span className="font-mono text-xs text-muted-foreground">{c.taskCode}</span>
                  <span className="min-w-0 flex-1 truncate">{c.taskTitle}</span>
                  {c.kind === "TESTS" && <Badge variant="outline">Tests</Badge>}
                  <span className="text-xs text-muted-foreground">
                    {c.accepted} accepted · {c.rejected} rejected{c.status === "PENDING" ? ` · ${c.pending} to review` : ""}
                  </span>
                  <Badge variant={c.status === "COMPLETED" ? "success" : "outline"}>
                    {c.status === "PENDING" ? "In review" : c.status === "COMPLETED" ? "Reviewed" : "Discarded"}
                  </Badge>
                  <span className="text-xs text-muted-foreground">{formatRelative(c.createdAt)}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="space-y-3 rounded-xl border bg-card p-5">
        <h2 className="flex items-center gap-2 font-semibold">
          <Code2 className="size-4 text-primary" />
          Accepted code
        </h2>
        <FileBrowser projectId={project.id} files={overview.files} />
      </section>
    </div>
  );
}
