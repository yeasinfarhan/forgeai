# Operating ForgeAI

How to run ForgeAI beyond a laptop: configuration, limits, logs, metrics, tracing, CI and releases.
For setup and the full list of environment variables, see the [README](../README.md).

## Deployment shape

`docker compose up -d --build` starts three containers:

| Service    | Port (host)                  | Notes                                                        |
| ---------- | ---------------------------- | ------------------------------------------------------------ |
| `frontend` | `FRONTEND_PORT` (3000)       | nginx: the web app, and `/api` proxied to the backend         |
| `backend`  | `127.0.0.1:BACKEND_PORT` (8080) | Spring Boot API, only reachable from the host itself      |
| `backend`  | 8081 (not published)         | Actuator: health, Prometheus metrics, inside the Compose network |
| `postgres` | `POSTGRES_PORT` (5432)       | Data in the `postgres-data` volume                            |

Put a TLS-terminating reverse proxy (or a load balancer) in front of the frontend for anything
reachable from other machines, and add `Strict-Transport-Security` there. Back up the
`postgres-data` volume (`pg_dump`); everything ForgeAI knows is in PostgreSQL.

The backend shuts down gracefully (finishes running requests) and its image runs as a non-root user.

## AI usage limits

Every AI call is recorded per user and project (table `ai_usage`): purpose, provider, model,
tokens, outcome and duration. Two limits apply to each user over a rolling window, checked before
each call:

| Variable               | Default   | Meaning                                         |
| ---------------------- | --------- | ----------------------------------------------- |
| `AI_REQUESTS_PER_HOUR` | 60        | AI calls in any 60 minutes                       |
| `AI_TOKENS_PER_DAY`    | 2 000 000 | Input + output tokens in any 24 hours            |

`0` turns a limit off. A refused call gets HTTP 429 with `Retry-After` and a message saying when the
user can try again. Failed calls count as requests (they reached the provider); calls refused
because no API key is set are not recorded. Users see their own usage on the **AI usage** page
(`GET /api/usage`): the last hour, 24 hours, 14 days, and the last 30 days by kind of call and project.

The model, output-token cap per call (`AI_MAX_TOKENS`), timeout and retries are set in `.env` too.
Provider prices change, so ForgeAI reports tokens, not money; multiply by your provider's price list.

## Sign-in protection

After `LOGIN_MAX_FAILURES_PER_EMAIL` (10) failed sign-ins for one email, or
`LOGIN_MAX_FAILURES_PER_ADDRESS` (50) from one client address, within 15 minutes, further attempts
get HTTP 429 until the oldest failure is 15 minutes old. A successful sign-in clears the email's
count. The counters are in memory (per backend instance, reset on restart). Behind the bundled
nginx, `FORWARD_HEADERS_STRATEGY=native` makes the backend see the client's address instead of nginx's.

## Logs

- In Docker the backend writes JSON logs (`LOG_FORMAT`: `ecs` by default, or `logstash`, `gelf`), one
  object per line, ready for Loki, Elasticsearch or any log shipper reading `docker logs`.
  Running the backend directly keeps plain-text logs.
- Every JSON log line carries the request's `traceId`/`spanId` and a `requestId` (plain-text logs
  show the trace and span ids). The request id comes
  from the `X-Request-Id` header (nginx sets one per request) or is generated, and is returned in
  the response's `X-Request-Id` header, so a user-reported error can be found in the logs.
- One line per API request: method, path (no query string), status and duration. One line per AI
  call: provider, purpose, model, tokens, duration and attempts.
- Levels: `FORGEAI_LOG_LEVEL` (default `INFO`) for ForgeAI's own code.

## Metrics

Prometheus format at `http://backend:8081/actuator/prometheus` (inside the Compose network;
`http://localhost:8081/...` when the backend runs directly). Besides the standard JVM, HTTP server,
database pool and Hibernate metrics, ForgeAI publishes:

| Metric                              | Type    | Tags                               |
| ----------------------------------- | ------- | ---------------------------------- |
| `forgeai_ai_requests_seconds`       | timer   | `provider`, `purpose`, `outcome`   |
| `forgeai_ai_tokens_total`           | counter | `provider`, `purpose`, `direction` |

Example scrape job, with Prometheus added to the same Compose project:

```yaml
scrape_configs:
  - job_name: forgeai
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["backend:8081"]
```

Health for orchestrators: `/actuator/health/liveness` and `/actuator/health/readiness` on port 8081.
`/api/health` (port 8080) stays the public, database-aware check the web app shows.

## Tracing

The backend traces every request with OpenTelemetry (Micrometer Tracing). Trace ids are always in
the logs. To send traces to a collector (Jaeger, Tempo, an OpenTelemetry Collector …), give the
backend container:

```yaml
    environment:
      MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT: http://otel-collector:4318/v1/traces
      TRACING_SAMPLING_PROBABILITY: "0.2"   # default 1.0 = every request
```

Metrics can be pushed over OTLP as well: `OTLP_METRICS_ENABLED=true` and
`MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://otel-collector:4318/v1/metrics`.

## Continuous integration and releases

`.github/workflows/ci.yml` runs on every push and pull request:

1. **Backend**: `mvn -B verify` on Java 25: compiles and runs all unit and integration tests
   (Testcontainers starts PostgreSQL). Test reports are uploaded when it fails.
2. **Frontend**: `npm install`, strict TypeScript check (`npm run typecheck`, the lint gate:
   strict mode, no unused code) and the production build.
3. **Images**: on pushes to `main` and on `v*` tags, after both pass, the backend and frontend
   images are built and pushed to GitHub Container Registry as
   `ghcr.io/<owner>/forgeai-backend` and `ghcr.io/<owner>/forgeai-frontend`, tagged with the
   branch, the short commit SHA and, for tags like `v1.0.0`, the version. It uses the workflow's
   own `GITHUB_TOKEN`; no secrets need to be configured.

To release: `git tag v1.0.0 && git push origin v1.0.0`.
