package com.forgeai.task;

/** Everything about a task a person or the AI writes (not its status, position or code). */
record TaskContent(String title, String description, TaskType type, Integer estimate, TaskDetails details) {
}
