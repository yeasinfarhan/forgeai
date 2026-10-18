import { useEffect, useState, type ReactNode } from "react";
import { ArrowLeft, History, Sparkles, TriangleAlert, type LucideIcon } from "lucide-react";
import { Link, useParams } from "react-router";

import { DesignHistory } from "@/components/design/design-history";
import { ExportSection } from "@/components/design/export-panel";
import { FindingsPanel } from "@/components/design/findings-panel";
import { ReviewActions } from "@/components/design/review-actions";
import { FormError } from "@/components/form-field";
import { StatusBadge } from "@/components/requirements/requirement-labels";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  ApiError,
  changeDesignStatus,
  designExportPath,
  errorMessage,
  generateDesign,
  getDesign,
  getProject,
  isAbortError,
  updateDesign,
  type DesignStage,
  type DesignState,
  type Project,
  type ReviewStatus,
  type StageOverview,
} from "@/lib/api";
import { formatRelative } from "@/lib/format";
import { NotFoundPage } from "@/pages/not-found-page";

/** What a stage page hands to its sections. */
export interface StageSectionsProps<T, O> {
  overview: O;
  design: DesignState<T>;
  /** Editing is possible (not approved, nothing else going on). */
  editable: boolean;
  /** Which section is being edited, by a name the page chooses. */
  editing: string | null;
  setEditing: (section: string | null) => void;
  /** Saves new content; errors are re-thrown so the editor can show them. */
  save: (content: T) => Promise<void>;
}

interface DesignStagePageProps<T, O extends StageOverview<T>> {
  stage: DesignStage;
  title: string;
  description: string;
  icon: LucideIcon;
  /** Shown before the first generation. */
  emptyText: string;
  busyText: string;
  /** Where to go when an earlier stage blocks this one. */
  previousStage: { label: string; path: string };
  exportTitle: string;
  exportDescription: string;
  sections: (props: StageSectionsProps<T, O>) => ReactNode;
  /** An earlier revision, read-only. */
  revision: (content: T, overview: O) => ReactNode;
}

type LoadState<O> =
  | { kind: "loading" }
  | { kind: "loaded"; project: Project; overview: O }
  | { kind: "not-found" }
  | { kind: "error"; message: string };

/**
 * Page shell shared by the database and API design: loading, generation, review workflow,
 * regeneration with confirmation, notices, consistency findings, export and history. The
 * stage-specific content and editors come from {@code sections}.
 */
export function DesignStagePage<T, O extends StageOverview<T>>(props: DesignStagePageProps<T, O>) {
  const { stage } = props;
  const { projectId = "" } = useParams();
  const [state, setState] = useState<LoadState<O>>({ kind: "loading" });
  const [busy, setBusy] = useState<{ message: string; slow: boolean } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [confirmRegenerate, setConfirmRegenerate] = useState(false);
  const [showHistory, setShowHistory] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([getProject(projectId, controller.signal), getDesign<O>(projectId, stage, controller.signal)])
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
  }, [projectId, stage]);

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
  const design = overview.design;
  const Icon = props.icon;

  async function run(message: string, action: () => Promise<O>, slow = false) {
    setActionError(null);
    setBusy({ message, slow });
    try {
      setState({ kind: "loaded", project, overview: await action() });
    } catch (error) {
      setActionError(errorMessage(error));
      // The server may have finished or changed something anyway (e.g. a run that outlived the
      // browser's wait): show its current state alongside the error.
      await getDesign<O>(project.id, stage)
        .then((latest) => setState({ kind: "loaded", project, overview: latest }))
        .catch(() => undefined);
    } finally {
      setBusy(null);
    }
  }

  const generate = () => {
    setConfirmRegenerate(false);
    setEditing(null);
    run(props.busyText, () => generateDesign<O>(project.id, stage), true);
  };

  const moveTo = (current: DesignState<T>, status: ReviewStatus, message: string) =>
    run(message, () => changeDesignStatus<O, T>(project.id, stage, current, status));

  async function save(current: DesignState<T>, content: T) {
    const next = await updateDesign<O, T>(project.id, stage, current, content);
    setState({ kind: "loaded", project, overview: next });
    setEditing(null);
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
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">{props.title}</h1>
            {design && <StatusBadge status={design.status} />}
            {design?.edited && <Badge variant="outline">Edited</Badge>}
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            {design
              ? `Revision ${design.revision} · updated ${formatRelative(design.updatedAt)}${
                  design.model ? ` · generated by ${design.model}` : ""
                }`
              : props.description}
          </p>
        </div>
        {design && (
          <div className="flex shrink-0 flex-wrap gap-2">
            <Button variant="ghost" size="sm" aria-expanded={showHistory} onClick={() => setShowHistory((v) => !v)}>
              <History />
              History
            </Button>
            {overview.canGenerate && (
              <Button variant="outline" size="sm" disabled={busy !== null} onClick={() => setConfirmRegenerate(true)}>
                <Sparkles />
                Regenerate
              </Button>
            )}
            <ReviewActions
              status={design.status}
              disabled={busy !== null || editing !== null}
              onMove={(status, message) => moveTo(design, status, message)}
            />
          </div>
        )}
      </section>

      {!design && overview.blocker && (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-start gap-3 text-sm sm:flex-row sm:items-center">
            <p className="text-muted-foreground">{overview.blocker}</p>
            <Button asChild variant="outline" size="sm" className="sm:ml-auto">
              <Link to={props.previousStage.path}>{props.previousStage.label}</Link>
            </Button>
          </CardContent>
        </Card>
      )}

      {confirmRegenerate && design && (
        <div role="alertdialog" className="flex flex-wrap items-center gap-2 rounded-lg border border-primary/30 bg-primary/10 p-3 text-sm">
          <span className="mr-auto">
            Replace the current draft with a new proposal? Revision {design.revision}
            {design.edited && ", including your edits,"} stays available in the history.
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

      {!design ? (
        overview.canGenerate &&
        busy === null && (
          <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-14 text-center">
            <span className="grid size-12 place-items-center rounded-full bg-primary/10 text-primary">
              <Icon className="size-5" />
            </span>
            <p className="font-medium">Nothing generated yet</p>
            <p className="max-w-md text-sm text-muted-foreground">{props.emptyText}</p>
            <Button onClick={generate}>
              <Sparkles />
              Generate
            </Button>
          </div>
        )
      ) : (
        <>
          {design.status === "APPROVED" && (
            <p className="rounded-lg border border-success/30 bg-success/10 px-4 py-3 text-sm text-success">
              Approved and locked. Reopen it to make changes.
            </p>
          )}
          {design.inputsChanged && (
            <p className="flex items-start gap-2 rounded-lg border border-primary/30 bg-primary/10 px-4 py-3 text-sm">
              <TriangleAlert className="mt-0.5 size-4 shrink-0 text-primary" />
              <span>
                The earlier stages this was generated from have changed since. Check that it still fits, then edit it
                {overview.canGenerate ? " or regenerate it" : ""}.
              </span>
            </p>
          )}

          <FindingsPanel findings={overview.findings} />

          {showHistory && (
            <DesignHistory<T>
              projectId={project.id}
              stage={stage}
              currentRevision={design.revision}
              render={(content) => props.revision(content, overview)}
            />
          )}

          {props.sections({
            overview,
            design,
            editable: design.status !== "APPROVED" && busy === null,
            editing,
            setEditing,
            save: (content) => save(design, content),
          })}

          <ExportSection
            key={design.revision}
            path={designExportPath(project.id, stage)}
            title={props.exportTitle}
            description={props.exportDescription}
          />
        </>
      )}
    </div>
  );
}
