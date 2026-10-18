import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, ListPlus, RotateCcw, ScanSearch, TriangleAlert, X } from "lucide-react";
import { Link, useParams } from "react-router";

import { FormError } from "@/components/form-field";
import { TYPE_LABEL } from "@/components/tasks/task-labels";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  actOnFinding,
  ApiError,
  errorMessage,
  generateReview,
  getProject,
  getReview,
  getReviews,
  isAbortError,
  type CodeReview,
  type FindingCategory,
  type FindingSeverity,
  type FindingStatus,
  type Project,
  type ReviewFinding,
  type ReviewOverview,
} from "@/lib/api";
import { formatDateTime, formatRelative } from "@/lib/format";
import { cn } from "@/lib/utils";
import { NotFoundPage } from "@/pages/not-found-page";

type LoadState =
  | { kind: "loading" }
  | { kind: "loaded"; project: Project; overview: ReviewOverview }
  | { kind: "not-found" }
  | { kind: "error"; message: string };

const SEVERITY: Record<FindingSeverity, { label: string; className: string }> = {
  HIGH: { label: "High", className: "border-destructive/30 bg-destructive/10 text-destructive" },
  MEDIUM: { label: "Medium", className: "border-primary/40 bg-primary/10 text-primary" },
  LOW: { label: "Low", className: "text-muted-foreground" },
};

const CATEGORY: Record<FindingCategory, string> = {
  CORRECTNESS: "Correctness",
  SECURITY: "Security",
  REQUIREMENTS: "Requirements",
  DESIGN: "Design",
  TESTING: "Testing",
  PERFORMANCE: "Performance",
  MAINTAINABILITY: "Maintainability",
};

type Filter = "OPEN" | FindingStatus | "ALL";

const FILTERS: { value: Filter; label: string }[] = [
  { value: "OPEN", label: "Open" },
  { value: "TASK_CREATED", label: "Task created" },
  { value: "DISMISSED", label: "Dismissed" },
  { value: "ALL", label: "All" },
];

export function ReviewPage() {
  const { projectId = "" } = useParams();
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [review, setReview] = useState<CodeReview | null>(null);
  const [filter, setFilter] = useState<Filter>("OPEN");
  const [reviewing, setReviewing] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([getProject(projectId, controller.signal), getReviews(projectId, controller.signal)])
      .then(([project, overview]) => {
        setState({ kind: "loaded", project, overview });
        setReview(overview.latest);
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
  }, [projectId]);

  const visible = useMemo(
    () => (review ? review.findings.filter((f) => filter === "ALL" || f.status === filter) : []),
    [review, filter],
  );

  if (state.kind === "not-found") return <NotFoundPage />;
  if (state.kind === "loading") {
    return <div aria-hidden="true" className="mx-auto h-96 max-w-5xl animate-pulse rounded-xl border bg-card" />;
  }
  if (state.kind === "error") {
    return (
      <div className="mx-auto max-w-5xl">
        <FormError message={state.message} />
      </div>
    );
  }

  const { project, overview } = state;

  async function refresh() {
    try {
      setState({ kind: "loaded", project, overview: await getReviews(project.id) });
    } catch {
      // The page keeps what it has; the next action reloads again.
    }
  }

  async function runReview() {
    setError(null);
    setReviewing(true);
    try {
      setReview(await generateReview(project.id));
      setFilter("OPEN");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setReviewing(false);
      await refresh();
    }
  }

  async function open(reviewId: string) {
    setError(null);
    try {
      setReview(await getReview(project.id, reviewId));
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function act(finding: ReviewFinding, action: "task" | "dismiss" | "reopen") {
    if (!review) return;
    setError(null);
    setBusy(finding.id);
    try {
      setReview(await actOnFinding(project.id, review.id, finding.id, action));
      await refresh();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <Link
        to={`/projects/${project.id}`}
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" />
        {project.name}
      </Link>

      <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Code review</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            The accepted code checked against the approved requirements and design and common coding standards. Turn a
            finding into a backlog task, or dismiss it.
          </p>
        </div>
        <Button className="shrink-0" disabled={!overview.canReview || reviewing} onClick={runReview}>
          <ScanSearch />
          {reviewing ? "Reviewing…" : overview.reviews.length > 0 ? "Review again" : "Review the code"}
        </Button>
      </section>

      {overview.blocker && (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-start gap-3 text-sm sm:flex-row sm:items-center">
            <p className="text-muted-foreground">{overview.blocker}</p>
            <Button asChild variant="outline" size="sm" className="sm:ml-auto">
              <Link to={`/projects/${project.id}/code`}>Go to code</Link>
            </Button>
          </CardContent>
        </Card>
      )}

      <FormError message={error} />
      {reviewing && (
        <div role="status" className="flex items-center gap-3 rounded-lg border border-primary/30 bg-primary/10 px-4 py-3 text-sm">
          <span className="size-2 animate-pulse rounded-full bg-primary" />
          Reviewing {overview.fileCount} {overview.fileCount === 1 ? "file" : "files"}…
          <span className="ml-auto text-xs text-muted-foreground">This can take a few minutes.</span>
        </div>
      )}

      {review && (
        <section className="space-y-4 rounded-xl border bg-card p-5">
          <div className="space-y-1">
            <h2 className="font-semibold">Review of {formatDateTime(review.createdAt)}</h2>
            <p className="text-xs text-muted-foreground">
              {review.model && `By ${review.model} · `}
              {review.filesReviewed === review.filesTotal
                ? `${review.filesTotal} ${review.filesTotal === 1 ? "file" : "files"} reviewed`
                : `${review.filesReviewed} of ${review.filesTotal} files reviewed (the rest did not fit; application code went first)`}
            </p>
          </div>
          {review.outdated && (
            <p className="flex items-center gap-2 rounded-lg border border-primary/30 bg-primary/10 px-3 py-2 text-sm text-primary">
              <TriangleAlert className="size-4 shrink-0" />
              The accepted code changed since this review. Some findings may already be fixed; review again to check.
            </p>
          )}
          <p className="text-sm">{review.summary}</p>

          <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm">
            <span>
              <span className="font-semibold">{review.counts.total}</span> findings
            </span>
            <span className="text-destructive">{review.counts.high} high</span>
            <span className="text-primary">{review.counts.medium} medium</span>
            <span className="text-muted-foreground">{review.counts.low} low</span>
          </div>

          <div role="group" aria-label="Filter findings" className="flex flex-wrap gap-1.5">
            {FILTERS.map((f) => {
              const count =
                f.value === "ALL"
                  ? review.counts.total
                  : f.value === "OPEN"
                    ? review.counts.open
                    : f.value === "DISMISSED"
                      ? review.counts.dismissed
                      : review.counts.taskCreated;
              return (
                <button
                  key={f.value}
                  type="button"
                  aria-pressed={filter === f.value}
                  onClick={() => setFilter(f.value)}
                  className={cn(
                    "rounded-md border px-2.5 py-1 text-xs",
                    filter === f.value ? "border-primary/40 bg-primary/10 text-primary" : "text-muted-foreground hover:bg-accent",
                  )}
                >
                  {f.label} · {count}
                </button>
              );
            })}
          </div>

          {visible.length === 0 ? (
            <p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
              {review.counts.total === 0 ? "The review found no problems." : "No findings in this view."}
            </p>
          ) : (
            <ul className="space-y-3">
              {visible.map((finding) => (
                <FindingItem
                  key={finding.id}
                  projectId={project.id}
                  finding={finding}
                  busy={busy !== null}
                  onAct={(action) => act(finding, action)}
                />
              ))}
            </ul>
          )}
        </section>
      )}

      {overview.reviews.length > 1 && (
        <section className="space-y-3 rounded-xl border bg-card p-5">
          <h2 className="font-semibold">Earlier reviews</h2>
          <ul className="divide-y rounded-lg border">
            {overview.reviews.map((r) => (
              <li key={r.id}>
                <button
                  type="button"
                  onClick={() => open(r.id)}
                  className={cn(
                    "flex w-full flex-wrap items-center gap-2 px-3 py-2 text-left text-sm hover:bg-accent/60",
                    review?.id === r.id && "bg-accent/60",
                  )}
                >
                  <span className="min-w-0 flex-1">{formatDateTime(r.createdAt)}</span>
                  <span className="text-xs text-muted-foreground">
                    {r.counts.total} findings · {r.counts.open} open · {r.counts.taskCreated} tasks
                  </span>
                  <span className="text-xs text-muted-foreground">{formatRelative(r.createdAt)}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

function FindingItem({
  projectId,
  finding,
  busy,
  onAct,
}: {
  projectId: string;
  finding: ReviewFinding;
  busy: boolean;
  onAct: (action: "task" | "dismiss" | "reopen") => void;
}) {
  const severity = SEVERITY[finding.severity];
  return (
    <li className={cn("space-y-2 rounded-lg border bg-background/40 p-3", finding.status === "DISMISSED" && "opacity-70")}>
      <div className="flex flex-wrap items-start gap-2">
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="outline" className={severity.className}>
              {severity.label}
            </Badge>
            <Badge variant="secondary">{CATEGORY[finding.category]}</Badge>
            {finding.requirementCode && <Badge variant="outline">{finding.requirementCode}</Badge>}
          </div>
          <h3 className="text-sm font-medium">{finding.title}</h3>
          {finding.path && (
            <p className="truncate font-mono text-xs text-muted-foreground">
              {finding.path}
              {finding.line !== null && `:${finding.line}`}
            </p>
          )}
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          {finding.status === "OPEN" && (
            <>
              <Button size="sm" variant="ghost" disabled={busy} onClick={() => onAct("dismiss")}>
                <X />
                Dismiss
              </Button>
              <Button size="sm" disabled={busy} onClick={() => onAct("task")}>
                <ListPlus />
                Create {TYPE_LABEL[finding.taskType].toLowerCase()} task
              </Button>
            </>
          )}
          {finding.status === "DISMISSED" && (
            <>
              <Badge variant="outline">Dismissed</Badge>
              <Button size="sm" variant="ghost" disabled={busy} onClick={() => onAct("reopen")}>
                <RotateCcw />
                Reopen
              </Button>
            </>
          )}
          {finding.status === "TASK_CREATED" &&
            (finding.taskId ? (
              <Button asChild size="sm" variant="outline">
                <Link to={`/projects/${projectId}/tasks?task=${finding.taskId}`}>Task {finding.taskCode}</Link>
              </Button>
            ) : (
              <Badge variant="outline">Task {finding.taskCode} (deleted)</Badge>
            ))}
        </div>
      </div>
      <p className="text-sm text-muted-foreground">{finding.detail}</p>
      <p className="text-sm">
        <span className="font-medium">Fix: </span>
        {finding.suggestion}
      </p>
    </li>
  );
}
