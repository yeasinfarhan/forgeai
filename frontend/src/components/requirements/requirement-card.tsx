import { useState } from "react";
import { Check, ChevronDown, History, Pencil, RotateCcw, Send, Trash2, Undo2 } from "lucide-react";

import { FormError } from "@/components/form-field";
import { RequirementForm } from "@/components/requirements/requirement-form";
import { PriorityBadge, StatusBadge, statusLabel } from "@/components/requirements/requirement-labels";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  changeRequirementStatus,
  deleteRequirement,
  errorMessage,
  getRequirementHistory,
  updateRequirement,
  type Requirement,
  type RequirementRevision,
  type RequirementStatus,
} from "@/lib/api";
import { formatDateTime, formatRelative } from "@/lib/format";

interface RequirementCardProps {
  projectId: string;
  requirement: Requirement;
  onChanged: (requirement: Requirement) => void;
  onDeleted: (requirementId: string) => void;
}

/** One requirement: read view, inline editing, workflow actions, history and deletion. */
export function RequirementCard({ projectId, requirement, onChanged, onDeleted }: RequirementCardProps) {
  const [mode, setMode] = useState<"view" | "edit" | "confirm-delete">("view");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [history, setHistory] = useState<RequirementRevision[] | null>(null);
  const [showHistory, setShowHistory] = useState(false);

  async function run(action: () => Promise<void>) {
    setError(null);
    setBusy(true);
    try {
      await action();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  const moveTo = (status: RequirementStatus) =>
    run(async () => {
      onChanged(await changeRequirementStatus(projectId, requirement, status));
      setHistory(null);
    });

  async function toggleHistory() {
    if (showHistory) {
      setShowHistory(false);
      return;
    }
    setShowHistory(true);
    if (!history) {
      await run(async () => setHistory(await getRequirementHistory(projectId, requirement.id)));
    }
  }

  if (mode === "edit") {
    return (
      <li className="rounded-xl border border-primary/40 bg-card p-5">
        <p className="mb-4 font-mono text-xs text-muted-foreground">Editing {requirement.code}</p>
        <RequirementForm
          initial={{
            category: requirement.category ?? "",
            title: requirement.title,
            description: requirement.description,
            acceptanceCriteria: requirement.acceptanceCriteria,
            priority: requirement.priority,
          }}
          submitLabel="Save changes"
          onCancel={() => setMode("view")}
          onSubmit={async (input) => {
            onChanged(await updateRequirement(projectId, requirement, input));
            setHistory(null);
            setMode("view");
          }}
        />
      </li>
    );
  }

  const editable = requirement.status !== "APPROVED";

  return (
    <li className="space-y-3 rounded-xl border bg-card p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs text-muted-foreground">{requirement.code}</span>
            {requirement.category && <Badge variant="secondary">{requirement.category}</Badge>}
            <PriorityBadge priority={requirement.priority} />
            <StatusBadge status={requirement.status} />
            {requirement.source === "USER" && <Badge variant="outline">Added by you</Badge>}
            {requirement.source === "AI" && requirement.edited && <Badge variant="outline">Edited</Badge>}
          </div>
          <h3 className="font-medium">{requirement.title}</h3>
        </div>
        <WorkflowActions status={requirement.status} busy={busy} onMove={moveTo} />
      </div>

      <p className="text-sm leading-relaxed text-muted-foreground">{requirement.description}</p>

      {requirement.acceptanceCriteria.length > 0 && (
        <div>
          <p className="mb-1 text-xs font-semibold tracking-wider text-muted-foreground uppercase">Acceptance criteria</p>
          <ul className="list-disc space-y-0.5 pl-5 text-sm marker:text-muted-foreground">
            {requirement.acceptanceCriteria.map((criterion, index) => (
              <li key={index}>{criterion}</li>
            ))}
          </ul>
        </div>
      )}

      <FormError message={error} />

      {mode === "confirm-delete" && (
        <div role="alertdialog" className="flex flex-wrap items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm">
          <span className="mr-auto">Delete {requirement.code}? Its history is deleted too.</span>
          <Button
            size="sm"
            className="bg-destructive text-white hover:bg-destructive/90"
            disabled={busy}
            onClick={() =>
              run(async () => {
                await deleteRequirement(projectId, requirement.id);
                onDeleted(requirement.id);
              })
            }
          >
            Delete
          </Button>
          <Button size="sm" variant="ghost" disabled={busy} onClick={() => setMode("view")}>
            Cancel
          </Button>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-1 border-t pt-3 text-xs text-muted-foreground">
        <span className="mr-auto">
          Revision {requirement.revision} · updated {formatRelative(requirement.updatedAt)}
        </span>
        <Button variant="ghost" size="sm" onClick={toggleHistory} aria-expanded={showHistory}>
          <History />
          History
          <ChevronDown className={showHistory ? "rotate-180 transition-transform" : "transition-transform"} />
        </Button>
        {editable && (
          <>
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
          </>
        )}
      </div>

      {showHistory && <HistoryList history={history} />}
    </li>
  );
}

function WorkflowActions({
  status,
  busy,
  onMove,
}: {
  status: RequirementStatus;
  busy: boolean;
  onMove: (status: RequirementStatus) => void;
}) {
  switch (status) {
    case "DRAFT":
      return (
        <Button size="sm" variant="outline" disabled={busy} onClick={() => onMove("IN_REVIEW")}>
          <Send />
          Submit for review
        </Button>
      );
    case "IN_REVIEW":
      return (
        <div className="flex gap-2">
          <Button size="sm" variant="ghost" disabled={busy} onClick={() => onMove("DRAFT")}>
            <Undo2 />
            Request changes
          </Button>
          <Button size="sm" disabled={busy} onClick={() => onMove("APPROVED")}>
            <Check />
            Approve
          </Button>
        </div>
      );
    case "APPROVED":
      return (
        <Button size="sm" variant="ghost" disabled={busy} onClick={() => onMove("DRAFT")}>
          <RotateCcw />
          Reopen
        </Button>
      );
  }
}

const CHANGE_LABEL: Record<RequirementRevision["change"], string> = {
  CREATED: "Created",
  EDITED: "Edited",
  STATUS_CHANGED: "Status changed",
};

function HistoryList({ history }: { history: RequirementRevision[] | null }) {
  if (!history) {
    return <div aria-hidden="true" className="h-16 animate-pulse rounded-lg bg-muted" />;
  }
  return (
    <ol className="space-y-2 rounded-lg border bg-background/40 p-3">
      {history.map((entry) => (
        <li key={entry.revision} className="text-sm">
          <p className="text-xs text-muted-foreground">
            <span className="font-mono">r{entry.revision}</span> · {CHANGE_LABEL[entry.change]}
            {entry.change === "STATUS_CHANGED" && <> → {statusLabel(entry.status)}</>} · {formatDateTime(entry.createdAt)}
          </p>
          {entry.change !== "STATUS_CHANGED" && (
            <p className="mt-0.5">
              <span className="font-medium">{entry.title}</span>
              <span className="text-muted-foreground"> — {entry.description}</span>
            </p>
          )}
        </li>
      ))}
    </ol>
  );
}
