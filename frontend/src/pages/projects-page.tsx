import { FolderKanban, Plus } from "lucide-react";
import { Link } from "react-router";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useProjects } from "@/hooks/use-projects";
import type { Project } from "@/lib/api";
import { formatRelative } from "@/lib/format";

export function ProjectsPage() {
  const { state, reload } = useProjects();

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Projects</h1>
          <p className="mt-1 text-sm text-muted-foreground">Each project is one piece of software ForgeAI helps you build.</p>
        </div>
        <Button asChild size="sm">
          <Link to="/projects/new">
            <Plus />
            New project
          </Link>
        </Button>
      </section>

      {state.kind === "loading" && <ProjectGridSkeleton />}

      {state.kind === "error" && (
        <div role="alert" className="flex flex-col items-start gap-3 rounded-lg border border-destructive/30 bg-destructive/10 p-4">
          <p className="text-sm text-destructive">{state.message}</p>
          <Button variant="outline" size="sm" onClick={reload}>
            Try again
          </Button>
        </div>
      )}

      {state.kind === "loaded" && state.projects.length === 0 && <EmptyState />}

      {state.kind === "loaded" && state.projects.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {state.projects.map((project) => (
            <li key={project.id}>
              <ProjectCard project={project} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ProjectCard({ project }: { project: Project }) {
  return (
    <Link
      to={`/projects/${project.id}`}
      className="group block h-full rounded-xl outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
    >
      <Card className="h-full gap-3 px-5 transition-colors group-hover:border-primary/40">
        <p className="truncate font-medium group-hover:text-primary">{project.name}</p>
        <p className="line-clamp-2 min-h-10 text-sm text-muted-foreground">
          {project.description ?? <span className="italic">No description</span>}
        </p>
        <p className="mt-auto text-xs text-muted-foreground">Updated {formatRelative(project.updatedAt)}</p>
      </Card>
    </Link>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center gap-4 rounded-xl border border-dashed px-6 py-16 text-center">
      <span className="grid size-12 place-items-center rounded-full bg-primary/10 text-primary">
        <FolderKanban className="size-5" />
      </span>
      <div>
        <p className="font-medium">No projects yet</p>
        <p className="mt-1 text-sm text-muted-foreground">Create your first project to get started.</p>
      </div>
      <Button asChild size="sm">
        <Link to="/projects/new">
          <Plus />
          New project
        </Link>
      </Button>
    </div>
  );
}

function ProjectGridSkeleton() {
  return (
    <div aria-hidden="true" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: 3 }, (_, index) => (
        <div key={index} className="h-36 animate-pulse rounded-xl border bg-card" />
      ))}
    </div>
  );
}
