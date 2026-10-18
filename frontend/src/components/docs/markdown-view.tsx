import { Children, isValidElement, type ReactNode } from "react";
import Markdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";

import { MermaidDiagram } from "@/components/architecture/mermaid-diagram";
import { cn } from "@/lib/utils";

/** The heading anchor GitHub generates, so links like "#invoiceresponse" in the documents work. */
export function headingId(children: ReactNode): string {
  return textOf(children)
    .toLowerCase()
    .replace(/[^a-z0-9 _-]/g, "")
    .replace(/ /g, "-");
}

function textOf(node: ReactNode): string {
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(textOf).join("");
  if (isValidElement<{ children?: ReactNode }>(node)) return textOf(node.props.children);
  return "";
}

/** "docs/developer-guide.md" + "api-reference.md" → "docs/api-reference.md"; "../README.md" → "README.md". */
export function resolvePath(from: string, href: string): string | null {
  const target = href.split("#")[0];
  if (!target) return null;
  const parts = from.split("/").slice(0, -1);
  for (const segment of target.split("/")) {
    if (segment === "" || segment === ".") continue;
    if (segment === "..") {
      if (parts.length === 0) return null;
      parts.pop();
    } else {
      parts.push(segment);
    }
  }
  return parts.join("/");
}

/**
 * Renders a Markdown document (GitHub flavour: tables, task lists). Raw HTML in the document is
 * not rendered. Mermaid code blocks are drawn as diagrams. Relative links to other files of the
 * package call `onOpenPath` instead of navigating.
 */
export function MarkdownView({
  content,
  path,
  onOpenPath,
  className,
}: {
  content: string;
  /** The document's path in the package, to resolve relative links. */
  path: string;
  /** Opens another file of the package; returns false if there is no such file. */
  onOpenPath?: (path: string) => boolean;
  className?: string;
}) {
  const components: Components = {
    h1: ({ children }) => (
      <h1 id={headingId(children)} className="mb-4 mt-2 text-2xl font-semibold tracking-tight">
        {children}
      </h1>
    ),
    h2: ({ children }) => (
      <h2 id={headingId(children)} className="mb-3 mt-8 border-b pb-1 text-xl font-semibold">
        {children}
      </h2>
    ),
    h3: ({ children }) => (
      <h3 id={headingId(children)} className="mb-2 mt-6 text-base font-semibold">
        {children}
      </h3>
    ),
    h4: ({ children }) => (
      <h4 id={headingId(children)} className="mb-2 mt-4 text-sm font-semibold">
        {children}
      </h4>
    ),
    p: ({ children }) => <p className="my-3 leading-relaxed">{children}</p>,
    ul: ({ children }) => <ul className="my-3 list-disc space-y-1 pl-6">{children}</ul>,
    ol: ({ children }) => <ol className="my-3 list-decimal space-y-1 pl-6">{children}</ol>,
    blockquote: ({ children }) => (
      <blockquote className="my-3 border-l-2 pl-4 text-muted-foreground">{children}</blockquote>
    ),
    hr: () => <hr className="my-6" />,
    table: ({ children }) => (
      <div className="my-4 overflow-x-auto">
        <table className="w-full border-collapse text-sm">{children}</table>
      </div>
    ),
    th: ({ children }) => <th className="border px-2 py-1 text-left font-medium">{children}</th>,
    td: ({ children }) => <td className="border px-2 py-1 align-top">{children}</td>,
    pre: ({ children }) => {
      const child = Children.toArray(children)[0];
      if (
        isValidElement<{ className?: string; children?: ReactNode }>(child) &&
        child.props.className?.includes("language-mermaid")
      ) {
        return (
          <div className="my-4 overflow-x-auto rounded-lg border bg-background/40 p-3">
            <MermaidDiagram source={textOf(child.props.children).trim()} label="Diagram" />
          </div>
        );
      }
      return <pre className="my-4 overflow-x-auto rounded-lg border bg-muted/40 p-3 text-xs leading-relaxed">{children}</pre>;
    },
    a: ({ href, children }) => {
      if (!href) return <span>{children}</span>;
      if (/^[a-z][a-z0-9+.-]*:/i.test(href)) {
        return (
          <a href={href} target="_blank" rel="noopener noreferrer" className="text-primary underline underline-offset-2">
            {children}
          </a>
        );
      }
      if (href.startsWith("#")) {
        return (
          <a href={href} className="text-primary underline underline-offset-2">
            {children}
          </a>
        );
      }
      const target = resolvePath(path, href);
      return (
        <a
          href={href}
          className="text-primary underline underline-offset-2"
          onClick={(event) => {
            event.preventDefault();
            if (target && onOpenPath) onOpenPath(target);
          }}
        >
          {children}
        </a>
      );
    },
    // Images would load from anywhere; show the link instead.
    img: ({ src, alt }) => (
      <span className="text-muted-foreground">
        [image: {alt || (typeof src === "string" ? src : "")}]
      </span>
    ),
  };

  return (
    <div
      className={cn(
        "min-w-0 text-sm [&_:not(pre)>code]:rounded [&_:not(pre)>code]:bg-muted [&_:not(pre)>code]:px-1 [&_:not(pre)>code]:py-0.5 [&_code]:font-mono [&_code]:text-[0.85em]",
        className,
      )}
    >
      <Markdown remarkPlugins={[remarkGfm]} components={components}>
        {content}
      </Markdown>
    </div>
  );
}
