package com.forgeai.project;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forgeai.common.exception.ResourceNotFoundException;
import com.forgeai.user.UserRepository;

/**
 * Project use cases. Every operation is scoped to the requesting user: a project that belongs to
 * someone else is reported as "not found", so its existence is never revealed.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final UserRepository users;

    ProjectService(ProjectRepository projects, UserRepository users) {
        this.projects = projects;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID ownerId) {
        return projects.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID ownerId, UUID projectId) {
        return ProjectResponse.from(findOwned(ownerId, projectId));
    }

    @Transactional
    public ProjectResponse create(UUID ownerId, ProjectRequest request) {
        Project project = new Project(
                users.getReferenceById(ownerId),
                request.name().strip(),
                normalizeDescription(request.description()));
        return ProjectResponse.from(projects.save(project));
    }

    @Transactional
    public ProjectResponse update(UUID ownerId, UUID projectId, ProjectRequest request) {
        Project project = findOwned(ownerId, projectId);
        project.update(request.name().strip(), normalizeDescription(request.description()));
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(UUID ownerId, UUID projectId) {
        projects.delete(findOwned(ownerId, projectId));
    }

    private Project findOwned(UUID ownerId, UUID projectId) {
        return projects.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.strip();
    }
}
