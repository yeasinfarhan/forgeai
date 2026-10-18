# ForgeAI

ForgeAI is an AI-powered software engineering workspace. It takes a natural-language software idea and
progressively turns it into a complete, reviewable delivery:

```
Idea → Requirements → Architecture → Database Design → API Design
     → Development Tasks → Code → Tests → Code Review → Documentation
```

Claude acts as the reasoning engine. The application itself owns everything around it: projects,
persistent context, approvals, tasks, generated artifacts and the development workflow.

> **Status: Phase 5 — Architecture.** Users register, manage projects, turn an idea into a
> structured project brief, generate and approve requirements, and from those an architecture
> (components, decision records, Mermaid diagrams) with the same review workflow. Later stages are
> planned in
> [`docs/development-plan.md`](docs/development-plan.md).

---

## Technology stack

| Layer          | Technology                                                                 |
| -------------- | -------------------------------------------------------------------------- |
| Frontend       | React 19, TypeScript, Vite, Tailwind CSS 4, shadcn/ui, React Router        |
| Backend        | Java 25, Spring Boot 4.1, Spring Web MVC, Spring Data JPA, Spring Security, Bean Validation |
| Database       | PostgreSQL 17                                                              |
| AI             | Claude API (introduced in Phase 3)                                         |
| Infrastructure | Docker, Docker Compose                                                     |

## Folder structure

```
forgeai/
├── backend/                     Spring Boot API
│   ├── src/main/java/com/forgeai/
│   │   ├── ForgeAiApplication.java
│   │   ├── common/exception/    Global exception handling (RFC 9457 problem details)
│   │   ├── ai/                  AI clients (Claude, Gemini), prompt templates, AI errors
│   │   ├── architecture/        Architecture proposal: generation, editing, review workflow, history
│   │   ├── auth/                Registration, login, JWT issuing
│   │   ├── common/review/       Review workflow shared by requirements and architecture
│   │   ├── common/security/     Current-user helper
│   │   ├── config/              Security, JWT settings, startup database check
│   │   ├── health/              GET /api/health
│   │   ├── intake/              Idea intake: questions, answers, project brief
│   │   ├── project/             Project CRUD
│   │   ├── requirement/         Requirements: generation, editing, review workflow, history
│   │   └── user/                User entity and repository
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── db/migration/        Flyway SQL migrations
│   │   └── prompts/             Prompt templates (Markdown)
│   ├── src/test/java/...        Web-layer and Testcontainers integration tests
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                    React + Vite application
│   ├── src/
│   │   ├── auth/                Session state, route guard
│   │   ├── components/architecture/ Architecture sections, editors, Mermaid rendering, history
│   │   ├── components/intake/   Idea intake panel, brief view
│   │   ├── components/layout/   App shell: sidebar, header, layouts
│   │   ├── components/requirements/ Requirement cards, form, summary card
│   │   ├── components/ui/       shadcn/ui components
│   │   ├── hooks/               React hooks (API health polling)
│   │   ├── lib/                 API client and utilities
│   │   ├── pages/               Route pages
│   │   └── router.tsx           Route definitions
│   ├── Dockerfile
│   └── nginx.conf               Serves the SPA and proxies /api to the backend
├── docs/
│   ├── architecture.md
│   └── development-plan.md
├── docker-compose.yml
├── .env.example
└── README.md
```

## Prerequisites

| Tool           | Version | Needed for                          |
| -------------- | ------- | ----------------------------------- |
| JDK            | 25      | Running the backend locally         |
| Maven          | 3.9+    | Building the backend locally        |
| Node.js        | 22+     | Running the frontend locally        |
| Docker Desktop | recent  | PostgreSQL and the full stack       |

Docker alone is enough to run the whole stack (`docker compose up --build`).

## Environment setup

All configuration lives in a single `.env` file at the project root.

```bash
cp .env.example .env          # Windows PowerShell: Copy-Item .env.example .env
```

Then edit `.env` and set `DATABASE_PASSWORD` and `JWT_SECRET`. Generate a secret with:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))   # PowerShell
```
```bash
openssl rand -base64 48                                                                  # bash
```

| Variable            | Purpose                                                  | Used from |
| ------------------- | -------------------------------------------------------- | --------- |
| `DATABASE_URL`      | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/forgeai` | Phase 1   |
| `DATABASE_USERNAME` | Database user                                            | Phase 1   |
| `DATABASE_PASSWORD` | Database password (required)                             | Phase 1   |
| `CLAUDE_API_KEY`    | Claude API key (optional; AI features need it)           | Phase 3   |
| `CLAUDE_MODEL`      | Model for AI features (default `claude-sonnet-5-5`)      | Phase 3   |
| `AI_PROVIDER`       | `claude` (default) or `gemini`                           | Phase 3   |
| `GEMINI_API_KEY`    | Google Gemini API key (when `AI_PROVIDER=gemini`)        | Phase 3   |
| `GEMINI_MODEL`      | Gemini model (default `gemini-3.8-flash`)                | Phase 3   |
| `JWT_SECRET`        | Secret for signing access tokens — **required**, 32+ chars | Phase 2   |
| `JWT_EXPIRATION`    | Access-token lifetime (ISO-8601, default `PT8H`)         | Phase 2   |

The backend reads the root `.env` automatically when started from `backend/` or the project root.
Real environment variables always override values in `.env`. **Never commit `.env`** — it is git-ignored.

## PostgreSQL setup

Start only the database container:

```bash
docker compose up -d postgres
docker compose ps             # wait until postgres shows "healthy"
```

The database, user and password are created from `.env` on first start. Data is kept in the
`postgres-data` Docker volume. To reset it: `docker compose down -v`.

Using your own PostgreSQL instead? Create a database and user, then point `DATABASE_URL`,
`DATABASE_USERNAME` and `DATABASE_PASSWORD` at it.

## Backend startup

```bash
cd backend
mvn spring-boot:run
```

On startup the backend opens a database connection and logs, for example:

```
Connected to PostgreSQL 17.x at jdbc:postgresql://localhost:5432/forgeai
```

If the database is unreachable the application stops with a clear error instead of starting half-working.

Run the tests:

```bash
mvn test
```

No Java or Maven installed? Run the tests in a container instead (from the project root). The Docker
socket is mounted so the integration tests can start their own PostgreSQL with Testcontainers:

```bash
# PowerShell / bash
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v forgeai-m2:/root/.m2 -v "${PWD}/backend":/workspace -w /workspace maven:3.9-eclipse-temurin-25 mvn -B test
```
```cmd
:: cmd
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v forgeai-m2:/root/.m2 -v "%cd%\backend":/workspace -w /workspace maven:3.9-eclipse-temurin-25 mvn -B test
```

Integration tests (`*IntegrationTest`) need Docker running; they use a throwaway `postgres:17-alpine` container.

Build a runnable jar:

```bash
mvn package
java -jar target/forgeai-backend-0.1.0-SNAPSHOT.jar
```

## Frontend startup

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The Vite dev server proxies `/api` requests to `http://localhost:8080`
(override with `VITE_API_PROXY_TARGET`). The header and dashboard show live API status.

Production build: `npm run build` (output in `frontend/dist`).

## Docker startup

Run the complete stack:

```bash
docker compose up --build
```

| Service    | URL                     |
| ---------- | ----------------------- |
| Frontend   | http://localhost:3000   |
| Backend    | http://localhost:8080   |
| PostgreSQL | localhost:5432          |

The frontend container (nginx) serves the built app and forwards `/api` to the backend container.
Stop everything with `docker compose down`.

## API health check

```bash
curl http://localhost:8080/api/health
```

```json
{
  "status": "UP",
  "service": "ForgeAI"
}
```

## API overview

| Method | Path                    | Auth | Description                                   |
| ------ | ----------------------- | ---- | --------------------------------------------- |
| GET    | `/api/health`           | —    | Service health                                |
| POST   | `/api/auth/register`    | —    | Create an account → `201` + access token      |
| POST   | `/api/auth/login`       | —    | Sign in → access token                        |
| GET    | `/api/auth/me`          | ✓    | The signed-in user                            |
| GET    | `/api/projects`         | ✓    | Your projects, most recently updated first    |
| POST   | `/api/projects`         | ✓    | Create a project → `201` + `Location` header  |
| GET    | `/api/projects/{id}`    | ✓    | One project                                   |
| PUT    | `/api/projects/{id}`    | ✓    | Update name / description                     |
| DELETE | `/api/projects/{id}`    | ✓    | Delete → `204`                                |
| GET    | `/api/projects/{id}/intake`         | ✓ | Idea intake state (questions, answers, brief) |
| POST   | `/api/projects/{id}/intake`         | ✓ | Start / restart with `{"idea": "…"}`           |
| POST   | `/api/projects/{id}/intake/answers` | ✓ | Answer the current round of questions          |
| POST   | `/api/projects/{id}/intake/brief`   | ✓ | Write (or rewrite) the brief now               |
| GET    | `/api/projects/{id}/requirements`              | ✓ | All requirements with status counts         |
| POST   | `/api/projects/{id}/requirements/generate`     | ✓ | Generate / regenerate drafts from the brief |
| POST   | `/api/projects/{id}/requirements`              | ✓ | Add a requirement by hand → `201`           |
| PUT    | `/api/projects/{id}/requirements/{rid}`        | ✓ | Edit (needs `version`; not when approved)   |
| POST   | `/api/projects/{id}/requirements/{rid}/status` | ✓ | Move through draft → in review → approved   |
| POST   | `/api/projects/{id}/requirements/bulk-status`  | ✓ | Move every requirement in one status        |
| DELETE | `/api/projects/{id}/requirements/{rid}`        | ✓ | Delete (not when approved) → `204`          |
| GET    | `/api/projects/{id}/requirements/{rid}/history`| ✓ | Revision history, newest first              |
| GET    | `/api/projects/{id}/architecture`                 | ✓ | Architecture (or null), approved requirements, readiness |
| POST   | `/api/projects/{id}/architecture/generate`        | ✓ | Generate / regenerate (draft only) from approved requirements |
| PUT    | `/api/projects/{id}/architecture`                 | ✓ | Save edited content (needs `version`; not when approved) |
| POST   | `/api/projects/{id}/architecture/status`          | ✓ | Move through draft → in review → approved   |
| GET    | `/api/projects/{id}/architecture/revisions`       | ✓ | Revision history, newest first              |
| GET    | `/api/projects/{id}/architecture/revisions/{n}`   | ✓ | One earlier revision with its full content  |

Intake endpoints call Claude and can take up to a minute. AI failures return a problem detail with
a `reason` (`NOT_CONFIGURED`, `RATE_LIMITED`, `TIMEOUT`, …) and a message safe to show users.

Authenticated endpoints expect `Authorization: Bearer <accessToken>`. Missing or invalid tokens get
`401`. Another user's project returns `404`, so its existence is never revealed. Errors use the
RFC 9457 `application/problem+json` format.

Example:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"ada@example.com","password":"correct-horse","displayName":"Ada"}'
```

## Using AI features

1. Choose a provider in `.env`:
   - **Claude** (default, paid): key from https://console.anthropic.com → `CLAUDE_API_KEY`.
   - **Gemini** (free tier for Flash models): key from https://aistudio.google.com →
     `GEMINI_API_KEY`, and set `AI_PROVIDER=gemini`. On the free tier Google may use prompts and
     responses to improve its products, so don't send confidential ideas.
2. Restart the backend (`docker compose up -d --build backend`).
3. Open a project and use **Idea intake**: describe the idea, answer Claude's questions, read the brief.

Without a key everything else works; the intake panel explains that AI is not configured.
Each AI call is logged with its token usage (`AI call provider=… purpose=… inputTokens=… outputTokens=…`).
Tests never call a real AI provider: it is mocked, so `mvn test` needs no key and costs nothing.

## Documentation

- [Architecture](docs/architecture.md)
- [Development plan](docs/development-plan.md)
