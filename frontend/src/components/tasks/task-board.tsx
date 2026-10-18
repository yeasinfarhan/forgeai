import { useState, type DragEvent } from "react";
import { Lock } from "lucide-react";

import { Estimate, STATUS_LABEL, TASK_STATUSES, TypeBadge } from "@/components/tasks/task-labels";
import type { Task, TaskStatus } from "@/lib/api";
import { cn } from "@/lib/utils";

/**
 * Three columns by status. Cards can be dragged between columns; the details panel offers the
 * same change with a select for keyboard users.
 */
export function TaskBoard({
  tasks,
  selectedId,
  disabled,
  onSelect,
  onMove,
}: {
  tasks: Task[];
  selectedId: string | null;
  disabled: boolean;
  onSelect: (task: Task) => void;
  onMove: (task: Task, status: TaskStatus) => void;
}) {
  const [dragging, setDragging] = useState<string | null>(null);
  const [over, setOver] = useState<TaskStatus | null>(null);

  function drop(event: DragEvent, status: TaskStatus) {
    event.preventDefault();
    setOver(null);
    const task = tasks.find((t) => t.id === event.dataTransfer.getData("text/plain"));
    setDragging(null);
    if (task && task.status !== status) onMove(task, status);
  }

  return (
    <div className="grid gap-3 md:grid-cols-3">
      {TASK_STATUSES.map((status) => {
        const column = tasks.filter((t) => t.status === status);
        const points = column.reduce((sum, t) => sum + (t.estimate ?? 0), 0);
        return (
          <section
            key={status}
            aria-label={STATUS_LABEL[status]}
            onDragOver={(e) => {
              if (disabled || !dragging) return;
              e.preventDefault();
              setOver(status);
            }}
            onDragLeave={() => setOver((s) => (s === status ? null : s))}
            onDrop={(e) => drop(e, status)}
            className={cn(
              "flex min-h-40 flex-col gap-2 rounded-xl border bg-card/60 p-3 transition-colors",
              over === status && "border-primary/60 bg-primary/5",
            )}
          >
            <div className="flex items-center justify-between px-1 text-sm">
              <span className="font-medium">{STATUS_LABEL[status]}</span>
              <span className="font-mono text-xs text-muted-foreground">
                {column.length} · {points} pt
              </span>
            </div>
            {column.map((task) => (
              <button
                key={task.id}
                type="button"
                draggable={!disabled}
                onDragStart={(e) => {
                  e.dataTransfer.setData("text/plain", task.id);
                  e.dataTransfer.effectAllowed = "move";
                  setDragging(task.id);
                }}
                onDragEnd={() => {
                  setDragging(null);
                  setOver(null);
                }}
                onClick={() => onSelect(task)}
                aria-pressed={selectedId === task.id}
                className={cn(
                  "space-y-1.5 rounded-lg border bg-background/60 p-3 text-left transition-colors hover:border-primary/40",
                  selectedId === task.id && "border-primary ring-1 ring-primary/40",
                  dragging === task.id && "opacity-50",
                )}
              >
                <div className="flex items-center gap-2">
                  <span className="font-mono text-[11px] text-muted-foreground">{task.code}</span>
                  <TypeBadge type={task.type} />
                  <span className="ml-auto">
                    <Estimate points={task.estimate} />
                  </span>
                </div>
                <p className="text-sm leading-snug">{task.title}</p>
                {task.blockedBy.length > 0 && status !== "DONE" && (
                  <p className="flex items-center gap-1 text-[11px] text-primary">
                    <Lock className="size-3" />
                    Waiting for {task.blockedBy.join(", ")}
                  </p>
                )}
              </button>
            ))}
            {column.length === 0 && <p className="px-1 text-xs text-muted-foreground">No tasks.</p>}
          </section>
        );
      })}
    </div>
  );
}
