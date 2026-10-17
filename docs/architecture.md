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
| `backend`  | Multi-stage Maven → Temurin 25 JRE    | 8080  |
| `frontend` | Multi-stage Node build → nginx        | 3000  |

The backend waits for PostgreSQL to report healthy before starting.
