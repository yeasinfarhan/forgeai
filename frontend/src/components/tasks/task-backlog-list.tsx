import { ArrowDown, ArrowUp, Lock } from "lucide-react";

import { Estimate, TaskStatusBadge, TypeBadge } from "@/components/tasks/task-labels";
import { Button } from "@/components/ui/button";
import type { Task } from "@/lib/api";
import { cn } from "@/lib/utils";

/** The backlog in its working order, with buttons to move a task up or down. */
export function TaskBacklogList({
  tasks,
  selectedId,
  disabled,
  onSelect,
  onReorder,
}: {
  tasks: Task[];
  selectedId: string | null;
  disabled: boolean;
  onSelect: (task: Task) => void;
  onReorder: (task: Task, position: number) => void;
}) {
  return (
    <ol className="divide-y rounded-xl border bg-card">
      {tasks.map((task, index) => (
        <li key={task.id} className={cn("flex items-center gap-2 px-3 py-2", selectedId === task.id && "bg-accent/60")}>
          <span className="w-6 text-right font-mono text-xs text-muted-foreground">{index + 1}</span>
          <button type="button" onClick={() => onSelect(task)} className="flex min-w-0 flex-1 flex-wrap items-center gap-2 text-left">
            <span className="font-mono text-xs text-muted-foreground">{task.code}</span>
            <span className="min-w-0 text-sm">{task.title}</span>
            <TypeBadge type={task.type} />
            <Estimate points={task.estimate} />
            {task.dependsOn.length > 0 && (
              <span className="text-[11px] text-muted-foreground">after {task.dependsOn.join(", ")}</span>
            )}
            {task.blockedBy.length > 0 && task.status !== "DONE" && <Lock className="size-3 text-primary" aria-label="Waiting" />}
          </button>
          <TaskStatusBadge status={task.status} />
          <Button
            variant="ghost"
            size="icon"
            aria-label={`Move ${task.code} up`}
            disabled={disabled || index === 0}
            onClick={() => onReorder(task, index - 1)}
          >
            <ArrowUp />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            aria-label={`Move ${task.code} down`}
            disabled={disabled || index === tasks.length - 1}
            onClick={() => onReorder(task, index + 1)}
          >
            <ArrowDown />
          </Button>
        </li>
      ))}
    </ol>
  );
}
