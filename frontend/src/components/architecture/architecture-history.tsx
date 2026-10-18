import { useEffect, useState } from "react";
import { Eye, X } from "lucide-react";

import { ArchitectureView } from "@/components/architecture/architecture-view";
import { FormError } from "@/components/form-field";
import { statusLabel } from "@/components/requirements/requirement-labels";
import { Button } from "@/components/ui/button";
import {
  errorMessage,
  getArchitectureHistory,
  getArchitectureRevision,
  type ArchitectureRevisionDetail,
  type ArchitectureRevisionSummary,
  type RequirementRef,
} from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";

const CHANGE_LABEL: Record<ArchitectureRevisionSummary["change"], string> = {
  GENERATED: "Generated",
  EDITED: "Edited",
  STATUS_CHANGED: "Status changed",
};

interface ArchitectureHistoryProps {
  projectId: string;
  /** The current revision; the list reloads whenever it changes. */
  currentRevision: number;
  requirements: RequirementRef[];
}

/** Every revision of the architecture; earlier ones can be opened read-only. */
export function ArchitectureHistory({ projectId, currentRevision, requirements }: ArchitectureHistoryProps) {
  const [revisions, setRevisions] = useState<ArchitectureRevisionSummary[] | null>(null);
  const [viewing, setViewing] = useState<ArchitectureRevisionDetail | null>(null);
  const [loadingRevision, setLoadingRevision] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getArchitectureHistory(projectId)
      .then((list) => {
        if (!cancelled) setRevisions(list);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(errorMessage(err));
      });
    return () => {
      cancelled = true;
    };
  }, [projectId, currentRevision]);

  async function open(revision: number) {
    setError(null);
    setLoadingRevision(revision);
    try {
      setViewing(await getArchitectureRevision(projectId, revision));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoadingRevision(null);
    }
  }

  return (
    <section className="space-y-3 rounded-xl border bg-card p-5">
      <h2 className="font-semibold">History</h2>
      <FormError message={error} />
      {!revisions ? (
        <div aria-hidden="true" className="h-24 animate-pulse rounded-lg bg-muted" />
      ) : (
        <ol className="divide-y rounded-lg border">
          {revisions.map((revision) => (
            <li
              key={revision.revision}
              className={cn(
                "flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2 text-sm",
                viewing?.revision === revision.revision && "bg-accent/60",
              )}
            >
              <span className="font-mono text-xs text-muted-foreground">r{revision.revision}</span>
              <span>
                {CHANGE_LABEL[revision.change]}
                {revision.change === "STATUS_CHANGED" && <> → {statusLabel(revision.status)}</>}
                {revision.change === "GENERATED" && revision.model && (
                  <span className="text-muted-foreground"> by {revision.model}</span>
                )}
              </span>
              <span className="text-xs text-muted-foreground">{formatDateTime(revision.createdAt)}</span>
              <span className="ml-auto">
                {revision.revision === currentRevision ? (
                  <span className="text-xs text-muted-foreground">Current</span>
                ) : (
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={loadingRevision !== null}
                    onClick={() => open(revision.revision)}
                  >
                    <Eye />
                    {loadingRevision === revision.revision ? "Opening…" : "View"}
                  </Button>
                )}
              </span>
            </li>
          ))}
        </ol>
      )}

      {viewing && (
        <div className="space-y-3 rounded-lg border border-primary/30 p-3">
          <div className="flex items-center gap-2 text-sm">
            <span>
              Revision {viewing.revision} · {statusLabel(viewing.status)} · {formatDateTime(viewing.createdAt)}
              <span className="text-muted-foreground"> (read-only)</span>
            </span>
            <Button variant="ghost" size="sm" className="ml-auto" onClick={() => setViewing(null)}>
              <X />
              Close
            </Button>
          </div>
          <ArchitectureView content={viewing.content} requirements={requirements} />
        </div>
      )}
    </section>
  );
}
