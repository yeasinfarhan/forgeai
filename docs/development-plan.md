# ForgeAI Development Plan

ForgeAI is built in phases. Each phase ends with a working, tested product increment. A phase starts
only when the previous one is complete and reviewed.

| Phase | Name                                   | Status      |
| ----- | -------------------------------------- | ----------- |
| 1     | Foundation                             | Complete    |
| 2     | Authentication & projects              | Complete    |
| 3     | AI module & idea intake                | Complete    |
| 4     | Requirements                           | Complete    |
| 5     | Architecture                           | Complete    |
| 6     | Database & API design                  | Complete    |
| 7     | Development tasks                      | Complete    |
| 8     | Code generation                        | Implemented |
| 9     | Tests & code review                    | Planned     |
| 10    | Documentation & export                 | Planned     |
| 11    | Hardening & release                    | Planned     |

---

## Phase 1 — Foundation ✅

- Monorepo structure: `backend/`, `frontend/`, `docs/`.
- Spring Boot API with layered package structure, environment-based configuration, global
  exception handling (RFC 9457), validation and a security baseline.
- `GET /api/health`.
- PostgreSQL via Docker Compose with startup connectivity verification.
- React + TypeScript + Vite + Tailwind + shadcn/ui application shell with live API status.
- Docker Compose for PostgreSQL, backend and frontend.

## Phase 2 — Authentication & projects ✅

Goal: users can sign in and manage projects, the container for all later work.

Delivered:

- Flyway migrations; first schema: `users`, `projects`.
- Registration and login with BCrypt password hashing; stateless JWT access tokens (`JWT_SECRET`).
- Project CRUD API (`/api/projects`) with DTOs, validation and ownership checks.
- Frontend: auth pages, protected routes, project list and project detail shell.
- Integration tests with Testcontainers PostgreSQL.

## Phase 3 — AI module & idea intake ✅

Goal: a reliable, isolated integration with Claude and the first pipeline stage.

Delivered:

- `com.forgeai.ai` module: Claude API client, configuration (`CLAUDE_API_KEY`), timeouts, retries,
  error mapping, token/usage logging.
- Prompt templates stored in versioned resources, not inline strings.
- Structured outputs (JSON schema via `output_config.format`) validated again in code before they
  are persisted.
- Idea intake: the user describes the idea; Claude asks up to 3 rounds of clarifying questions
  (or the user skips ahead); the structured project brief is saved.
- Persistent project context that every later stage builds on (idea, Q&A rounds, brief).
- Usage is logged per call (tokens, duration, attempts); persisting usage for cost reporting is
  left for Phase 11.

## Phase 4 — Requirements ✅

Delivered:

- Generate functional and non-functional requirements from the brief (and intake Q&A) with a
  JSON-schema structured output, validated in code.
- Structured, individually editable items with category, acceptance criteria and priority; add
  and delete by hand.
- Approval workflow: draft → in review → approved, per item and in bulk; reopen to change.
- Revision history per requirement; regeneration replaces only untouched AI drafts and keeps
  approved, in-review, edited and hand-written requirements.
- Optimistic locking so two tabs cannot silently overwrite each other.

## Phase 5 — Architecture ✅

Delivered:

- Architecture proposal generated from the approved requirements (and the brief): overview and
  style, components with responsibilities and technology, decision records with alternatives and
  trade-offs, and key flows. Every component and decision refers to the requirement codes it
  addresses; a coverage table shows anything left unaddressed.
- Rendered Mermaid diagrams: a component diagram derived from the component list and sequence
  diagrams for the flows, with live preview while editing.
- Section-by-section editing with consistency checks (unique names, real dependencies, real
  requirement codes).
- Same review workflow as requirements (shared `ReviewStatus`), full revision history with
  read-only viewing, regeneration only in draft, and a warning when the approved requirements
  change after generation.

## Phase 6 — Database & API design ✅

Delivered:

- Database design from the approved architecture: entities, columns, keys, foreign keys, indexes,
  defaults and allowed values; Mermaid ER diagram; PostgreSQL export, verified by running it in tests.
- API design from the approved data model: conventions, error model, schemas and endpoints;
  OpenAPI 3.1 export.
- Section editors with blocking structural rules, and non-blocking consistency findings across
  requirements, data model and API.
- Shared review workflow, revision history and change detection for both documents.

## Phase 7 — Development tasks ✅

Delivered:

- Ordered backlog generated from the approved design: tasks with type, story points, acceptance
  criteria and dependencies; cycles refused, dependencies ordered first.
- Board (to do / in progress / done) with drag and drop; a task starts only when its dependencies
  are done. Backlog view with manual ordering. Activity log per task.
- Every task links to the requirements, entities, endpoints and components it implements; coverage
  checks report anything no task implements and links that went stale.
- Add, edit and delete tasks by hand; regeneration keeps started, edited and hand-written tasks.

## Phase 8 — Code generation (implemented)

Delivered:

- Code generated per task from the approved design, the task's details and the accepted code
  (files from the tasks it depends on first); a task waits until its dependencies are done.
- Every proposal is repaired (safe paths only, actions matching the workspace, no unchanged files).
- Diff view with per-file accept / reject, accept all, discard; stale proposals cannot be accepted.
- File browser over the accepted code and ZIP export. Git push is left out on purpose: it needs
  stored repository credentials.

## Phase 9 — Tests & code review

- Generate unit and integration tests for accepted code.
- AI code review against the architecture, requirements and coding standards, with actionable findings.
- Findings can be turned into new tasks.

## Phase 10 — Documentation & export

- Generate README, API reference and developer guides from the project's approved artifacts.
- Export the full project package.

## Phase 11 — Hardening & release

- Rate limiting and cost controls for AI usage.
- Observability: structured logging, metrics, tracing.
- CI pipeline (build, test, lint, image publishing).
- Security review, accessibility pass and performance tuning.
