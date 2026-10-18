import { useEffect, useState } from "react";
import { ArrowLeft, Download, FileText, Pencil, Sparkles, TriangleAlert } from "lucide-react";
import { Link, useParams } from "react-router";

import { MarkdownView } from "@/components/docs/markdown-view";
import { FormError } from "@/components/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import {
  ApiError,
  downloadBinary,
  errorMessage,
  generateDocument,
  getDocs,
  getDocument,
  getPackageFile,
  getProject,
  isAbortError,
  packageExportPath,
  updateDocument,
  type DocsOverview,
  type DocumentKind,
  type DocumentSummary,
  type PackageSource,
  type Project,
  type ProjectDocument,
} from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { NotFoundPage } from "@/pages/not-found-page";

type LoadState =
  | { kind: "loading" }
  | { kind: "loaded"; project: Project; overview: DocsOverview }
  | { kind: "not-found" }
  | { kind: "error"; message: string };

/** The file shown in the viewer: an AI document (editable) or any other file of the package. */
type Viewed =
  | { type: "document"; document: ProjectDocument }
  | { type: "file"; path: string; source: PackageSource; content: string };

const SOURCE: Record<PackageSource, string> = {
  CODE: "Code",
  DOCUMENT: "Written by AI",
  DESIGN: "From the design",
};

const DESCRIPTION: Record<DocumentKind, string> = {
  README: "What the project is, how to configure, run and test it, and where to find the other documents.",
  DEVELOPER_GUIDE: "For developers joining: architecture in the code, conventions, workflow and how to add a feature.",
};

export function DocsPage() {
  const { projectId = "" } = useParams();
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [viewed, setViewed] = useState<Viewed | null>(null);
  const [writing, setWriting] = useState<DocumentKind | null>(null);
  const [confirmRewrite, setConfirmRewrite] = useState<DocumentKind | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([getProject(projectId, controller.signal), getDocs(projectId, controller.signal)])
      .then(([project, overview]) => setState({ kind: "loaded", project, overview }))
      .catch((err: unknown) => {
        if (isAbortError(err) || controller.signal.aborted) return;
        if (err instanceof ApiError && (err.status === 404 || err.status === 400)) {
          setState({ kind: "not-found" });
        } else {
          setState({ kind: "error", message: errorMessage(err) });
        }
      });
    return () => controller.abort();
  }, [projectId]);

  if (state.kind === "not-found") return <NotFoundPage />;
  if (state.kind === "loading") {
    return <div aria-hidden="true" className="mx-auto h-96 max-w-6xl animate-pulse rounded-xl border bg-card" />;
  }
  if (state.kind === "error") {
    return (
      <div className="mx-auto max-w-6xl">
        <FormError message={state.message} />
      </div>
    );
  }

  const { project, overview } = state;
  const counts = {
    code: overview.entries.filter((e) => e.source === "CODE").length,
    docs: overview.entries.filter((e) => e.source !== "CODE").length,
  };

  async function refresh() {
    try {
      setState({ kind: "loaded", project, overview: await getDocs(project.id) });
    } catch {
      // The page keeps what it has; the next action reloads again.
    }
  }

  async function write(kind: DocumentKind) {
    setConfirmRewrite(null);
    setError(null);
    setWriting(kind);
    try {
      setViewed({ type: "document", document: await generateDocument(project.id, kind) });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setWriting(null);
      await refresh();
    }
  }

  async function openDocument(kind: DocumentKind) {
    setError(null);
    try {
      setViewed({ type: "document", document: await getDocument(project.id, kind) });
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  /** Opens a package file; AI documents open as documents so they can be edited. */
  function openPath(path: string): boolean {
    const written = overview.documents.find((d) => d.path === path && d.generated);
    if (written) {
      void openDocument(written.kind);
      return true;
    }
    if (!overview.entries.some((e) => e.path === path)) return false;
    setError(null);
    getPackageFile(project.id, path)
      .then((file) => setViewed({ type: "file", ...file }))
      .catch((err: unknown) => setError(errorMessage(err)));
    return true;
  }

  async function exportPackage() {
    setError(null);
    setExporting(true);
    try {
      const file = await downloadBinary(packageExportPath(project.id));
      const url = URL.createObjectURL(file.blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = file.fileName;
      link.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <Link
        to={`/projects/${project.id}`}
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" />
        {project.name}
      </Link>

      <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Documentation</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            A README and a developer guide written from the design and the code, the design documents, and everything
            packaged with the code in one download.
          </p>
        </div>
        <Button className="shrink-0" disabled={!overview.canGenerate || exporting} onClick={exportPackage}>
          <Download />
          {exporting ? "Preparing…" : "Download package"}
        </Button>
      </section>

      {overview.blocker && (
        <Card className="border-dashed">
          <CardContent className="text-sm text-muted-foreground">
            Documentation is made from the approved design. {overview.blocker}
          </CardContent>
        </Card>
      )}

      <FormError message={error} />
      {writing && (
        <div role="status" className="flex items-center gap-3 rounded-lg border border-primary/30 bg-primary/10 px-4 py-3 text-sm">
          <span className="size-2 animate-pulse rounded-full bg-primary" />
          Writing the {writing === "README" ? "README" : "developer guide"}…
          <span className="ml-auto text-xs text-muted-foreground">This can take a few minutes.</span>
        </div>
      )}

      <section className="grid gap-4 md:grid-cols-2">
        {overview.documents.map((summary) => (
          <DocumentCard
            key={summary.kind}
            summary={summary}
            canGenerate={overview.canGenerate}
            busy={writing !== null}
            confirming={confirmRewrite === summary.kind}
            onOpen={() => openDocument(summary.kind)}
            onWrite={() => (summary.edited ? setConfirmRewrite(summary.kind) : write(summary.kind))}
            onConfirm={() => write(summary.kind)}
            onCancel={() => setConfirmRewrite(null)}
          />
        ))}
      </section>

      {viewed && (
        <Viewer
          key={viewed.type === "document" ? `${viewed.document.kind}-${viewed.document.version}` : viewed.path}
          projectId={project.id}
          viewed={viewed}
          onOpenPath={openPath}
          onSaved={(document) => {
            setViewed({ type: "document", document });
            void refresh();
          }}
          onClose={() => setViewed(null)}
        />
      )}

      {overview.entries.length > 0 && (
        <section className="space-y-3 rounded-xl border bg-card p-5">
          <div>
            <h2 className="font-semibold">Package contents</h2>
            <p className="mt-0.5 text-sm text-muted-foreground">
              {overview.fileName}: {counts.code} code {counts.code === 1 ? "file" : "files"} and {counts.docs} documents.
              Documents derived from the design are built from its approved state on every download.
            </p>
          </div>
          <ul className="divide-y rounded-lg border">
            {overview.entries.map((entry) => (
              <li key={entry.path}>
                <button
                  type="button"
                  onClick={() => openPath(entry.path)}
                  className={cn(
                    "flex w-full flex-wrap items-center gap-2 px-3 py-2 text-left text-sm hover:bg-accent/60",
                    viewedPath(viewed) === entry.path && "bg-accent/60",
                  )}
                >
                  <span className="min-w-0 flex-1 truncate font-mono text-xs">{entry.path}</span>
                  {entry.replacesCode && (
                    <span className="text-xs text-primary">replaces the file of the same name in the code</span>
                  )}
                  <Badge variant={entry.source === "CODE" ? "outline" : "secondary"}>{SOURCE[entry.source]}</Badge>
                  <span className="w-16 text-right text-xs text-muted-foreground">{formatSize(entry.size)}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

function viewedPath(viewed: Viewed | null): string | null {
  if (!viewed) return null;
  return viewed.type === "document" ? viewed.document.path : viewed.path;
}

function formatSize(bytes: number): string {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}

function DocumentCard({
  summary,
  canGenerate,
  busy,
  confirming,
  onOpen,
  onWrite,
  onConfirm,
  onCancel,
}: {
  summary: DocumentSummary;
  canGenerate: boolean;
  busy: boolean;
  confirming: boolean;
  onOpen: () => void;
  onWrite: () => void;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <Card>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <FileText className="size-4 text-primary" />
          <h2 className="font-semibold">{summary.title}</h2>
          <span className="font-mono text-xs text-muted-foreground">{summary.path}</span>
          {summary.edited && <Badge variant="outline">Edited</Badge>}
          {summary.outdated && (
            <Badge variant="outline" className="border-primary/40 bg-primary/10 text-primary">
              Outdated
            </Badge>
          )}
        </div>
        <p className="text-sm text-muted-foreground">{DESCRIPTION[summary.kind]}</p>
        {summary.generated && summary.generatedAt && (
          <p className="text-xs text-muted-foreground">
            Written {formatDateTime(summary.generatedAt)}
            {summary.model && ` by ${summary.model}`}
            {summary.outdated && " · the design or the code changed since"}
          </p>
        )}
        {confirming ? (
          <div className="flex flex-wrap items-center gap-2 rounded-lg border border-primary/30 bg-primary/10 px-3 py-2 text-sm">
            <span className="flex-1">Rewriting replaces your edits.</span>
            <Button size="sm" variant="ghost" onClick={onCancel}>
              Cancel
            </Button>
            <Button size="sm" onClick={onConfirm}>
              Rewrite
            </Button>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {summary.generated && (
              <Button size="sm" variant="outline" onClick={onOpen}>
                Open
              </Button>
            )}
            <Button
              size="sm"
              variant={summary.generated ? "ghost" : "default"}
              disabled={!canGenerate || busy}
              onClick={onWrite}
            >
              <Sparkles />
              {summary.generated ? "Rewrite with AI" : "Write with AI"}
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function Viewer({
  projectId,
  viewed,
  onOpenPath,
  onSaved,
  onClose,
}: {
  projectId: string;
  viewed: Viewed;
  onOpenPath: (path: string) => boolean;
  onSaved: (document: ProjectDocument) => void;
  onClose: () => void;
}) {
  const path = viewedPath(viewed) ?? "";
  const content = viewed.type === "document" ? viewed.document.content : viewed.content;
  const markdown = path.endsWith(".md");
  const [mode, setMode] = useState<"preview" | "source">(markdown ? "preview" : "source");
  const [draft, setDraft] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    if (viewed.type !== "document" || draft === null) return;
    setError(null);
    setSaving(true);
    try {
      onSaved(await updateDocument(projectId, viewed.document.kind, draft, viewed.document.version));
    } catch (err) {
      setError(errorMessage(err));
      setSaving(false);
    }
  }

  return (
    <section className="space-y-4 rounded-xl border bg-card p-5" aria-label={path}>
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="min-w-0 flex-1 truncate font-mono text-sm font-semibold">{path}</h2>
        {draft === null ? (
          <>
            {markdown && (
              <div role="group" aria-label="View" className="flex gap-1">
                {(["preview", "source"] as const).map((value) => (
                  <button
                    key={value}
                    type="button"
                    aria-pressed={mode === value}
                    onClick={() => setMode(value)}
                    className={cn(
                      "rounded-md border px-2.5 py-1 text-xs capitalize",
                      mode === value ? "border-primary/40 bg-primary/10 text-primary" : "text-muted-foreground hover:bg-accent",
                    )}
                  >
                    {value}
                  </button>
                ))}
              </div>
            )}
            {viewed.type === "document" && (
              <Button size="sm" variant="outline" onClick={() => setDraft(viewed.document.content)}>
                <Pencil />
                Edit
              </Button>
            )}
            <Button size="sm" variant="ghost" onClick={onClose}>
              Close
            </Button>
          </>
        ) : (
          <>
            <Button size="sm" variant="ghost" disabled={saving} onClick={() => setDraft(null)}>
              Cancel
            </Button>
            <Button size="sm" disabled={saving || draft.trim() === ""} onClick={save}>
              {saving ? "Saving…" : "Save"}
            </Button>
          </>
        )}
      </div>
      {viewed.type === "document" && viewed.document.outdated && draft === null && (
        <p className="flex items-center gap-2 rounded-lg border border-primary/30 bg-primary/10 px-3 py-2 text-sm text-primary">
          <TriangleAlert className="size-4 shrink-0" />
          The design or the code changed since this was written. Rewrite it, or edit it to match.
        </p>
      )}
      <FormError message={error} />
      {draft !== null ? (
        <Textarea
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          aria-label={`Edit ${path}`}
          className="min-h-[28rem] font-mono text-xs"
        />
      ) : markdown && mode === "preview" ? (
        <MarkdownView content={content} path={path} onOpenPath={onOpenPath} />
      ) : (
        <pre className="max-h-[40rem] overflow-auto rounded-lg border bg-muted/40 p-3 font-mono text-xs leading-relaxed">
          {content}
        </pre>
      )}
    </section>
  );
}
