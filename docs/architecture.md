# ForgeAI Architecture

This document describes how ForgeAI is structured today (Phase 3) and the rules that keep it
maintainable as later phases add features.

## System overview

```
┌──────────────────────┐   /api/*    ┌───────────────────────────┐   JDBC   ┌──────────────┐
│  Frontend (React)    │ ──────────► │  Backend (Spring Boot)    │ ───────► │  PostgreSQL  │
│  Vite dev / nginx    │ ◄────────── │  REST API, JSON           │          │              │
└──────────────────────┘             └─────────────┬─────────────┘          └──────────────┘
                                                   │ HTTPS (Phase 3)
                                                   ▼
                                           ┌───────────────┐
                                           │  Claude API   │
                                           └───────────────┘
```

- The **frontend** is a single-page app. It only talks to the backend, never to Claude directly,
  so API keys never reach the browser.
- The **backend** owns all state: projects, context, approvals, tasks and generated artifacts.
- **PostgreSQL** is the system of record.
- **Claude** is called only from the backend's AI module.

## Backend

### Layering

```
Controller   HTTP mapping, request validation, DTOs in/out
    ↓
Service      Business rules and transactions
    ↓
Repository   Spring Data JPA interfaces
    ↓
Database     PostgreSQL
```

Rules:

1. **Controllers never expose JPA entities.** Requests and responses are DTOs (Java records).
   Mapping between DTOs and entities happens in the service layer.
2. **Controllers contain no business logic.** They validate input (`@Valid`) and delegate.
3. **Services own transactions** (`@Transactional`) and are the only callers of repositories.
4. **The AI integration is isolated** in its own package (`com.forgeai.ai`, Phase 3). Feature
   services depend on an interface from that package, not on Claude HTTP details, so prompts,
   retries and model choice can change without touching feature code.

### Packages

Packages are organised **by feature**, with shared infrastructure separated:

```
com.forgeai
├── ForgeAiApplication
├── common/          Cross-cutting code shared by all features
│   ├── exception/   GlobalExceptionHandler, ResourceNotFoundException
│   └── security/    CurrentUser (reads the user id from the access token)
├── config/          SecurityConfig, JwtProperties, DatabaseConnectionVerifier
├── auth/            AuthController, AuthService, JwtTokenService, request/response DTOs
├── user/            User entity, UserRepository, UserResponse
├── project/         Project entity, repository, service, controller, DTOs
├── ai/              AiClient (Claude/Gemini), PromptTemplates, AiProperties, AiException/AiFailure
├── intake/          Idea intake: entities, IntakeStore, IdeaIntakeAssistant, service, controller
├── health/          Health check feature
└── (future)         requirement/, architecture/, ... — one package per feature
```

### Error handling

`GlobalExceptionHandler` converts every error into an
[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem detail:

| Situation                        | Status | Notes                                       |
| -------------------------------- | ------ | ------------------------------------------- |
| Bean Validation failure          | 400    | `violations: [{ field, message }]`          |
| Malformed JSON / bad parameters  | 400    | Handled by Spring's base handler            |
| `ResourceNotFoundException`      | 404    | Message comes from the service              |
| Missing authentication           | 401    | Returned by Spring Security                 |
| Anything unexpected              | 500    | Logged server-side; generic message to client |

Stack traces and internal messages are never sent to clients.

### Security

- **Stateless:** no server sessions, cookies, form login or HTTP Basic, so CSRF does not apply.
- **Public routes:** `GET /api/health`, `POST /api/auth/register`, `POST /api/auth/login`. Everything
  else needs `Authorization: Bearer <token>`.
- **Passwords:** hashed with BCrypt. Passwords over 72 bytes (BCrypt's limit) are rejected, not
  silently truncated. Login compares against a dummy hash for unknown emails so response time does
  not reveal whether an account exists, and the error never says which field was wrong.
- **Tokens:** HS256-signed JWTs issued by `JwtTokenService` with Spring Security's Nimbus encoder.
  Claims: `iss=forgeai`, `sub=<user id>`, `iat`, `exp`, `email`. Validated by Spring Security's
  OAuth2 resource server (signature, expiry, issuer). Lifetime is `JWT_EXPIRATION` (default 8 h).
- **Secret:** `JWT_SECRET` is required and must be at least 32 bytes; the app refuses to start otherwise.
- **Ownership:** every project query is scoped by owner id. Another user's project returns `404`
  rather than `403`, so its existence is not disclosed.
- **Not yet:** refresh tokens, token revocation, rate limiting on login (planned for hardening).

### AI module (`com.forgeai.ai`)

```
Feature service (e.g. IdeaIntakeService)
   └─ feature assistant (IdeaIntakeAssistant): builds prompts, JSON schema, validates output
        └─ AiClient (interface) ── chosen by AI_PROVIDER
             ├─ ClaudeClient → Claude Messages API (POST /v1/messages, output_config.format)
             └─ GeminiClient → Gemini generateContent (responseMimeType + responseSchema)
             (both extend HttpAiClient: not-configured check, retries, timeouts, usage logging)
```

- **One door to the model.** Features depend only on `AiClient`. They pass an `AiRequest`
  (purpose, system prompt, user message, optional JSON schema) and get an `AiResult`.
  `AI_PROVIDER` selects the implementation; Claude is the default and Gemini is an optional,
  free-tier-friendly alternative. `GeminiSchemas` converts the shared JSON Schemas to Gemini's
  `responseSchema` format (upper-case types, `nullable` instead of `anyOf` with null).
- **Structured outputs.** Responses that the app stores are requested with
  `output_config.format = {type: json_schema, schema}`, so Claude must return JSON matching the
  schema. The assistant still validates the parsed result (e.g. "at most five questions"),
  because the schema subset cannot express every rule. Forced tool use is not used: current
  models reject it.
- **Prompts are files.** `src/main/resources/prompts/*.md`, rendered by `PromptTemplates` with
  `{{placeholder}}` values in a single pass, so user text cannot inject new placeholders. User
  content is wrapped in tags and the system prompt tells Claude to treat it as data.
- **Reliability.** 429, 529 and 5xx are retried with exponential backoff (honouring
  `Retry-After`, capped at 20 s, `CLAUDE_MAX_RETRIES` attempts). Connection failures are retried;
  read timeouts (`CLAUDE_TIMEOUT`, default 120 s) are not. `refusal` and `max_tokens` stop
  reasons are reported as failures rather than parsed.
- **Errors.** `AiException` carries an `AiFailure` (HTTP status + user-safe message). Technical
  detail is logged, never returned. No API key → `503 NOT_CONFIGURED`; the rest of the app runs.
- **Usage.** Every call logs purpose, model, input/output tokens, duration and attempts.
- **No DB transaction during AI calls.** `IdeaIntakeService` reads state, calls Claude, then
  writes through `IntakeStore` in a short transaction with an optimistic version check
  (`idea_intakes.version`), so a slow call never holds a connection and two tabs cannot both
  apply an answer.

### Idea intake

Flow: idea → up to 3 rounds of ≤5 clarifying questions → brief. The user can skip ahead
("Write the brief now"), leave answers blank (sent to Claude as "skipped"), regenerate the
brief, or restart with a new idea (replaces questions, answers and brief).

| Table                   | Purpose                                                              |
| ----------------------- | -------------------------------------------------------------------- |
| `idea_intakes`          | One per project (PK = project id): idea, status, current round, brief JSON, model, version |
| `idea_intake_questions` | Questions per round with rationale and the user's answer            |

The brief (`ProjectBrief`: title, summary, problem, target users, goals, core features, out of
scope, constraints, open questions) is the input for the requirements phase.

### Requirements (`com.forgeai.requirement`)

Requirements are individual rows, numbered per type (`FR-001`, `NFR-001`) and never renumbered.
Each has a category, title, description, acceptance criteria (one per line), MoSCoW priority,
status, source (`AI` / `USER`), an `edited` flag and an optimistic-locking `version`.

Workflow: `DRAFT → IN_REVIEW → APPROVED`; in review can go back to draft ("request changes"),
approved can be reopened to draft. Approved requirements cannot be edited or deleted until
reopened. Every create, edit and status change writes a full snapshot to
`requirement_revisions`, so the history shows exactly what was approved.

Regeneration never overwrites reviewed work: only untouched AI drafts are deleted. Everything
else (in review, approved, edited, added by hand) is kept, passed to the AI as existing
requirements so it does not duplicate them, and new items are numbered after the highest code.
The AI call runs outside any transaction; the replace runs in a short one afterwards.

| Table                   | Purpose                                                          |
| ----------------------- | ---------------------------------------------------------------- |
| `requirements`          | Current state of each requirement (unique per project/type/number) |
| `requirement_revisions` | Snapshot per change (`CREATED`, `EDITED`, `STATUS_CHANGED`)      |

Conflicts (stale `version`, editing approved items, generating without a brief) return `409`.

The review workflow (`ReviewStatus`, in `com.forgeai.common.review`) is shared with the
architecture stage.

### Architecture (`com.forgeai.architecture`)

One architecture per project, generated from the **approved** requirements (generation is refused
until every requirement is approved). Its content is a JSON document (`ArchitectureContent`):

- overview, architectural style and why it fits;
- components: name, kind (client, service, data store, external, infrastructure), responsibility,
  technology, dependencies (by component name) and the requirement codes it addresses;
- decisions: architecture decision records (context, decision, alternatives, consequences,
  requirement codes), shown as `ADR-001` …;
- flows: key interactions as Mermaid sequence diagrams.

The component diagram is not stored: the frontend derives a Mermaid flowchart from the component
list, so it always matches the components. Mermaid is loaded lazily and runs with
`securityLevel: "strict"`, because labels come from AI output and user edits.

`ArchitectureRules` keeps the content consistent: unique component names, dependencies that point
at real components, requirement codes that exist. A person's edit that breaks a rule is rejected
with `400` and one message per problem (`problems` in the problem detail); AI output is repaired
instead (duplicates and unknown references dropped, code fences removed, sizes capped).

Same workflow as requirements: draft → in review → approved, approved is locked until reopened,
and approving needs every requirement approved. Regeneration is allowed only in draft and the
replaced content stays in the history. Every generation, edit and status change stores a full
snapshot in `architecture_revisions`, and any revision can be opened read-only.

A SHA-256 fingerprint of the approved requirements (id + version) is stored at generation; when the
requirements change afterwards the page says so. Requirement coverage (which component or decision
addresses each approved requirement) is computed on every read.

| Table                    | Purpose                                                          |
| ------------------------ | ---------------------------------------------------------------- |
| `architectures`          | Current architecture per project: status, content JSON, model, requirements fingerprint, revision |
| `architecture_revisions` | Snapshot per change (`GENERATED`, `EDITED`, `STATUS_CHANGED`)     |

### Database & API design (`com.forgeai.design`)

Two JSON documents per project, stored in one table by kind (`DATABASE`, `API`) and handled by
`DesignDocumentStore`: the same review workflow, optimistic locking and full revision history as
the architecture. Each stage needs the previous one approved: the data model is generated from the
approved architecture and requirements, the API from the approved data model. A fingerprint of the
inputs is stored at generation; the page warns when they change later.

- **Data model** (`design.database`): entities (snake_case tables) with typed columns (UUID, TEXT,
  VARCHAR(n), INTEGER, BIGINT, DECIMAL(p,s), BOOLEAN, DATE, TIMESTAMP, JSON), primary keys, unique
  columns, defaults from a closed set (`SqlDefaults`), allowed values, foreign keys with ON DELETE,
  indexes and requirement codes. The ER diagram (Mermaid) is derived from the foreign keys.
  `SqlExporter` writes a PostgreSQL script: tables, then foreign keys, then indexes; identifiers
  are validated and quoted, constraint names stay within 63 characters. The test suite runs the
  exported script against PostgreSQL.
- **API** (`design.api`): base path, authentication, error format, the error model (statuses with
  codes), schemas (PascalCase, camelCase fields, linked to entities) and endpoints (method, path,
  path/query parameters, request/response schema, success status, error statuses, requirement
  codes). `OpenApiExporter` produces OpenAPI 3.1 with RFC 9457 problem responses and a bearer scheme.

Rules work in two levels. Structural mistakes block saving (`400` with `problems`): unknown
references, duplicates, missing type parameters, path parameters that do not match the path, error
statuses missing from the error model. Design issues are reported as findings by
`ConsistencyService` without blocking: tables without a primary key, foreign keys to non-key
columns or of another type, schema fields without a matching column, entities and schemas nothing
uses, and functional requirements no entity or endpoint serves. AI output is repaired to satisfy
the blocking rules before it is stored.

| Table                       | Purpose                                                      |
| --------------------------- | ------------------------------------------------------------ |
| `design_documents`          | Current database and API design per project (unique per kind) |
| `design_document_revisions` | Snapshot per change                                          |

### Development tasks (`com.forgeai.task`)

Generated once the API design is approved (and with it the data model, architecture and
requirements). Each task has a stable code (`T-001`, never reused), a position in the backlog, a
type, an estimate in story points (1, 2, 3, 5, 8, 13), acceptance criteria, dependencies on other
tasks, and links to the requirements, entities, endpoints (`METHOD /path`) and components it
implements. Lists are stored as one JSON column (`details_json`).

- **Dependencies** are checked on every save: they must exist and must not form a cycle (`400`).
  A task can only be started or finished once everything it depends on is done (`409`); the board
  shows which tasks are waiting. A task others depend on cannot be deleted.
- **Generation** asks the AI with the approved design as input, then repairs the result: unknown
  links dropped, estimates snapped to the scale, dependencies that would form a cycle removed, and
  the new tasks put in an order where every task follows its dependencies before they are numbered.
  Regeneration replaces only AI tasks that are untouched and still to do; started, finished, edited
  and hand-written tasks stay, are passed to the AI, and keep their codes.
- **Findings** (non-blocking): links to design elements that no longer exist, tasks placed before
  their dependencies, and endpoints, entities and functional requirements no task implements.
- Every create, edit and status change is logged in `task_events`. A fingerprint of the approved
  design is stored per generation in `task_backlogs`, so the page can say when the design changed.

| Table           | Purpose                                                       |
| --------------- | ------------------------------------------------------------- |
| `tasks`         | Backlog tasks (unique code per project)                       |
| `task_events`   | Activity log per task (`CREATED`, `EDITED`, `STATUS_CHANGED`) |
| `task_backlogs` | Model and input fingerprint of the latest generation          |

### Code generation (`com.forgeai.code`)

Code is generated one task at a time once the design is approved and the backlog exists. A task
whose dependencies are not done is refused (`409`), so its code always builds on accepted code.

- **Context**: the approved architecture, data model and API, the task (acceptance criteria,
  linked requirements, entities, endpoints, components), the list of every accepted file, and the
  contents of as many files as fit an 80 000-character budget — files written by the tasks this one
  depends on first, then the most recently changed.
- **Change sets**: the AI answers with structured JSON (summary plus files with path, action,
  content, explanation). The proposal is repaired before it is stored: unsafe paths dropped
  (absolute, `..`, `.git/`, `.env`), duplicates dropped, `update` of a missing file turned into
  `create` and vice versa, unchanged files and deletions of missing files dropped, wrapping Markdown
  fences removed. At most 25 files per proposal. Generating again replaces the task's pending proposal.
- **Review**: each file change is accepted or rejected on its own (or all at once). Accepting
  checks that the file still has the content the proposal was based on; if another change set
  changed it in the meantime, accepting is refused and the task should be regenerated. When every
  file is decided the change set is completed. Generating starts the task (to do → in progress).
- **Export**: the accepted files as a ZIP (`<project-slug>/<path>`). Pushing to a Git repository
  is not offered: it would need stored repository credentials.

| Table             | Purpose                                                          |
| ----------------- | ---------------------------------------------------------------- |
| `code_files`      | Accepted code, one row per file (unique path per project)        |
| `code_changesets` | One AI proposal per task generation (pending, completed, discarded) |
| `code_changes`    | File changes in a proposal with base content, new content, decision |

**Tests** are a second kind of change set (`kind = TESTS`), generated for a task once its code is
accepted and reviewed file by file like code. The model sees the files the task's accepted code
wrote first. A test proposal may only create or update test files (files in test folders such as
`src/test/`, `tests/`, `__tests__/`, or named like `*.test.ts`, `*Test.java`, `test_*.py`, plus test
runner configuration) and update an existing build file to add a test dependency; anything else is
dropped. ForgeAI writes the tests but does not run them.

### Code review (`com.forgeai.review`)

Reviews the accepted code against the approved requirements, architecture, data model and API, and
common coding standards (correctness, security, design, testing, performance, maintainability).
Needs the design approved and at least one accepted file.

- **Context**: application code before tests, most recently changed first, with line numbers, up to
  150 000 characters. Files that do not fit are listed as not shown, and the review records how
  many files it covered. The backlog is included so work a task still to do will cover is not reported.
- **Findings** are repaired before they are stored: a file must be one that was shown, a line must
  exist in it, a requirement must be approved, unknown values fall back to defaults, duplicates and
  findings without a title are dropped, at most 40, most severe (high, medium, low) first.
- **Actions**: a finding becomes a backlog task (title, description with the suggested fix and
  location, the finding's task type, linked to its requirement if still approved) or is dismissed
  and can be reopened. A fingerprint of the code at review time tells when the code changed since.

| Table             | Purpose                                                          |
| ----------------- | ---------------------------------------------------------------- |
| `code_reviews`    | One review: summary, model, files reviewed, code fingerprint     |
| `review_findings` | Findings with severity, category, location, status, created task |

### Documentation & package (`com.forgeai.docs`)

Needs the design approved. Two kinds of documents:

- **Written by the AI** (`README.md`, `docs/developer-guide.md`): from the approved requirements,
  architecture, data model and API, and the accepted code (build and setup files first, then
  application code, then tests, up to 80 000 characters). The prompt forbids inventing commands,
  variables or paths the code does not show. They can be edited by hand (optimistic locking) or
  rewritten; a fingerprint of the design and code they were written from tells when they are outdated.
- **Derived from the design** on every download, without the AI, so they always match it:
  `docs/requirements.md`, `docs/architecture.md` (component diagram and flows as Mermaid),
  `docs/data-model.md` (ER diagram as Mermaid), `docs/database.sql`, `docs/api-reference.md`,
  `docs/openapi.json`, `docs/backlog.md`.

The **project package** is a ZIP of the accepted code plus all documents, in a folder named after the
project. A document replaces an accepted file at the same path (typically `README.md`); the page
lists the package contents and marks such files before download.

| Table               | Purpose                                                       |
| ------------------- | ------------------------------------------------------------- |
| `project_documents` | README and developer guide per project: content, model, input fingerprint, edited flag |

### AI usage and limits (`com.forgeai.usage`)

Feature code calls `AiClient`; its only implementation (`MeteredAiClient`, in the AI module) wraps
the configured `AiProvider` (Claude or Gemini). Before each call it asks every `AiUsageGate`
whether the call may go ahead, and afterwards reports the outcome to the gates and to Micrometer
(`forgeai.ai.requests` timer, `forgeai.ai.tokens` counter). The AI module only defines the gate
interface; `AiUsageService` implements it, so the AI module stays independent of users and the
database.

`AiUsageService` takes the caller from the request (the JWT's user, the project id in the path),
refuses calls over the per-user limits with 429 and `Retry-After`, and records every sent call in
its own transaction (kept even if the request fails). `GET /api/usage` summarises the caller's usage.

| Table      | Purpose                                                              |
| ---------- | -------------------------------------------------------------------- |
| `ai_usage` | One row per AI call: user, project, purpose, provider, model, tokens, outcome, duration |

### Operational concerns

- **Sign-in protection**: `LoginThrottle` counts failed sign-ins per email and per client address
  in memory (15-minute window); over the limit, sign-in answers 429.
- **Request logging**: `RequestLoggingFilter` assigns a request id (`X-Request-Id`), puts it in the
  logging context and logs one line per API request; tracing adds trace and span ids.
- **Observability**: Actuator on a separate management port (health with liveness/readiness,
  Prometheus metrics), OpenTelemetry tracing (export opt-in), structured JSON logs in Docker.
  Details in [operations](operations.md).
- **Security review**: see [security](security.md).

### Configuration

All environment-specific values come from environment variables (see `.env.example`). The
backend also imports the project-root `.env` for local development; real environment variables
take precedence. No secrets are stored in the repository.

### Database and migrations

Flyway owns the schema: versioned SQL files in `src/main/resources/db/migration` run on startup.
Hibernate runs with `ddl-auto: validate`, so the app refuses to start if an entity no longer
matches the schema. Never edit an applied migration; add a new `V<n>__description.sql` instead.

| Table      | Purpose                                                       |
| ---------- | ------------------------------------------------------------- |
| `users`    | Accounts. Email is stored lower-cased and is unique.          |
| `projects` | Owned by a user (`owner_id`, cascade on delete); name + description. |

`DatabaseConnectionVerifier` also logs the database product and version at startup.

### Testing

- **Web-layer tests** (`@WebMvcTest`): controllers, security rules and error mapping, no database.
- **Integration tests** (`*IntegrationTest`): the full app over HTTP (MockMvc) against a real
  PostgreSQL started by Testcontainers, with the real Flyway migrations.

## Frontend

```
src/
├── main.tsx              Entry point
├── router.tsx            Route table (React Router data router)
├── components/
│   ├── layout/           AppLayout, Sidebar, Header
│   └── ui/               shadcn/ui primitives (Button, Card, Badge)
├── hooks/                Reusable hooks (useHealthCheck)
├── lib/                  API client, utilities
└── pages/                One component per route
```

- **Routing:** `AppLayout` renders the sidebar and header and an `<Outlet>` for pages. Page titles
  come from route `handle` metadata.
- **API access:** all HTTP calls go through `lib/api.ts` using relative `/api` paths. Vite (dev) and
  nginx (Docker) proxy them to the backend, so no backend URL is hardcoded and no CORS is needed.
- **Styling:** Tailwind CSS 4 with shadcn/ui design tokens defined as CSS variables in `index.css`.
- **Navigation:** only implemented routes appear in the sidebar.
- **Auth:** `AuthProvider` keeps the session (token, expiry, user) in `localStorage`, confirms it
  with `/api/auth/me` on load, signs out on any `401` or when the token expires. `RequireAuth`
  guards the app routes and sends anonymous users to `/login`, returning them afterwards.

## Deployment (Docker Compose)

| Service    | Image / build                         | Port  |
| ---------- | ------------------------------------- | ----- |
| `postgres` | `postgres:17-alpine`, health-checked  | 5432  |
| `backend`  | Multi-stage Maven → Temurin 25 JRE    | 8080 (localhost only), 8081 (internal) |
| `frontend` | Multi-stage Node build → nginx        | 3000  |

The backend waits for PostgreSQL to report healthy before starting. nginx adds security headers
(Content Security Policy and others), compresses responses and caches fingerprinted assets for a
year; `index.html` is never cached, and the stage pages are separate bundles loaded on first use.

CI (`.github/workflows/ci.yml`) builds and tests both parts on every push and pull request and
publishes the images to GitHub Container Registry from `main` and version tags.
