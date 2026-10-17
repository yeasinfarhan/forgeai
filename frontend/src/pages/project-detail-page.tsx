import { useEffect, useState } from "react";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router";

import { FormError } from "@/components/form-field";
import { ProjectForm } from "@/components/project-form";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  ApiError,
  deleteProject,
  errorMessage,
  getProject,
  isAbortError,
  updateProject,
  type Project,
  type ProjectInput,
} from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { NotFoundPage } from "@/pages/not-found-page";

type PageState =
  | { kind: "loading" }
  | { kind: "loaded"; project: Project }
  | { kind: "not-found" }
  | { kind: "error"; message: string };

export function ProjectDetailPage() {
  const { projectId = "" } = useParams();
  const [state, setState] = useState<PageState>({ kind: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    setState({ kind: "loading" });
    getProject(projectId, controller.signal)
      .then((project) => setState({ kind: "loaded", project }))
      .catch((error: unknown) => {
        if (isAbortError(error) || controller.signal.aborted) return;
        if (error instanceof ApiError && (error.status === 404 || error.status === 400)) {
          setState({ kind: "not-found" });
        } else {
          setState({ kind: "error", message: errorMessage(error) });
        }
      });
    return () => controller.abort();
  }, [projectId]);

  if (state.kind === "not-found") return <NotFoundPage />;

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Link to="/projects" className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" />
        Projects
      </Link>

      {state.kind === "loading" && <div aria-hidden="true" className="h-64 animate-pulse rounded-xl border bg-card" />}
      {state.kind === "error" && <FormError message={state.message} />}
      {state.kind === "loaded" && (
        <ProjectDetail project={state.project} onUpdated={(project) => setState({ kind: "loaded", project })} />
      )}
    </div>
  );
}

function ProjectDetail({ project, onUpdated }: { project: Project; onUpdated: (project: Project) => void }) {
  const navigate = useNavigate();
  const [isEditing, setIsEditing] = useState(false);
  const [isConfirmingDelete, setIsConfirmingDelete] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function handleUpdate(input: ProjectInput) {
    onUpdated(await updateProject(project.id, input));
    setIsEditing(false);
  }

  async function handleDelete() {
    setDeleteError(null);
    setIsDeleting(true);
    try {
      await deleteProject(project.id);
      navigate("/projects", { replace: true });
    } catch (error) {
      setDeleteError(errorMessage(error));
      setIsDeleting(false);
    }
  }

  if (isEditing) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-xl">Edit project</CardTitle>
        </CardHeader>
        <CardContent>
          <ProjectForm
            initialValues={{ name: project.name, description: project.description ?? "" }}
            submitLabel="Save changes"
            submittingLabel="Saving…"
            onSubmit={handleUpdate}
            onCancel={() => setIsEditing(false)}
          />
        </CardContent>
      </Card>
    );
  }

  return (
    <>
      <section className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold tracking-tight break-words">{project.name}</h1>
          <p className="mt-1 text-xs text-muted-foreground">
            Created {formatDateTime(project.createdAt)} · Updated {formatDateTime(project.updatedAt)}
          </p>
        </div>
        <div className="flex shrink-0 gap-2">
          <Button variant="outline" size="sm" onClick={() => setIsEditing(true)}>
            <Pencil />
            Edit
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="text-destructive hover:text-destructive"
            onClick={() => setIsConfirmingDelete(true)}
            disabled={isConfirmingDelete}
          >
            <Trash2 />
            Delete
          </Button>
        </div>
      </section>

      {isConfirmingDelete && (
        <div role="alertdialog" aria-labelledby="delete-title" className="space-y-3 rounded-lg border border-destructive/30 bg-destructive/10 p-4">
          <p id="delete-title" className="text-sm font-medium">
            Delete “{project.name}”? This cannot be undone.
          </p>
          <FormError message={deleteError} />
          <div className="flex gap-2">
            <Button
              size="sm"
              className="bg-destructive text-white hover:bg-destructive/90"
              onClick={handleDelete}
              disabled={isDeleting}
            >
              {isDeleting ? "Deleting…" : "Delete project"}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setIsConfirmingDelete(false)} disabled={isDeleting}>
              Cancel
            </Button>
          </div>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Description</CardTitle>
        </CardHeader>
        <CardContent>
          {project.description ? (
            <p className="text-sm leading-relaxed whitespace-pre-wrap">{project.description}</p>
          ) : (
            <p className="text-sm text-muted-foreground italic">No description yet.</p>
          )}
        </CardContent>
      </Card>
    </>
  );
}
