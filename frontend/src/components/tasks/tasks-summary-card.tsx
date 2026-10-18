import { useEffect, useState } from "react";
import { ArrowRight, SquareKanban } from "lucide-react";
import { Link } from "react-router";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { getBacklog, isAbortError, type BacklogOverview } from "@/lib/api";

/** Backlog progress on the project page, linking to the tasks page. */
export function TasksSummaryCard({ projectId }: { projectId: string }) {
  const [overview, setOverview] = useState<BacklogOverview | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    getBacklog(projectId, controller.signal)
      .then(setOverview)
      .catch((error: unknown) => {
        if (!isAbortError(error)) setFailed(true);
      });
    return () => controller.abort();
  }, [projectId]);

  const counts = overview?.counts;
  const percent = counts && counts.points > 0 ? Math.round((counts.pointsDone / counts.points) * 100) : 0;

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4">
        <div className="space-y-1.5">
          <CardTitle className="flex items-center gap-2">
            <SquareKanban className="size-4 text-primary" />
            Development tasks
          </CardTitle>
          <CardDescription>The backlog and board, linked to requirements and design.</CardDescription>
        </div>
        <Button asChild size="sm" variant={counts && counts.total > 0 ? "outline" : "default"}>
          <Link to={`/projects/${projectId}/tasks`}>
            {counts && counts.total > 0 ? "Open" : "Start"}
            <ArrowRight />
          </Link>
        </Button>
      </CardHeader>
      <CardContent>
        {failed ? (
          <p className="text-sm text-destructive">The backlog could not be loaded.</p>
        ) : !overview ? (
          <div aria-hidden="true" className="h-10 animate-pulse rounded-lg bg-muted" />
        ) : overview.counts.total === 0 ? (
          <p className="text-sm text-muted-foreground">
            {overview.canGenerate ? "The design is approved. Generate the backlog." : overview.blocker}
          </p>
        ) : (
          <div className="space-y-2">
            <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm">
              <span>
                <span className="font-semibold">{overview.counts.total}</span> tasks
              </span>
              <span className="text-muted-foreground">{overview.counts.todo} to do</span>
              <span className="text-primary">{overview.counts.inProgress} in progress</span>
              <span className="text-success">{overview.counts.done} done</span>
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
      </CardContent>
    </Card>
  );
}
