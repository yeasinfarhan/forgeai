import { useEffect, useState } from "react";
import { ArrowRight, ScanSearch } from "lucide-react";
import { Link } from "react-router";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { getReviews, isAbortError, type ReviewOverview } from "@/lib/api";
import { formatRelative } from "@/lib/format";

/** Code review status on the project page, linking to the review page. */
export function ReviewSummaryCard({ projectId }: { projectId: string }) {
  const [overview, setOverview] = useState<ReviewOverview | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    getReviews(projectId, controller.signal)
      .then(setOverview)
      .catch((error: unknown) => {
        if (!isAbortError(error)) setFailed(true);
      });
    return () => controller.abort();
  }, [projectId]);

  const latest = overview?.latest ?? null;

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4">
        <div className="space-y-1.5">
          <CardTitle className="flex items-center gap-2">
            <ScanSearch className="size-4 text-primary" />
            Code review
          </CardTitle>
          <CardDescription>The accepted code checked against the design; findings become tasks.</CardDescription>
        </div>
        <Button asChild size="sm" variant={latest ? "outline" : "default"}>
          <Link to={`/projects/${projectId}/review`}>
            {latest ? "Open" : "Start"}
            <ArrowRight />
          </Link>
        </Button>
      </CardHeader>
      <CardContent>
        {failed ? (
          <p className="text-sm text-destructive">The review status could not be loaded.</p>
        ) : !overview ? (
          <div aria-hidden="true" className="h-10 animate-pulse rounded-lg bg-muted" />
        ) : latest ? (
          <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm">
            <span>
              <span className="font-semibold">{latest.counts.open}</span> open findings
            </span>
            <span className={latest.counts.high > 0 ? "text-destructive" : "text-muted-foreground"}>
              {latest.counts.high} high
            </span>
            <span className="text-muted-foreground">
              Reviewed {formatRelative(latest.createdAt)}
              {latest.outdated && " · code changed since"}
            </span>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            {overview.canReview ? "Code is accepted. Run the first review." : overview.blocker}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
