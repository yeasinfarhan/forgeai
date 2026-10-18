package com.forgeai.task;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TaskEventRepository extends JpaRepository<TaskEvent, UUID> {

    List<TaskEvent> findAllByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
