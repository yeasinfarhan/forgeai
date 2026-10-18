import { useState } from "react";
import { Check, CheckCheck, ChevronDown, FilePlus2, FileMinus2, FilePen, TriangleAlert, Trash2, X } from "lucide-react";

import { DiffView } from "@/components/code/diff-view";
import { FormError } from "@/components/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  acceptAllChanges,
  decideChange,
  discardChangeset,
  errorMessage,
  type ChangeAction,
  type Changeset,
  type CodeChange,
} from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";

const ACTION: Record<ChangeAction, { label: string; icon: typeof FilePlus2; className: string }> = {
  CREATE: { label: "New", icon: FilePlus2, className: "text-success" },
  UPDATE: { label: "Changed", icon: FilePen, className: "text-primary" },
  DELETE: { label: "Deleted", icon: FileMinus2, className: "text-destructive" },
};

/** A proposed change set: every file as a diff, accepted or rejected one by one. */
export function ChangesetReview({
  projectId,
  changeset,
  onChanged,
}: {
  projectId: string;
  changeset: Changeset;
  /** Called with the updated change set after every decision. */
  onChanged: (changeset: Changeset) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const reviewing = changeset.status === "PENDING";
  const pending = changeset.changes.filter((c) => c.decision === "PENDING").length;

  async function run(action: () => Promise<Changeset>) {
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

  return (
    <section className="space-y-4 rounded-xl border bg-card p-5" aria-label={`Code for ${changeset.taskCode}`}>
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs text-muted-foreground">{changeset.taskCode}</span>
            <h2 className="font-semibold">{changeset.taskTitle}</h2>
            <StatusBadge status={changeset.status} />
          </div>
          <p className="text-sm text-muted-foreground">{changeset.summary}</p>
          <p className="text-xs text-muted-foreground">
            Proposed {formatDateTime(changeset.createdAt)}
            {changeset.model && ` by ${changeset.model}`} · {changeset.changes.length}{" "}
            {changeset.changes.length === 1 ? "file" : "files"}
            {reviewing && ` · ${pending} to review`}
          </p>
        </div>
        {reviewing && (
          <div className="flex gap-2">
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => run(() => discardChangeset(projectId, changeset.id))}>
              <Trash2 />
              Discard the rest
            </Button>
            <Button size="sm" disabled={busy || pending === 0} onClick={() => run(() => acceptAllChanges(projectId, changeset.id))}>
              <CheckCheck />
              Accept all {pending}
            </Button>
          </div>
        )}
      </div>
      <FormError message={error} />
      <ul className="space-y-3">
        {changeset.changes.map((change) => (
          <ChangeItem
            key={change.id}
            change={change}
            reviewing={reviewing}
            busy={busy}
            onDecide={(decision) => run(() => decideChange(projectId, changeset.id, change.id, decision))}
          />
        ))}
      </ul>
    </section>
  );
}

function StatusBadge({ status }: { status: Changeset["status"] }) {
  if (status === "PENDING") return <Badge variant="outline" className="border-primary/40 bg-primary/10 text-primary">In review</Badge>;
  if (status === "COMPLETED") return <Badge variant="success">Reviewed</Badge>;
  return <Badge variant="outline">Discarded</Badge>;
}

function ChangeItem({
  change,
  reviewing,
  busy,
  onDecide,
}: {
  change: CodeChange;
  reviewing: boolean;
  busy: boolean;
  onDecide: (decision: "accept" | "reject") => void;
}) {
  const [open, setOpen] = useState(change.decision === "PENDING");
  const action = ACTION[change.action];
  const Icon = action.icon;
  return (
    <li className={cn("space-y-2 rounded-lg border bg-background/40 p-3", change.decision === "REJECTED" && "opacity-70")}>
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          className="flex min-w-0 flex-1 items-center gap-2 text-left"
        >
          <ChevronDown className={cn("size-4 shrink-0 transition-transform", !open && "-rotate-90")} />
          <Icon className={cn("size-4 shrink-0", action.className)} aria-label={action.label} />
          <span className="truncate font-mono text-sm">{change.path}</span>
        </button>
        {change.decision === "ACCEPTED" && <Badge variant="success">Accepted</Badge>}
        {change.decision === "REJECTED" && <Badge variant="outline">Rejected</Badge>}
        {reviewing && change.decision === "PENDING" && (
          <>
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => onDecide("reject")}>
              <X />
              Reject
            </Button>
            <Button size="sm" disabled={busy || change.stale} onClick={() => onDecide("accept")}>
              <Check />
              Accept
            </Button>
          </>
        )}
      </div>
      <p className="text-xs text-muted-foreground">{change.explanation}</p>
      {change.stale && reviewing && change.decision === "PENDING" && (
        <p className="flex items-center gap-2 text-xs text-primary">
          <TriangleAlert className="size-3.5" />
          This file changed after the proposal was made. Reject it and generate the task's code again.
        </p>
      )}
      {open && <DiffView before={change.baseContent} after={change.newContent} />}
    </li>
  );
}
