import { Badge } from "@/components/ui/badge";
import type { RequirementPriority, RequirementStatus } from "@/lib/api";
import { cn } from "@/lib/utils";

const STATUS: Record<RequirementStatus, { label: string; className: string }> = {
  DRAFT: { label: "Draft", className: "text-muted-foreground" },
  IN_REVIEW: { label: "In review", className: "border-primary/40 bg-primary/10 text-primary" },
  APPROVED: { label: "Approved", className: "border-success/30 bg-success/10 text-success" },
};

const PRIORITY: Record<RequirementPriority, { label: string; className: string }> = {
  MUST: { label: "Must", className: "border-destructive/30 text-destructive" },
  SHOULD: { label: "Should", className: "text-foreground" },
  COULD: { label: "Could", className: "text-muted-foreground" },
};

export function StatusBadge({ status }: { status: RequirementStatus }) {
  const { label, className } = STATUS[status];
  return (
    <Badge variant="outline" className={cn(className)}>
      {label}
    </Badge>
  );
}

export function PriorityBadge({ priority }: { priority: RequirementPriority }) {
  const { label, className } = PRIORITY[priority];
  return (
    <Badge variant="outline" className={cn(className)} title={`Priority: ${label} have`}>
      {label}
    </Badge>
  );
}

export function statusLabel(status: RequirementStatus) {
  return STATUS[status].label;
}
