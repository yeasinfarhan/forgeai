# Security review

The state of ForgeAI's security at release 1.0: what is protected, how, and what remains for the
operator. Report issues privately to the maintainers rather than in a public issue.

## Authentication and accounts

- Passwords are hashed with BCrypt; passwords over 72 bytes (BCrypt's limit) are rejected rather
  than silently truncated. Sign-in takes the same time whether or not the email exists, and errors
  do not reveal which.
- Failed sign-ins are limited per email and per client address (see
  [operations](operations.md#sign-in-protection)). The per-email limit lets someone lock an account
  for 15 minutes by guessing; this is the usual trade-off against password guessing.
- Access tokens are HS256 JWTs signed with `JWT_SECRET` (at least 32 bytes, required at startup),
  checked for issuer and expiry (`JWT_EXPIRATION`, 8 hours by default). The web app keeps the token
  in the browser's local storage and sends it as a bearer header, not a cookie, so CSRF does not
  apply. Local storage is readable by scripts on the page, which is one reason the Content Security
  Policy only allows the app's own scripts. There are no refresh tokens; signing in again issues a
  new token.

## Authorisation

- Every project endpoint checks that the project belongs to the signed-in user before anything
  else. Another user's project answers 404, so its existence is not revealed. This is covered by an
  "other users cannot see …" integration test per module.
- Usage figures (`/api/usage`) are only ever the caller's own.

## Input handling

- Every request body is validated (Bean Validation); invalid input gets 400 with field messages.
  Errors use RFC 9457 problem details and never include stack traces or internal messages.
- All database access goes through JPA with bound parameters; there is no string-built SQL.
- Generated code paths are normalised and restricted to the project: no absolute paths, no `..`,
  nothing under `.git/`, no `.env` files. Exports (ZIP, SQL, OpenAPI, Markdown) are built from
  stored data, never from file system paths.
- Markdown is rendered without raw HTML; links to other sites open in a new tab without referrer,
  and images are not loaded. Mermaid runs with its strict security level (no scripts, no click handlers).

## AI-specific risks

- **Prompt injection**: every prompt tells the model that the user's material (idea, requirements,
  design, code) is data, not instructions. More importantly, nothing the model returns is executed:
  every answer is parsed against a JSON schema and repaired by the same rules as a person's edit, and
  every generated file must be accepted by a person in a diff before it enters the project.
- **Cost and abuse**: per-user request and token limits, a cap on output tokens per call, one
  generation at a time per project and stage, and every call recorded with its token count.
- **Secrets**: API keys come only from the environment; they are never logged, stored or sent to
  the browser. Generated `.env` files are refused (only `.env.example`).
- **Data sharing**: prompts include the project's material. With Gemini's free tier, Google may use
  prompts to improve its products (noted in `.env.example`); choose the provider accordingly.

## Transport and browser

- nginx sends a Content Security Policy (scripts only from the app itself; styles inline for
  diagrams; fonts from Google Fonts; no framing), `X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` and a restrictive `Permissions-Policy`,
  and hides its version. The API adds Spring Security's default headers.
- The backend port is bound to 127.0.0.1 by `docker-compose.yml`; browsers reach the API through
  nginx. Actuator endpoints (metrics) are on a separate port that is not published.
- TLS is the operator's part: terminate HTTPS in front of the frontend and add HSTS there.

## Dependencies and build

- Versions are managed by the Spring Boot parent and pinned ranges in `package.json`; CI builds and
  tests every change. Run `npm audit` and a dependency check (e.g. OWASP Dependency-Check or GitHub
  Dependabot) regularly.
- Images are multi-stage builds; the backend runs as a non-root user.

## Known limitations

- Rate-limit counters for sign-ins are per instance and in memory; run one backend instance or
  move them to a shared store before scaling out.
- Tokens cannot be revoked before they expire (no server-side sessions); keep `JWT_EXPIRATION` short
  where that matters, and rotate `JWT_SECRET` to sign everyone out.
- There is no account recovery or email verification yet.
