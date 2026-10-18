import { useEffect, useState } from "react";
import { ArrowRight, Database, Waypoints, type LucideIcon } from "lucide-react";
import { Link } from "react-router";

import { StatusBadge } from "@/components/requirements/requirement-labels";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  getDesign,
  isAbortError,
  type ApiDesignOverview,
  type DatabaseDesignOverview,
  type DesignStage,
  type StageOverview,
} from "@/lib/api";

/** Database and API design status on the project page, linking to both pages. */
export function DesignSummaryCard({ projectId }: { projectId: string }) {
  const [database, setDatabase] = useState<DatabaseDesignOverview | null>(null);
  const [api, setApi] = useState<ApiDesignOverview | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    const onError = (error: unknown) => {
      if (!isAbortError(error)) setFailed(true);
    };
    getDesign<DatabaseDesignOverview>(projectId, "database-design", controller.signal).then(setDatabase).catch(onError);
    getDesign<ApiDesignOverview>(projectId, "api-design", controller.signal).then(setApi).catch(onError);
    return () => controller.abort();
  }, [projectId]);

  const errors = (database?.findings ?? []).filter((f) => f.severity === "ERROR").length;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Database &amp; API design</CardTitle>
        <CardDescription>
          The data model and the HTTP API, with consistency checks against the requirements.
          {errors > 0 && <span className="text-destructive"> {errors} consistency {errors === 1 ? "error" : "errors"}.</span>}
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-2">
        {failed && <p className="text-sm text-destructive sm:col-span-2">The design status could not be loaded.</p>}
        <StageRow
          projectId={projectId}
          stage="database-design"
          icon={Database}
          title="Database design"
          overview={database}
          detail={(o) => `${o.design?.content.entities.length ?? 0} entities`}
        />
        <StageRow
          projectId={projectId}
          stage="api-design"
          icon={Waypoints}
          title="API design"
          overview={api}
          detail={(o) => `${o.design?.content.endpoints.length ?? 0} endpoints`}
        />
      </CardContent>
    </Card>
  );
}

function StageRow<O extends StageOverview<unknown>>({
  projectId,
  stage,
  icon: Icon,
  title,
  overview,
  detail,
}: {
  projectId: string;
  stage: DesignStage;
  icon: LucideIcon;
  title: string;
  overview: O | null;
  detail: (overview: O) => string;
}) {
  return (
    <div className="flex items-center gap-3 rounded-lg border bg-background/40 p-3">
      <Icon className="size-4 shrink-0 text-primary" />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium">{title}</p>
        {!overview ? (
          <div aria-hidden="true" className="mt-1 h-4 w-24 animate-pulse rounded bg-muted" />
        ) : overview.design ? (
          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <StatusBadge status={overview.design.status} />
            {detail(overview)}
          </div>
        ) : (
          <p className="text-xs text-muted-foreground">{overview.canGenerate ? "Ready to generate." : overview.blocker}</p>
        )}
      </div>
      <Button asChild size="sm" variant={overview?.design ? "outline" : "default"}>
        <Link to={`/projects/${projectId}/${stage}`}>
          {overview?.design ? "Open" : "Start"}
          <ArrowRight />
        </Link>
      </Button>
    </div>
  );
}
