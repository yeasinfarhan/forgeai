import type { ReactNode } from "react";
import { Activity, RefreshCw, Server, Timer, type LucideIcon } from "lucide-react";

import { useAppLayoutContext } from "@/components/layout/app-layout";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { HealthCheck } from "@/hooks/use-health-check";

const PIPELINE_STAGES = [
  { name: "Idea", detail: "Capture the product idea in natural language" },
  { name: "Requirements", detail: "Functional and non-functional requirements" },
  { name: "Architecture", detail: "System components and their boundaries" },
  { name: "Database design", detail: "Entities, relations and migrations" },
  { name: "API design", detail: "Endpoints, contracts and error model" },
  { name: "Development tasks", detail: "A prioritised, reviewable backlog" },
  { name: "Code", detail: "Implementation generated per task" },
  { name: "Tests", detail: "Unit and integration test suites" },
  { name: "Code review", detail: "Automated review against the design" },
  { name: "Documentation", detail: "Developer and user-facing docs" },
] as const;

export function DashboardPage() {
  const { health } = useAppLayoutContext();

  return (
    <div className="space-y-8">
      <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
          <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
            ForgeAI turns a software idea into requirements, architecture, code, tests and documentation. This
            workspace is in its foundation phase.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={health.refresh} disabled={health.isRefreshing}>
          <RefreshCw className={health.isRefreshing ? "animate-spin" : undefined} />
          Check status
        </Button>
      </section>

      <SystemStatus health={health} />

      <Card>
        <CardHeader>
          <CardTitle>Delivery pipeline</CardTitle>
          <CardDescription>
            The stages ForgeAI will guide a project through. Each stage is delivered in an upcoming phase.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ol className="grid gap-px overflow-hidden rounded-lg border bg-border sm:grid-cols-2 lg:grid-cols-5">
            {PIPELINE_STAGES.map((stage, index) => (
              <li key={stage.name} className="flex flex-col gap-2 bg-card p-4">
                <div className="flex items-center justify-between">
                  <span className="font-mono text-xs text-muted-foreground">{String(index + 1).padStart(2, "0")}</span>
                  <Badge variant="outline">Planned</Badge>
                </div>
                <p className="text-sm font-medium">{stage.name}</p>
                <p className="text-xs leading-relaxed text-muted-foreground">{stage.detail}</p>
              </li>
            ))}
          </ol>
        </CardContent>
      </Card>
    </div>
  );
}

function SystemStatus({ health }: { health: HealthCheck }) {
  const { state, checkedAt, latencyMs } = health;

  const statusBadge =
    state.kind === "up" ? (
      <Badge variant="success">Operational</Badge>
    ) : state.kind === "down" ? (
      <Badge variant="destructive">Unreachable</Badge>
    ) : (
      <Badge variant="outline">Checking…</Badge>
    );

  return (
    <section aria-label="System status" className="grid gap-4 md:grid-cols-3">
      <StatCard icon={Server} label="Backend API" value={state.kind === "up" ? state.data.service : "—"}>
        {statusBadge}
      </StatCard>

      <StatCard icon={Timer} label="Response time" value={latencyMs === null ? "—" : `${latencyMs} ms`}>
        <span className="font-mono text-xs text-muted-foreground">GET /api/health</span>
      </StatCard>

      <StatCard
        icon={Activity}
        label="Last checked"
        value={checkedAt ? checkedAt.toLocaleTimeString() : "—"}
      >
        <span className="text-xs text-muted-foreground">Refreshes every 30 seconds</span>
      </StatCard>

      {state.kind === "down" && (
        <p
          role="alert"
          className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive md:col-span-3"
        >
          The backend could not be reached ({state.message}). Make sure it is running on port 8080 — see the README
          for startup instructions.
        </p>
      )}
    </section>
  );
}

interface StatCardProps {
  icon: LucideIcon;
  label: string;
  value: string;
  children: ReactNode;
}

function StatCard({ icon: Icon, label, value, children }: StatCardProps) {
  return (
    <Card className="gap-3">
      <CardHeader className="flex-row items-center gap-2 text-muted-foreground">
        <Icon className="size-4" />
        <span className="text-xs font-medium tracking-wide uppercase">{label}</span>
      </CardHeader>
      <CardContent className="flex flex-col gap-2">
        <p className="truncate text-xl font-semibold tracking-tight">{value}</p>
        {children}
      </CardContent>
    </Card>
  );
}
