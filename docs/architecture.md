# ForgeAI Architecture

This document describes how ForgeAI is structured today (Phase 1) and the rules that keep it
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
│   └── exception/   GlobalExceptionHandler, ResourceNotFoundException
├── config/          Spring configuration (security, startup checks)
├── health/          Health check feature
└── (future)         project/, requirement/, ai/, ... — one package per feature,
                     each with its own controller, service, repository, entity and dto types
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

Phase 1 sets a secure baseline:

- Stateless sessions, no form login or HTTP Basic.
- `GET /api/health` is public; every other route requires authentication (none can be obtained yet).
- JWT-based authentication arrives in Phase 2.

### Configuration

All environment-specific values come from environment variables (see `.env.example`). The
backend also imports the project-root `.env` for local development; real environment variables
take precedence. No secrets are stored in the repository.

### Database connectivity

`DatabaseConnectionVerifier` opens a connection at startup and logs the database product and
version. If PostgreSQL is unreachable the application fails fast. Hibernate's `ddl-auto` is `none`;
schema changes will be managed by versioned migrations (Flyway) from Phase 2.

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

## Deployment (Docker Compose)

| Service    | Image / build                         | Port  |
| ---------- | ------------------------------------- | ----- |
| `postgres` | `postgres:17-alpine`, health-checked  | 5432  |
| `backend`  | Multi-stage Maven → Temurin 25 JRE    | 8080  |
| `frontend` | Multi-stage Node build → nginx        | 3000  |

The backend waits for PostgreSQL to report healthy before starting.
