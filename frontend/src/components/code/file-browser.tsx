import { useEffect, useMemo, useState } from "react";
import { File, Folder } from "lucide-react";

import { FormError } from "@/components/form-field";
import { errorMessage, getCodeFile, isAbortError, type CodeFileContent, type CodeFileSummary } from "@/lib/api";
import { formatRelative } from "@/lib/format";
import { cn } from "@/lib/utils";

interface TreeNode {
  name: string;
  path: string;
  file?: CodeFileSummary;
  children: TreeNode[];
}

/** Builds a folder tree from file paths; folders first, then files, alphabetically. */
function buildTree(files: CodeFileSummary[]): TreeNode[] {
  const root: TreeNode = { name: "", path: "", children: [] };
  for (const file of files) {
    const parts = file.path.split("/");
    let node = root;
    parts.forEach((part, index) => {
      const path = parts.slice(0, index + 1).join("/");
      let child = node.children.find((c) => c.name === part && (index === parts.length - 1) === Boolean(c.file));
      if (!child) {
        child = { name: part, path, children: [], file: index === parts.length - 1 ? file : undefined };
        node.children.push(child);
      }
      node = child;
    });
  }
  const sort = (nodes: TreeNode[]) => {
    nodes.sort((a, b) => (Boolean(a.file) === Boolean(b.file) ? a.name.localeCompare(b.name) : a.file ? 1 : -1));
    nodes.forEach((n) => sort(n.children));
  };
  sort(root.children);
  return root.children;
}

/** The accepted code: a folder tree and the selected file's content with line numbers. */
export function FileBrowser({ projectId, files }: { projectId: string; files: CodeFileSummary[] }) {
  const tree = useMemo(() => buildTree(files), [files]);
  const [chosen, setChosen] = useState<string | null>(null);
  // Falls back to the first file when nothing (or a file that no longer exists) is chosen.
  const selected = files.some((f) => f.path === chosen) ? chosen : (files[0]?.path ?? null);
  const [content, setContent] = useState<CodeFileContent | null>(null);
  const [error, setError] = useState<string | null>(null);
  const version = files.find((f) => f.path === selected)?.updatedAt;

  useEffect(() => {
    if (!selected) return;
    const controller = new AbortController();
    setError(null);
    getCodeFile(projectId, selected, controller.signal)
      .then(setContent)
      .catch((err: unknown) => {
        if (!isAbortError(err)) setError(errorMessage(err));
      });
    return () => controller.abort();
  }, [projectId, selected, version]);

  if (files.length === 0) {
    return <p className="text-sm text-muted-foreground">No accepted code yet. Accept files from a change set to see them here.</p>;
  }

  return (
    <div className="grid gap-3 lg:grid-cols-[16rem_1fr]">
      <nav aria-label="Files" className="max-h-[36rem] overflow-auto rounded-lg border bg-background/40 p-2 text-sm">
        <Tree nodes={tree} depth={0} selected={selected} onSelect={setChosen} />
      </nav>
      <div className="min-w-0 space-y-2">
        <FormError message={error} />
        {content && content.path === selected && (
          <>
            <p className="flex flex-wrap gap-x-3 text-xs text-muted-foreground">
              <span className="font-mono text-foreground">{content.path}</span>
              {content.lastTaskCode && <span>last written by {content.lastTaskCode}</span>}
              <span>{formatRelative(content.updatedAt)}</span>
            </p>
            <pre className="max-h-[36rem] overflow-auto rounded-lg border bg-background/60 font-mono text-xs leading-5">
              <code>
                {content.content.replace(/\n$/, "").split("\n").map((line, i) => (
                  <div key={i} className="flex">
                    <span className="w-12 shrink-0 pr-3 text-right text-muted-foreground select-none">{i + 1}</span>
                    <span className="whitespace-pre">{line}</span>
                  </div>
                ))}
              </code>
            </pre>
          </>
        )}
      </div>
    </div>
  );
}

function Tree({
  nodes,
  depth,
  selected,
  onSelect,
}: {
  nodes: TreeNode[];
  depth: number;
  selected: string | null;
  onSelect: (path: string) => void;
}) {
  return (
    <ul>
      {nodes.map((node) =>
        node.file ? (
          <li key={node.path}>
            <button
              type="button"
              onClick={() => onSelect(node.path)}
              aria-current={selected === node.path}
              className={cn(
                "flex w-full items-center gap-1.5 rounded px-1.5 py-0.5 text-left hover:bg-accent/60",
                selected === node.path && "bg-accent font-medium",
              )}
              style={{ paddingLeft: `${depth * 12 + 6}px` }}
            >
              <File className="size-3.5 shrink-0 text-muted-foreground" />
              <span className="truncate">{node.name}</span>
            </button>
          </li>
        ) : (
          <li key={node.path}>
            <p className="flex items-center gap-1.5 px-1.5 py-0.5 text-muted-foreground" style={{ paddingLeft: `${depth * 12 + 6}px` }}>
              <Folder className="size-3.5 shrink-0" />
              {node.name}
            </p>
            <Tree nodes={node.children} depth={depth + 1} selected={selected} onSelect={onSelect} />
          </li>
        ),
      )}
    </ul>
  );
}
