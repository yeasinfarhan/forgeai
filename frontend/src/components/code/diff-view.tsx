import { useMemo } from "react";

import { diffLines, diffStats, hunks } from "@/lib/diff";
import { cn } from "@/lib/utils";

/** Unified diff of two versions of a file, with unchanged stretches collapsed. */
export function DiffView({ before, after }: { before: string | null; after: string | null }) {
  const lines = useMemo(() => diffLines(before, after), [before, after]);
  const groups = useMemo(() => hunks(lines), [lines]);
  const stats = diffStats(lines);
  const trailing = lines.length - groups.reduce((n, g) => n + g.skippedBefore + g.lines.length, 0);

  return (
    <div className="overflow-hidden rounded-lg border">
      <div className="flex gap-3 border-b bg-muted/40 px-3 py-1.5 font-mono text-xs">
        <span className="text-success">+{stats.added}</span>
        <span className="text-destructive">−{stats.removed}</span>
      </div>
      <div className="max-h-[32rem] overflow-auto">
        <table className="w-full border-collapse font-mono text-xs leading-5">
          <tbody>
            {groups.map((group, g) => (
              <Group key={g} skipped={group.skippedBefore} lines={group.lines} />
            ))}
            {trailing > 0 && <Skipped count={trailing} />}
            {lines.length === 0 && (
              <tr>
                <td className="px-3 py-2 text-muted-foreground">Empty file.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Skipped({ count }: { count: number }) {
  return (
    <tr className="bg-muted/30 text-muted-foreground">
      <td colSpan={4} className="px-3 py-0.5">
        ⋯ {count} unchanged {count === 1 ? "line" : "lines"}
      </td>
    </tr>
  );
}

function Group({ skipped, lines }: { skipped: number; lines: ReturnType<typeof diffLines> }) {
  return (
    <>
      {skipped > 0 && <Skipped count={skipped} />}
      {lines.map((line, i) => (
        <tr
          key={i}
          className={cn(line.kind === "add" && "bg-success/10", line.kind === "del" && "bg-destructive/10")}
        >
          <td className="w-10 px-2 text-right text-muted-foreground select-none">{line.oldNo ?? ""}</td>
          <td className="w-10 px-2 text-right text-muted-foreground select-none">{line.newNo ?? ""}</td>
          <td
            className={cn(
              "w-4 text-center select-none",
              line.kind === "add" && "text-success",
              line.kind === "del" && "text-destructive",
            )}
          >
            {line.kind === "add" ? "+" : line.kind === "del" ? "−" : ""}
          </td>
          <td className="pr-3 whitespace-pre">{line.text}</td>
        </tr>
      ))}
    </>
  );
}
