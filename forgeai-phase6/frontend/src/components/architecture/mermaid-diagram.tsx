import { useEffect, useId, useState } from "react";

type Mermaid = typeof import("mermaid").default;

let mermaidPromise: Promise<Mermaid> | null = null;
let renderCount = 0;

/** Mermaid is large, so it is loaded on first use (only the architecture pages need it). */
function loadMermaid(): Promise<Mermaid> {
  mermaidPromise ??= import("mermaid")
    .then(({ default: mermaid }) => {
      mermaid.initialize({
        startOnLoad: false,
        // Labels come from AI output and user edits: keep Mermaid's sanitising, no click handlers.
        securityLevel: "strict",
        theme: "dark",
        fontFamily: "Inter, ui-sans-serif, system-ui, sans-serif",
      });
      return mermaid;
    })
    .catch((error: unknown) => {
      mermaidPromise = null; // allow a retry, e.g. after a network hiccup
      throw error;
    });
  return mermaidPromise;
}

type RenderState = { kind: "loading" } | { kind: "ready"; svg: string } | { kind: "error"; message: string };

interface MermaidDiagramProps {
  source: string;
  /** Accessible name of the rendered picture. */
  label: string;
  /** Wait this long after the last change before rendering (for live previews while typing). */
  debounceMs?: number;
}

/** Renders Mermaid source as SVG, or explains why it cannot. */
export function MermaidDiagram({ source, label, debounceMs = 0 }: MermaidDiagramProps) {
  const baseId = useId().replace(/[^a-zA-Z0-9]/g, "");
  const [state, setState] = useState<RenderState>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(() => {
      loadMermaid()
        .then(async (mermaid) => {
          await mermaid.parse(source); // throws a readable syntax error without touching the page
          renderCount += 1;
          const { svg } = await mermaid.render(`mermaid-${baseId}-${renderCount}`, source);
          if (!cancelled) setState({ kind: "ready", svg });
        })
        .catch((error: unknown) => {
          if (cancelled) return;
          const message = error instanceof Error ? error.message : String(error);
          setState({ kind: "error", message: message || "The diagram could not be rendered." });
        });
    }, debounceMs);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [source, baseId, debounceMs]);

  if (state.kind === "error") {
    return (
      <div role="alert" className="space-y-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm">
        <p className="font-medium text-destructive">This diagram has a syntax error and cannot be drawn.</p>
        <pre className="max-h-40 overflow-auto font-mono text-xs whitespace-pre-wrap text-muted-foreground">
          {state.message}
        </pre>
      </div>
    );
  }
  if (state.kind === "loading") {
    return <div aria-hidden="true" className="h-40 animate-pulse rounded-lg bg-muted" />;
  }
  return (
    <div
      role="img"
      aria-label={label}
      className="overflow-x-auto rounded-lg border bg-background/40 p-4 [&_svg]:mx-auto [&_svg]:h-auto [&_svg]:max-w-full"
      // Mermaid output with securityLevel "strict" is sanitised by Mermaid itself (DOMPurify).
      dangerouslySetInnerHTML={{ __html: state.svg }}
    />
  );
}
