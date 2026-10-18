import { useEffect, useState, type ReactNode } from "react";
import { ArrowLeft, Check, History, Network, Pencil, RotateCcw, Send, Sparkles, TriangleAlert, Undo2 } from "lucide-react";
import { Link, useParams } from "react-router";

import {
  ComponentsEditor,
  DecisionsEditor,
  FlowsEditor,
  OverviewEditor,
  type SectionEditorProps,
} from "@/components/architecture/architecture-editors";
import { ArchitectureHistory } from "@/components/architecture/architecture-history";
import {
  ComponentsSection,
  CoverageSection,
  DecisionsSection,
  FlowsSection,
  OverviewSection,
} from "@/components/architecture/architecture-view";
import { FormError } from "@/components/form-field";
import { StatusBadge } from "@/components/requirements/requirement-labels";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  ApiError,
  changeArchitectureStatus,
  errorMessage,
  generateArchitecture,
  getArchitecture,
  getProject,
  isAbortError,
  updateArchitecture,
  type Architecture,
  type ArchitectureContent,
  type ArchitectureOverview,
  type Project,
  type ReviewStatus,
} from "@/lib/api";
import { formatRelative } from "@/lib/format";
import { NotFoundPage } from "@/pages/not-found-page";

type LoadState =
  | { kind: "loading" }
  | { kind: "loaded"; project: Project; overview: ArchitectureOverview }
  | { kind: "not-found" }
  | { kind: "error"; message: string };

type SectionName = "overview" | "components" | "decisions" | "flows";

const EDITORS: Record<SectionName, (props: SectionEditorProps) => ReactNode> = {
  overview: OverviewEditor,
  components: ComponentsEditor,
  decisions: DecisionsEditor,
  flows: FlowsEditor,
};

export function ArchitecturePage() {
  const { projectId = "" } = useParams();
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [busy, setBusy] = useState<{ message: string; slow: boolean } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [editing, setEditing] = useState<SectionName | null>(null);
  const [confirmRegenerate, setConfirmRegenerate] = useState(false);
  const [showHistory, setShowHistory] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([getProject(projectId, controller.signal), getArchitecture(projectId, controller.signal)])
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
  const architecture = overview.architecture;

  async function run(message: string, action: () => Promise<ArchitectureOverview>, slow = false) {
    setActionError(null);
    setBusy({ message, slow });
    try {
      setState({ kind: "loaded", project, overview: await action() });
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setBusy(null);
    }
  }

  const generate = () => {
    setConfirmRegenerate(false);
    setEditing(null);
    run(
      architecture ? "Regenerating the architecture…" : "Designing the architecture from the approved requirements…",
      () => generateArchitecture(project.id),
      true,
    );
  };

  const moveTo = (current: Architecture, status: ReviewStatus, message: string) =>
    run(message, () => changeArchitectureStatus(project.id, current, status));

  /** Saves one edited section; errors are shown inside the editor, so they are re-thrown. */
  async function save(current: Architecture, content: ArchitectureContent) {
    const next = await updateArchitecture(project.id, current, content);
    setState({ kind: "loaded", project, overview: next });
    setEditing(null);
  }

  const requirementsReady = overview.requirementsTotal > 0 && overview.requirementsApproved === overview.requirementsTotal;

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
            <h1 className="text-2xl font-semibold tracking-tight">Architecture</h1>
            {architecture && <StatusBadge status={architecture.status} />}
            {architecture?.edited && <Badge variant="outline">Edited</Badge>}
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            {architecture
              ? `Revision ${architecture.revision} · updated ${formatRelative(architecture.updatedAt)}${
                  architecture.model ? ` · generated by ${architecture.model}` : ""
                }`
              : "Components, technology decisions and key flows, designed from the approved requirements."}
          </p>
        </div>
        {architecture && (
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
            <WorkflowActions
              status={architecture.status}
              disabled={busy !== null || editing !== null}
              onMove={(status, message) => moveTo(architecture, status, message)}
            />
          </div>
        )}
      </section>

      {!requirementsReady && (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-start gap-3 text-sm sm:flex-row sm:items-center">
            <p className="text-muted-foreground">
              {overview.requirementsTotal === 0
                ? "The architecture is designed from approved requirements, and this project has none yet."
                : `The architecture is designed from approved requirements: ${overview.requirementsApproved} of ${overview.requirementsTotal} are approved.`}{" "}
              {architecture && "Approve them all before regenerating or approving the architecture."}
            </p>
            <Button asChild variant="outline" size="sm" className="sm:ml-auto">
              <Link to={`/projects/${project.id}/requirements`}>Go to requirements</Link>
            </Button>
          </CardContent>
        </Card>
      )}

      {confirmRegenerate && architecture && (
        <div role="alertdialog" className="flex flex-wrap items-center gap-2 rounded-lg border border-primary/30 bg-primary/10 p-3 text-sm">
          <span className="mr-auto">
            Replace the current draft with a new proposal? Revision {architecture.revision}
            {architecture.edited && ", including your edits,"} stays available in the history.
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

      {!architecture ? (
        requirementsReady &&
        busy === null && (
          <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-14 text-center">
            <span className="grid size-12 place-items-center rounded-full bg-primary/10 text-primary">
              <Network className="size-5" />
            </span>
            <p className="font-medium">No architecture yet</p>
            <p className="max-w-md text-sm text-muted-foreground">
              All {overview.requirementsApproved} requirements are approved. Generate a proposal with components,
              technology decisions and sequence diagrams, then review, edit and approve it.
            </p>
            <Button onClick={generate}>
              <Sparkles />
              Generate architecture
            </Button>
          </div>
        )
      ) : (
        <>
          {architecture.status === "APPROVED" && (
            <p className="rounded-lg border border-success/30 bg-success/10 px-4 py-3 text-sm text-success">
              The architecture is approved and locked. Reopen it to make changes.
            </p>
          )}
          {architecture.requirementsChanged && (
            <Notice>
              The approved requirements changed after this architecture was generated. Check that it still fits, then edit
              it{architecture.status === "DRAFT" && requirementsReady ? " or regenerate it" : ""}.
            </Notice>
          )}
          {architecture.uncoveredRequirements.length > 0 && (
            <Notice>
              Not addressed by any component or decision:{" "}
              <span className="font-mono">{architecture.uncoveredRequirements.join(", ")}</span>
            </Notice>
          )}

          {showHistory && (
            <ArchitectureHistory
              projectId={project.id}
              currentRevision={architecture.revision}
              requirements={overview.requirements}
            />
          )}

          {(["overview", "components", "decisions", "flows"] as const).map((section) => {
            if (editing === section) {
              const Editor = EDITORS[section];
              return (
                <Editor
                  key={section}
                  content={architecture.content}
                  requirements={overview.requirements}
                  onSave={(content) => save(architecture, content)}
                  onCancel={() => setEditing(null)}
                />
              );
            }
            const action =
              architecture.status !== "APPROVED" && editing === null ? (
                <Button variant="ghost" size="sm" disabled={busy !== null} onClick={() => setEditing(section)}>
                  <Pencil />
                  Edit
                </Button>
              ) : undefined;
            switch (section) {
              case "overview":
                return <OverviewSection key={section} content={architecture.content} action={action} />;
              case "components":
                return (
                  <ComponentsSection
                    key={section}
                    content={architecture.content}
                    requirements={overview.requirements}
                    action={action}
                  />
                );
              case "decisions":
                return (
                  <DecisionsSection
                    key={section}
                    content={architecture.content}
                    requirements={overview.requirements}
                    action={action}
                  />
                );
              case "flows":
                return <FlowsSection key={section} content={architecture.content} action={action} />;
            }
          })}

          <CoverageSection content={architecture.content} requirements={overview.requirements} />
        </>
      )}
    </div>
  );
}

function Notice({ children }: { children: ReactNode }) {
  return (
    <p className="flex items-start gap-2 rounded-lg border border-primary/30 bg-primary/10 px-4 py-3 text-sm">
      <TriangleAlert className="mt-0.5 size-4 shrink-0 text-primary" />
      <span>{children}</span>
    </p>
  );
}

function WorkflowActions({
  status,
  disabled,
  onMove,
}: {
  status: ReviewStatus;
  disabled: boolean;
  onMove: (status: ReviewStatus, message: string) => void;
}) {
  switch (status) {
    case "DRAFT":
      return (
        <Button size="sm" disabled={disabled} onClick={() => onMove("IN_REVIEW", "Submitting for review…")}>
          <Send />
          Submit for review
        </Button>
      );
    case "IN_REVIEW":
      return (
        <>
          <Button size="sm" variant="ghost" disabled={disabled} onClick={() => onMove("DRAFT", "Returning to draft…")}>
            <Undo2 />
            Request changes
          </Button>
          <Button size="sm" disabled={disabled} onClick={() => onMove("APPROVED", "Approving…")}>
            <Check />
            Approve
          </Button>
        </>
      );
    case "APPROVED":
      return (
        <Button size="sm" variant="ghost" disabled={disabled} onClick={() => onMove("DRAFT", "Reopening…")}>
          <RotateCcw />
          Reopen
        </Button>
      );
  }
}
