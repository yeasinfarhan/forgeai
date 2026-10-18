import { useState } from "react";
import { Check, Copy, Download, FileCode, X } from "lucide-react";

import { FormError } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { downloadFile, errorMessage } from "@/lib/api";

/** Preview, copy or download an export (SQL script, OpenAPI document) produced by the server. */
export function ExportSection({ path, title, description }: { path: string; title: string; description: string }) {
  const [preview, setPreview] = useState<{ fileName: string; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  async function load() {
    setError(null);
    setBusy(true);
    try {
      return await downloadFile(path);
    } catch (err) {
      setError(errorMessage(err));
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function download() {
    const file = await load();
    if (!file) return;
    const url = URL.createObjectURL(new Blob([file.text], { type: "text/plain;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = file.fileName;
    link.click();
    URL.revokeObjectURL(url);
  }

  async function copy() {
    if (!preview) return;
    try {
      await navigator.clipboard.writeText(preview.text);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      setError("Copying is not allowed in this browser. Select the text and copy it instead.");
    }
  }

  return (
    <section className="space-y-3 rounded-xl border bg-card p-5">
      <div className="flex flex-wrap items-start gap-3">
        <div className="mr-auto">
          <h2 className="font-semibold">{title}</h2>
          <p className="mt-0.5 text-sm text-muted-foreground">{description}</p>
        </div>
        <Button variant="outline" size="sm" disabled={busy} onClick={async () => setPreview(preview ? null : await load())}>
          <FileCode />
          {preview ? "Hide" : "Preview"}
        </Button>
        <Button size="sm" disabled={busy} onClick={download}>
          <Download />
          Download
        </Button>
      </div>
      <FormError message={error} />
      {preview && (
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-sm">
            <span className="font-mono text-xs text-muted-foreground">{preview.fileName}</span>
            <Button variant="ghost" size="sm" className="ml-auto" onClick={copy}>
              {copied ? <Check /> : <Copy />}
              {copied ? "Copied" : "Copy"}
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setPreview(null)} aria-label="Close preview">
              <X />
            </Button>
          </div>
          <pre className="max-h-96 overflow-auto rounded-md border bg-background/60 p-3 font-mono text-xs leading-relaxed">
            {preview.text}
          </pre>
        </div>
      )}
    </section>
  );
}
