package com.forgeai.task;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** One entry in a task's activity log. Append-only. */
@Entity
@Table(name = "task_events")
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TaskEventType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private TaskStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20, updatable = false)
    private TaskStatus toStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskEvent() {
        // Required by JPA
    }

    static TaskEvent of(Task task, TaskEventType type) {
        TaskEvent event = new TaskEvent();
        event.taskId = task.getId();
        event.type = type;
        return event;
    }

    static TaskEvent statusChange(Task task, TaskStatus from, TaskStatus to) {
        TaskEvent event = of(task, TaskEventType.STATUS_CHANGED);
        event.fromStatus = from;
        event.toStatus = to;
        return event;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public TaskEventType getType() {
        return type;
    }

    public TaskStatus getFromStatus() {
        return fromStatus;
    }

    public TaskStatus getToStatus() {
        return toStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
