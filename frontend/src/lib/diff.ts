/** One line of a line-by-line comparison. */
export interface DiffLine {
  kind: "same" | "add" | "del";
  text: string;
  /** Line number in the old file (same, del). */
  oldNo?: number;
  /** Line number in the new file (same, add). */
  newNo?: number;
}

/** A run of changed lines with a little unchanged context around it. */
export interface DiffHunk {
  lines: DiffLine[];
  /** Unchanged lines skipped before this hunk. */
  skippedBefore: number;
}

/** Above this many line pairs the comparison would be slow; the files are shown as replaced instead. */
const MAX_CELLS = 4_000_000;

export function splitLines(text: string | null): string[] {
  if (!text) return [];
  const lines = text.split("\n");
  if (lines[lines.length - 1] === "") lines.pop();
  return lines;
}

/** Line diff by longest common subsequence: keeps unchanged lines, marks the rest added or removed. */
export function diffLines(oldText: string | null, newText: string | null): DiffLine[] {
  const a = splitLines(oldText);
  const b = splitLines(newText);
  const n = a.length;
  const m = b.length;
  if (n * m > MAX_CELLS) {
    return [
      ...a.map((text, i) => ({ kind: "del" as const, text, oldNo: i + 1 })),
      ...b.map((text, j) => ({ kind: "add" as const, text, newNo: j + 1 })),
    ];
  }
  // lcs[i][j] = length of the LCS of a[i..] and b[j..], in one flat array.
  const lcs = new Uint32Array((n + 1) * (m + 1));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      lcs[i * (m + 1) + j] =
        a[i] === b[j] ? lcs[(i + 1) * (m + 1) + j + 1] + 1 : Math.max(lcs[(i + 1) * (m + 1) + j], lcs[i * (m + 1) + j + 1]);
    }
  }
  const out: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      out.push({ kind: "same", text: a[i], oldNo: i + 1, newNo: j + 1 });
      i++;
      j++;
    } else if (lcs[(i + 1) * (m + 1) + j] >= lcs[i * (m + 1) + j + 1]) {
      out.push({ kind: "del", text: a[i], oldNo: i + 1 });
      i++;
    } else {
      out.push({ kind: "add", text: b[j], newNo: j + 1 });
      j++;
    }
  }
  while (i < n) out.push({ kind: "del", text: a[i], oldNo: ++i });
  while (j < m) out.push({ kind: "add", text: b[j], newNo: ++j });
  return out;
}

/** Groups a diff into hunks, keeping {@code context} unchanged lines around each change. */
export function hunks(lines: DiffLine[], context = 3): DiffHunk[] {
  const changed = lines.map((l) => l.kind !== "same");
  const keep = lines.map((_, index) => {
    for (let k = Math.max(0, index - context); k <= Math.min(lines.length - 1, index + context); k++) {
      if (changed[k]) return true;
    }
    return false;
  });
  const result: DiffHunk[] = [];
  let skipped = 0;
  let current: DiffHunk | null = null;
  lines.forEach((line, index) => {
    if (!keep[index]) {
      skipped++;
      current = null;
      return;
    }
    if (!current) {
      current = { lines: [], skippedBefore: skipped };
      result.push(current);
      skipped = 0;
    }
    current.lines.push(line);
  });
  return result;
}

export function diffStats(lines: DiffLine[]) {
  return {
    added: lines.filter((l) => l.kind === "add").length,
    removed: lines.filter((l) => l.kind === "del").length,
  };
}
