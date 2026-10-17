# ForgeAI

ForgeAI is an AI-powered software engineering workspace. It takes a natural-language software idea and
progressively turns it into a complete, reviewable delivery:

```
Idea → Requirements → Architecture → Database Design → API Design
     → Development Tasks → Code → Tests → Code Review → Documentation
```

Claude acts as the reasoning engine. The application itself owns everything around it: projects,
persistent context, approvals, tasks, generated artifacts and the development workflow.

> **Status: Phase 1 — Foundation.** This repository currently contains the project skeleton: a Spring Boot
> API with a health check and PostgreSQL connectivity, and a React application shell. The product
> features are planned in [`docs/development-plan.md`](docs/development-plan.md).

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
│   │   ├── config/              Security and startup database verification
│   │   └── health/              GET /api/health
│   ├── src/main/resources/application.yml
│   ├── src/test/java/...        Web-layer tests
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                    React + Vite application
│   ├── src/
│   │   ├── components/layout/   App shell: sidebar, header, layout
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

Then edit `.env` and set at least `DATABASE_PASSWORD`.

| Variable            | Purpose                                                  | Used from |
| ------------------- | -------------------------------------------------------- | --------- |
| `DATABASE_URL`      | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/forgeai` | Phase 1   |
| `DATABASE_USERNAME` | Database user                                            | Phase 1   |
| `DATABASE_PASSWORD` | Database password (required)                             | Phase 1   |
| `CLAUDE_API_KEY`    | Claude API key                                           | Phase 3   |
| `JWT_SECRET`        | Secret used to sign authentication tokens                | Phase 2   |

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

No Java or Maven installed? Run the tests in a container instead (from the project root):

```bash
docker run --rm -v "${PWD}/backend":/workspace -w /workspace maven:3.9-eclipse-temurin-25 mvn -B test   # PowerShell / bash
docker run --rm -v "%cd%\backend":/workspace -w /workspace maven:3.9-eclipse-temurin-25 mvn -B test   # cmd
```

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

Every other endpoint currently requires authentication and returns `401 Unauthorized`. Errors use
the RFC 9457 `application/problem+json` format.

## Documentation

- [Architecture](docs/architecture.md)
- [Development plan](docs/development-plan.md)
