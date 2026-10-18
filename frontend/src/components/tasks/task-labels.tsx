import { Badge } from "@/components/ui/badge";
import type { TaskStatus, TaskType } from "@/lib/api";
import { cn } from "@/lib/utils";

export const STATUS_LABEL: Record<TaskStatus, string> = {
  TODO: "To do",
  IN_PROGRESS: "In progress",
  DONE: "Done",
};

export const TYPE_LABEL: Record<TaskType, string> = {
  SETUP: "Setup",
  DATABASE: "Database",
  BACKEND: "Backend",
  FRONTEND: "Frontend",
  TESTING: "Testing",
  DEVOPS: "DevOps",
  DOCUMENTATION: "Docs",
};

export const TASK_TYPES = Object.keys(TYPE_LABEL) as TaskType[];
export const TASK_STATUSES: TaskStatus[] = ["TODO", "IN_PROGRESS", "DONE"];
export const ESTIMATES = [1, 2, 3, 5, 8, 13];

const STATUS_CLASS: Record<TaskStatus, string> = {
  TODO: "text-muted-foreground",
  IN_PROGRESS: "border-primary/40 bg-primary/10 text-primary",
  DONE: "border-success/30 bg-success/10 text-success",
};

export function TaskStatusBadge({ status }: { status: TaskStatus }) {
  return (
    <Badge variant="outline" className={cn(STATUS_CLASS[status])}>
      {STATUS_LABEL[status]}
    </Badge>
  );
}

export function TypeBadge({ type }: { type: TaskType }) {
  return <Badge variant="secondary">{TYPE_LABEL[type]}</Badge>;
}

export function Estimate({ points }: { points: number | null }) {
  if (points == null) return null;
  return (
    <span className="rounded border px-1.5 font-mono text-[11px] text-muted-foreground" title={`${points} story points`}>
      {points} pt
    </span>
  );
}
