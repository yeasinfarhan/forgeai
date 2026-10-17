import type { HealthState } from "@/hooks/use-health-check";
import { cn } from "@/lib/utils";

const PRESENTATION: Record<HealthState["kind"], { label: string; dot: string }> = {
  checking: { label: "Checking API", dot: "bg-muted-foreground animate-pulse" },
  up: { label: "API online", dot: "bg-success" },
  down: { label: "API unreachable", dot: "bg-destructive" },
};

export function ApiStatusIndicator({ state }: { state: HealthState }) {
  const { label, dot } = PRESENTATION[state.kind];

  return (
    <span
      role="status"
      className="inline-flex items-center gap-2 rounded-full border px-2.5 py-1 text-xs text-muted-foreground"
    >
      <span className={cn("size-1.5 rounded-full", dot)} />
      {label}
    </span>
  );
}
