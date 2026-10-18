package com.forgeai.code;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** One accepted file of a project's code workspace. */
@Entity
@Table(name = "code_files")
public class CodeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, length = 300, updatable = false)
    private String path;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "last_task_code", length = 10)
    private String lastTaskCode;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CodeFile() {
        // Required by JPA
    }

    CodeFile(UUID projectId, String path, String content, String taskCode) {
        this.projectId = projectId;
        this.path = path;
        this.content = content;
        this.lastTaskCode = taskCode;
    }

    void write(String content, String taskCode) {
        this.content = content;
        this.lastTaskCode = taskCode;
        this.updatedAt = now();
    }

    @PrePersist
    void onCreate() {
        createdAt = now();
        updatedAt = createdAt;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public String getLastTaskCode() {
        return lastTaskCode;
    }

    public Integer getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
