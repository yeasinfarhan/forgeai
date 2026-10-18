package com.forgeai.task;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TaskBacklogRepository extends JpaRepository<TaskBacklog, UUID> {
}
