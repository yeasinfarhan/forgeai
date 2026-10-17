import { ArrowLeft } from "lucide-react";
import { Link, useNavigate } from "react-router";

import { ProjectForm } from "@/components/project-form";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { createProject, type ProjectInput } from "@/lib/api";

export function NewProjectPage() {
  const navigate = useNavigate();

  async function handleCreate(input: ProjectInput) {
    const project = await createProject(input);
    navigate(`/projects/${project.id}`, { replace: true });
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <Link to="/projects" className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" />
        Projects
      </Link>
      <Card>
        <CardHeader>
          <CardTitle className="text-xl">New project</CardTitle>
          <CardDescription>Describe the software you want to build. You can change this later.</CardDescription>
        </CardHeader>
        <CardContent>
          <ProjectForm
            submitLabel="Create project"
            submittingLabel="Creating…"
            onSubmit={handleCreate}
            onCancel={() => navigate("/projects")}
          />
        </CardContent>
      </Card>
    </div>
  );
}
