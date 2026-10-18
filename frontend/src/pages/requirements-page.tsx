import { useEffect, useState } from "react";
import { ArrowLeft, CheckCheck, ListChecks, Plus, Send, Sparkles } from "lucide-react";
import { Link, useParams } from "react-router";

import { FormError } from "@/components/form-field";
import { RequirementCard } from "@/components/requirements/requirement-card";
import { RequirementForm } from "@/components/requirements/requirement-form";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  ApiError,
  bulkChangeRequirementStatus,
  createRequirement,
  errorMessage,
  generateRequirements,
  getProject,
  getRequirements,
  isAbortError,
  type Project,
  type Requirement,
  type RequirementsOverview,
  type RequirementStatus,
  type RequirementType,
} from "@/lib/api";
import { cn } from "@/lib/utils";
import { NotFoundPage } from "@/pages/not-found-page";

type Filter = "ALL" | RequirementStatus;

type LoadState =
  | { kind: "loading" }
  | { kind: "loaded"; project: Project; overview: RequirementsOverview }
  | { kind: "not-found" }
  | { kind: "error"; message: string };

export function RequirementsPage() {
  const { projectId = "" } = useParams();
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [filter, setFilter] = useState<Filter>("ALL");
  const [busyMessage, setBusyMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([getProject(projectId, controller.signal), getRequirements(projectId, controller.signal)])
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
    return <div aria-hidden="true" className="mx-auto h-96 max-w-4xl animate-pulse rounded-xl border bg-card" />;
  }
  if (state.kind === "error") {
    return (
      <div className="mx-auto max-w-4xl">
        <FormError message={state.message} />
      </div>
    );
  }

  const { project, overview } = state;
  const setOverview = (next: RequirementsOverview) => setState({ kind: "loaded", project, overview: next });

  /** Re-derives counts locally after a single-item change, so the page doesn't refetch. */
  function replaceRequirements(requirements: Requirement[]) {
    const counts = {
      draft: requirements.filter((r) => r.status === "DRAFT").length,
      inReview: requirements.filter((r) => r.status === "IN_REVIEW").length,
      approved: requirements.filter((r) => r.status === "APPROVED").length,
      total: requirements.length,
    };
    setOverview({
      ...overview,
      requirements,
      counts,
      allApproved: requirements.length > 0 && counts.approved === requirements.length,
    });
  }

  async function runBulk(message: string, action: () => Promise<RequirementsOverview>) {
    setActionError(null);
    setBusyMessage(message);
    try {
      setOverview(await action());
    } catch (error) {
      setActionError(errorMessage(error));
      // The server may have finished or changed something anyway (e.g. a run that outlived the
      // browser's wait): show its current state alongside the error.
      await getRequirements(project.id).then(setOverview).catch(() => undefined);
    } finally {
      setBusyMessage(null);
    }
  }

  const visible = overview.requirements.filter((r) => filter === "ALL" || r.status === filter);
  const functional = visible.filter((r) => r.type === "FUNCTIONAL");
  const nonFunctional = visible.filter((r) => r.type === "NON_FUNCTIONAL");
  const hasRequirements = overview.counts.total > 0;

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <Link
        to={`/projects/${project.id}`}
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" />
        {project.name}
      </Link>

      <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Requirements</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            What the software must do, derived from the project brief. Approved requirements feed the architecture phase.
          </p>
        </div>
        <div className="flex shrink-0 gap-2">
          <Button variant="outline" size="sm" onClick={() => setAdding(true)} disabled={adding || busyMessage !== null}>
            <Plus />
            Add
          </Button>
          <Button
            size="sm"
            disabled={!overview.briefReady || busyMessage !== null}
            title={overview.briefReady ? undefined : "Write the project brief first"}
            onClick={() =>
              runBulk(
                hasRequirements ? "Regenerating draft requirements…" : "Generating requirements from the brief…",
                () => generateRequirements(project.id),
              )
            }
          >
            <Sparkles />
            {hasRequirements ? "Regenerate drafts" : "Generate"}
          </Button>
        </div>
      </section>

      {!overview.briefReady && (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-start gap-3 text-sm sm:flex-row sm:items-center">
            <p className="text-muted-foreground">
              Requirements are generated from the project brief. Finish <strong>Idea intake</strong> on the project page
              first, or add requirements by hand.
            </p>
            <Button asChild variant="outline" size="sm" className="sm:ml-auto">
              <Link to={`/projects/${project.id}`}>Go to idea intake</Link>
            </Button>
          </CardContent>
        </Card>
      )}

      {hasRequirements && (
        <p className="text-xs text-muted-foreground">
          Regenerating replaces only AI drafts you haven't touched. Approved, in-review, edited and hand-written
          requirements are kept, and the AI is told about them.
        </p>
      )}

      <FormError message={actionError} />
      {busyMessage && (
        <div role="status" className="flex items-center gap-3 rounded-lg border border-primary/30 bg-primary/10 px-4 py-3 text-sm">
          <span className="size-2 animate-pulse rounded-full bg-primary" />
          {busyMessage}
          <span className="ml-auto text-xs text-muted-foreground">This can take up to a minute.</span>
        </div>
      )}

      {adding && (
        <AddRequirement
          projectId={project.id}
          onCancel={() => setAdding(false)}
          onAdded={(requirement) => {
            replaceRequirements(sortRequirements([...overview.requirements, requirement]));
            setAdding(false);
          }}
        />
      )}

      {hasRequirements && (
        <div className="flex flex-col gap-3 rounded-xl border bg-card p-3 sm:flex-row sm:items-center">
          <div className="flex flex-wrap gap-1" role="tablist" aria-label="Filter by status">
            {(
              [
                ["ALL", "All", overview.counts.total],
                ["DRAFT", "Draft", overview.counts.draft],
                ["IN_REVIEW", "In review", overview.counts.inReview],
                ["APPROVED", "Approved", overview.counts.approved],
              ] as const
            ).map(([value, label, count]) => (
              <button
                key={value}
                role="tab"
                aria-selected={filter === value}
                onClick={() => setFilter(value)}
                className={cn(
                  "rounded-md px-3 py-1.5 text-sm transition-colors",
                  filter === value ? "bg-accent font-medium" : "text-muted-foreground hover:bg-accent/60",
                )}
              >
                {label} <span className="ml-1 font-mono text-xs text-muted-foreground">{count}</span>
              </button>
            ))}
          </div>
          <div className="flex gap-2 sm:ml-auto">
            {overview.counts.draft > 0 && (
              <Button
                variant="outline"
                size="sm"
                disabled={busyMessage !== null}
                onClick={() =>
                  runBulk("Submitting drafts for review…", () =>
                    bulkChangeRequirementStatus(project.id, "DRAFT", "IN_REVIEW"),
                  )
                }
              >
                <Send />
                Submit all drafts
              </Button>
            )}
            {overview.counts.inReview > 0 && (
              <Button
                size="sm"
                disabled={busyMessage !== null}
                onClick={() =>
                  runBulk("Approving…", () => bulkChangeRequirementStatus(project.id, "IN_REVIEW", "APPROVED"))
                }
              >
                <CheckCheck />
                Approve all in review
              </Button>
            )}
          </div>
        </div>
      )}

      {overview.allApproved && (
        <p className="rounded-lg border border-success/30 bg-success/10 px-4 py-3 text-sm text-success">
          All {overview.counts.total} requirements are approved. They are ready for the{" "}
          <Link to={`/projects/${project.id}/architecture`} className="underline hover:no-underline">
            architecture
          </Link>
          .
        </p>
      )}

      {!hasRequirements && overview.briefReady && !busyMessage && (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-14 text-center">
          <span className="grid size-12 place-items-center rounded-full bg-primary/10 text-primary">
            <ListChecks className="size-5" />
          </span>
          <p className="font-medium">No requirements yet</p>
          <p className="max-w-sm text-sm text-muted-foreground">
            Generate a first draft from the brief, then review, edit and approve each requirement.
          </p>
        </div>
      )}

      <RequirementGroup
        title="Functional requirements"
        items={functional}
        projectId={project.id}
        onChanged={(updated) =>
          replaceRequirements(overview.requirements.map((r) => (r.id === updated.id ? updated : r)))
        }
        onDeleted={(id) => replaceRequirements(overview.requirements.filter((r) => r.id !== id))}
      />
      <RequirementGroup
        title="Non-functional requirements"
        items={nonFunctional}
        projectId={project.id}
        onChanged={(updated) =>
          replaceRequirements(overview.requirements.map((r) => (r.id === updated.id ? updated : r)))
        }
        onDeleted={(id) => replaceRequirements(overview.requirements.filter((r) => r.id !== id))}
      />
    </div>
  );
}

function RequirementGroup({
  title,
  items,
  projectId,
  onChanged,
  onDeleted,
}: {
  title: string;
  items: Requirement[];
  projectId: string;
  onChanged: (requirement: Requirement) => void;
  onDeleted: (id: string) => void;
}) {
  if (items.length === 0) return null;
  return (
    <section className="space-y-3">
      <h2 className="text-sm font-semibold tracking-wider text-muted-foreground uppercase">
        {title} <span className="font-mono">({items.length})</span>
      </h2>
      <ul className="space-y-3">
        {items.map((requirement) => (
          <RequirementCard
            key={requirement.id}
            projectId={projectId}
            requirement={requirement}
            onChanged={onChanged}
            onDeleted={onDeleted}
          />
        ))}
      </ul>
    </section>
  );
}

function AddRequirement({
  projectId,
  onAdded,
  onCancel,
}: {
  projectId: string;
  onAdded: (requirement: Requirement) => void;
  onCancel: () => void;
}) {
  const [type, setType] = useState<RequirementType>("FUNCTIONAL");
  return (
    <Card className="border-primary/40">
      <CardContent>
        <RequirementForm
          submitLabel="Add requirement"
          onCancel={onCancel}
          onSubmit={async (input) => onAdded(await createRequirement(projectId, type, input))}
          header={
            <div className="flex gap-2" role="radiogroup" aria-label="Requirement type">
              {(
                [
                  ["FUNCTIONAL", "Functional"],
                  ["NON_FUNCTIONAL", "Non-functional"],
                ] as const
              ).map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  role="radio"
                  aria-checked={type === value}
                  onClick={() => setType(value)}
                  className={cn(
                    "rounded-md border px-3 py-1.5 text-sm transition-colors",
                    type === value ? "border-primary bg-primary/10 text-primary" : "text-muted-foreground hover:bg-accent",
                  )}
                >
                  {label}
                </button>
              ))}
            </div>
          }
        />
      </CardContent>
    </Card>
  );
}

function sortRequirements(requirements: Requirement[]) {
  return [...requirements].sort((a, b) =>
    a.type === b.type ? a.code.localeCompare(b.code, undefined, { numeric: true }) : a.type.localeCompare(b.type),
  );
}
