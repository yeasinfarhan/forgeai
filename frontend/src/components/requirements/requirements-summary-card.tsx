import { useEffect, useState } from "react";
import { ArrowRight, ListChecks } from "lucide-react";
import { Link } from "react-router";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { getRequirements, isAbortError, type RequirementsOverview } from "@/lib/api";

/** Requirements progress on the project page, linking to the full Requirements page. */
export function RequirementsSummaryCard({ projectId }: { projectId: string }) {
  const [overview, setOverview] = useState<RequirementsOverview | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getRequirements(projectId, controller.signal)
      .then(setOverview)
      .catch((error: unknown) => {
        if (!isAbortError(error)) setOverview(null);
      });
    return () => controller.abort();
  }, [projectId]);

  const counts = overview?.counts;
  const approvedPercent = counts && counts.total > 0 ? Math.round((counts.approved / counts.total) * 100) : 0;

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4">
        <div className="space-y-1.5">
          <CardTitle className="flex items-center gap-2">
            <ListChecks className="size-4 text-primary" />
            Requirements
          </CardTitle>
          <CardDescription>Functional and non-functional requirements derived from the brief.</CardDescription>
        </div>
        <Button asChild size="sm" variant={counts && counts.total > 0 ? "outline" : "default"}>
          <Link to={`/projects/${projectId}/requirements`}>
            {counts && counts.total > 0 ? "Open" : "Start"}
            <ArrowRight />
          </Link>
        </Button>
      </CardHeader>
      <CardContent>
        {!overview ? (
          <div aria-hidden="true" className="h-10 animate-pulse rounded-lg bg-muted" />
        ) : overview.counts.total === 0 ? (
          <p className="text-sm text-muted-foreground">
            {overview.briefReady
              ? "The brief is ready. Generate the first draft of requirements."
              : "Available once idea intake has produced a brief."}
          </p>
        ) : (
          <div className="space-y-2">
            <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm">
              <span>
                <span className="font-semibold">{overview.counts.total}</span> total
              </span>
              <span className="text-muted-foreground">{overview.counts.draft} draft</span>
              <span className="text-primary">{overview.counts.inReview} in review</span>
              <span className="text-success">{overview.counts.approved} approved</span>
            </div>
            <div
              className="h-1.5 overflow-hidden rounded-full bg-muted"
              role="progressbar"
              aria-label="Approved requirements"
              aria-valuenow={approvedPercent}
              aria-valuemin={0}
              aria-valuemax={100}
            >
              <div className="h-full rounded-full bg-success transition-all" style={{ width: `${approvedPercent}%` }} />
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
