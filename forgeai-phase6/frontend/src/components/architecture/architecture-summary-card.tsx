import { useEffect, useState } from "react";
import { ArrowRight, Network } from "lucide-react";
import { Link } from "react-router";

import { StatusBadge } from "@/components/requirements/requirement-labels";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { getArchitecture, isAbortError, type ArchitectureOverview } from "@/lib/api";

/** Architecture status on the project page, linking to the Architecture page. */
export function ArchitectureSummaryCard({ projectId }: { projectId: string }) {
  const [overview, setOverview] = useState<ArchitectureOverview | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getArchitecture(projectId, controller.signal)
      .then(setOverview)
      .catch((error: unknown) => {
        if (!isAbortError(error)) setOverview(null);
      });
    return () => controller.abort();
  }, [projectId]);

  const architecture = overview?.architecture;

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4">
        <div className="space-y-1.5">
          <CardTitle className="flex items-center gap-2">
            <Network className="size-4 text-primary" />
            Architecture
          </CardTitle>
          <CardDescription>Components, technology decisions and key flows.</CardDescription>
        </div>
        <Button asChild size="sm" variant={architecture ? "outline" : "default"}>
          <Link to={`/projects/${projectId}/architecture`}>
            {architecture ? "Open" : "Start"}
            <ArrowRight />
          </Link>
        </Button>
      </CardHeader>
      <CardContent>
        {!overview ? (
          <div aria-hidden="true" className="h-10 animate-pulse rounded-lg bg-muted" />
        ) : architecture ? (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
            <StatusBadge status={architecture.status} />
            <span className="text-muted-foreground">{architecture.content.style}</span>
            <span className="text-muted-foreground">
              {architecture.content.components.length} components · {architecture.content.decisions.length} decisions
            </span>
            {architecture.requirementsChanged && <span className="text-primary">Requirements changed since generation</span>}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            {overview.requirementsTotal > 0 && overview.requirementsApproved === overview.requirementsTotal
              ? "All requirements are approved. Generate the architecture."
              : "Available once every requirement is approved."}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
