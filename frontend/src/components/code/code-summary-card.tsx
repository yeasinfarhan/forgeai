import { useEffect, useState } from "react";
import { ArrowRight, Code2 } from "lucide-react";
import { Link } from "react-router";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { getCode, isAbortError, type CodeOverview } from "@/lib/api";

/** Code status on the project page, linking to the code page. */
export function CodeSummaryCard({ projectId }: { projectId: string }) {
  const [overview, setOverview] = useState<CodeOverview | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    getCode(projectId, controller.signal)
      .then(setOverview)
      .catch((error: unknown) => {
        if (!isAbortError(error)) setFailed(true);
      });
    return () => controller.abort();
  }, [projectId]);

  const pending = overview?.changesets.filter((c) => c.status === "PENDING").length ?? 0;
  const started = Boolean(overview && (overview.files.length > 0 || overview.changesets.length > 0));

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4">
        <div className="space-y-1.5">
          <CardTitle className="flex items-center gap-2">
            <Code2 className="size-4 text-primary" />
            Code
          </CardTitle>
          <CardDescription>Generated per task, reviewed file by file, downloadable as a ZIP.</CardDescription>
        </div>
        <Button asChild size="sm" variant={started ? "outline" : "default"}>
          <Link to={`/projects/${projectId}/code`}>
            {started ? "Open" : "Start"}
            <ArrowRight />
          </Link>
        </Button>
      </CardHeader>
      <CardContent>
        {failed ? (
          <p className="text-sm text-destructive">The code status could not be loaded.</p>
        ) : !overview ? (
          <div aria-hidden="true" className="h-10 animate-pulse rounded-lg bg-muted" />
        ) : started ? (
          <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm">
            <span>
              <span className="font-semibold">{overview.files.length}</span> accepted files
            </span>
            <span className={pending > 0 ? "text-primary" : "text-muted-foreground"}>
              {pending} {pending === 1 ? "proposal" : "proposals"} to review
            </span>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            {overview.canGenerate ? "Tasks are ready. Generate the code of the first task." : overview.blocker}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
