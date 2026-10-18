import { useEffect, useState } from "react";
import { ArrowRight, BookOpen } from "lucide-react";
import { Link } from "react-router";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { getDocs, isAbortError, type DocsOverview } from "@/lib/api";

/** Documentation status on the project page, linking to the documentation page. */
export function DocsSummaryCard({ projectId }: { projectId: string }) {
  const [overview, setOverview] = useState<DocsOverview | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    getDocs(projectId, controller.signal)
      .then(setOverview)
      .catch((error: unknown) => {
        if (!isAbortError(error)) setFailed(true);
      });
    return () => controller.abort();
  }, [projectId]);

  const written = overview?.documents.filter((d) => d.generated) ?? [];
  const outdated = written.filter((d) => d.outdated).length;

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4">
        <div className="space-y-1.5">
          <CardTitle className="flex items-center gap-2">
            <BookOpen className="size-4 text-primary" />
            Documentation
          </CardTitle>
          <CardDescription>README, developer guide and design documents, packaged with the code.</CardDescription>
        </div>
        <Button asChild size="sm" variant={written.length > 0 ? "outline" : "default"}>
          <Link to={`/projects/${projectId}/docs`}>
            {written.length > 0 ? "Open" : "Start"}
            <ArrowRight />
          </Link>
        </Button>
      </CardHeader>
      <CardContent>
        {failed ? (
          <p className="text-sm text-destructive">The documentation status could not be loaded.</p>
        ) : !overview ? (
          <div aria-hidden="true" className="h-10 animate-pulse rounded-lg bg-muted" />
        ) : !overview.canGenerate ? (
          <p className="text-sm text-muted-foreground">{overview.blocker}</p>
        ) : (
          <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm">
            <span>
              <span className="font-semibold">{written.length}</span> of {overview.documents.length} documents written
            </span>
            {outdated > 0 && <span className="text-primary">{outdated} outdated</span>}
            <span className="text-muted-foreground">{overview.entries.length} files in the package</span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
