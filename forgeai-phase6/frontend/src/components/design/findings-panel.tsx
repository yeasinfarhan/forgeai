import { CircleAlert, CircleCheck, TriangleAlert } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import type { Finding } from "@/lib/api";
import { cn } from "@/lib/utils";

const AREA_LABEL: Record<Finding["area"], string> = {
  DATABASE: "Data model",
  API: "API",
  REQUIREMENTS: "Requirements",
};

/** Consistency checks across requirements, data model and API; errors first. */
export function FindingsPanel({ findings }: { findings: Finding[] }) {
  const sorted = [...findings].sort((a, b) => (a.severity === b.severity ? 0 : a.severity === "ERROR" ? -1 : 1));
  const errors = findings.filter((f) => f.severity === "ERROR").length;
  return (
    <section className="space-y-3 rounded-xl border bg-card p-5">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="font-semibold">Consistency checks</h2>
        {findings.length > 0 && (
          <span className="text-sm text-muted-foreground">
            {errors} {errors === 1 ? "error" : "errors"}, {findings.length - errors}{" "}
            {findings.length - errors === 1 ? "warning" : "warnings"}
          </span>
        )}
      </div>
      {findings.length === 0 ? (
        <p className="flex items-center gap-2 text-sm text-success">
          <CircleCheck className="size-4" />
          Requirements, data model and API are consistent.
        </p>
      ) : (
        <ul className="space-y-1.5">
          {sorted.map((finding, index) => {
            const Icon = finding.severity === "ERROR" ? CircleAlert : TriangleAlert;
            return (
              <li key={index} className="flex items-start gap-2 text-sm">
                <Icon
                  className={cn(
                    "mt-0.5 size-4 shrink-0",
                    finding.severity === "ERROR" ? "text-destructive" : "text-primary",
                  )}
                  aria-label={finding.severity === "ERROR" ? "Error" : "Warning"}
                />
                <span className="flex-1">{finding.message}</span>
                <Badge variant="outline">{AREA_LABEL[finding.area]}</Badge>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
