# ForgeAI Development Plan

ForgeAI is built in phases. Each phase ends with a working, tested product increment. A phase starts
only when the previous one is complete and reviewed.

| Phase | Name                                   | Status      |
| ----- | -------------------------------------- | ----------- |
| 1     | Foundation                             | Complete    |
| 2     | Authentication & projects              | Complete    |
| 3     | AI module & idea intake                | Complete    |
| 4     | Requirements                           | Complete    |
| 5     | Architecture                           | Implemented |
| 6     | Database & API design                  | Planned     |
| 7     | Development tasks                      | Planned     |
| 8     | Code generation                        | Planned     |
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

## Phase 5 — Architecture (implemented)

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

## Phase 6 — Database & API design

- Database design: entities, relationships, constraints; preview as an ER diagram; export as SQL.
- API design: endpoints, request/response contracts and error model; export as OpenAPI.
- Consistency checks between requirements, data model and API.

## Phase 7 — Development tasks

- Break the approved design into an ordered backlog of tasks with acceptance criteria and dependencies.
- Task board with status tracking.
- Each task links back to the requirements and design elements it implements.

## Phase 8 — Code generation

- Generate code per task, using the approved design and existing generated code as context.
- Diff view and per-file approval before code is accepted.
- Export to a Git repository or downloadable archive.

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
