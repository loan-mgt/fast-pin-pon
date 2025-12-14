Purpose

This file contains concise, repo-specific guidance for AI coding agents working on Fast Pin Pon. Focus on concrete, discoverable patterns (how services communicate, where to modify API, how to regenerate DB bindings), not generic advice.

Quick Start (commands)

- **Build API**: `cd api && go build ./...`
- **Run tests**: `cd api && go test ./...`
- **Run locally (containers)**: `docker-compose up --build`
- **Run production compose**: `docker-compose -f docker-compose.prod.yml up --build`
- **Regenerate DB bindings**: from `api/` run `sqlc generate` (uses `api/sqlc.yaml`) after modifying `api/internal/db/queries/*.sql`
- **Apply migrations**: SQL files in `api/migrations/` — run against Postgres (the project uses PostGIS; look at `database/initdb-postgis.sh`)

Big Picture — Components & Boundaries

- `api/` — main Go backend (REST API). Key subfolders:
  - `internal/server/` : HTTP handlers, router, DTOs and helper functions (see `router.go`, `dto.go`, `server.go`). Handlers use helper functions like `s.writeJSON`, `s.writeError`, `decodeAndValidate`.
  - `internal/db/sqlc/` : generated DB access code produced by `sqlc`. Queries live in `internal/db/queries/`.
  - `migrations/` : SQL migration files applied to DB.
- `database/` — DB container setup + PostGIS init scripts.
- `infra/keycloak/` — Keycloak realm exports used for auth integration.
- `network/` & `simulation/` — simulation and network adapters (Python and Java); they communicate with the API using the REST endpoints defined in `api/`.

Data flows / integration points

- API <-> Postgres: core persistence (use `sqlc` types and `pgx` for Postgres connectivity). Look at `api/internal/db/sqlc` for generated types and `api/internal/db/queries` for SQL.
- Simulation/network -> API: telemetry and events are posted to endpoints such as `/v1/units/{unitID}/telemetry` and `/v1/units` (see `api/internal/server/handlers_units.go` for examples).
- Auth: Keycloak realm JSON in `infra/keycloak/` — CI/infra expects Keycloak for auth flows (check CI workflows in `.github/workflows`).

Project-specific conventions & patterns

- SQLC-first DB: SQL files under `api/internal/db/queries/` are the source of truth. Always update SQL, then run `sqlc generate`.
- Handler patterns: HTTP handlers live in `api/internal/server/*.go`. Common helpers/patterns used across handlers:
  - `decodeAndValidate(r, &req)` for request payloads
  - `s.parseUUIDParam(r, "unitID")` to parse path UUID
  - `s.writeJSON(w, status, payload)` and `s.writeError(w, status, code, detail)` for responses
  - Swagger-style annotations above handlers (e.g., `// @Summary`, `// @Router`) — keep them in sync when adding endpoints.
- DB / types: generated `sqlc` package provides enums and structs (e.g., `db.UnitStatus`). Code maps DB `pgx` types (e.g., `pgtype.Timestamptz`, `pgtype.UUID`) to Go types via helper functions (`timestamptzPtr`, `uuidString`). See `handlers_units.go` for mapping examples.
- Status enums and validation: when accepting status strings, convert to `db.UnitStatus` and validate against allowed values. Example allowed statuses in `handlers_units.go`: `available en_route on_site maintenance offline`.

Editing queries / adding endpoints

- To add a DB query: add a new `.sql` to `api/internal/db/queries/`, run `cd api && sqlc generate`, then use the generated functions in `api/internal/server`.
- To add an endpoint: add handler in `api/internal/server`, register route in `router.go`, add swagger comments, then rebuild.

Examples (where to look)

- Telemetry & status example: `api/internal/server/handlers_units.go` — shows request validation, `sqlc` params, mapping PG types, and response construction.
- DB schema and migrations: `api/migrations/001_init.sql` and related files.
- DB container / PostGIS init: `database/initdb-postgis.sh` and `database/Dockerfile`.
- CI / publishing Swagger: `.github/workflows/swagger-pages.yml` and `README.md` mention auto-published Swagger UI.

Developer notes & gotchas

- Timezones: service uses UTC for timestamps (see `time.Now().UTC()` in handlers). Use `pgx` timestamptz mapping helpers when reading/writing times.
- pgx/pgtype: some DB fields use `github.com/jackc/pgx/v5/pgtype` types — prefer helper mapping functions already in `internal/server` rather than new ad-hoc conversions.
- Rebuilding generated code: failing to run `sqlc generate` after changing SQL will produce compile errors because `api/internal/db/sqlc` is committed but must match queries.
- When running locally with Docker, the DB image includes PostGIS; volume and init script behavior is in `database/`.

CI / workflows to be aware of

- `.github/workflows/docker-build-api.yml` — builds API container
- `.github/workflows/swagger-pages.yml` — publishes Swagger UI from `main`
- Sonar/QoS checks in `.github/workflows/sonar.yml`

If you're unsure about something

- Search handlers under `api/internal/server/` for concrete examples (DTOs, response helpers, validation).
- For DB questions, open `api/internal/db/queries/*.sql` and the generated code in `api/internal/db/sqlc/`.

Next step

Please review this file for accuracy and tell me if you'd like more details about any area (example: detailed `sqlc` command flags, debug session steps for `dlv`, or mapping helper locations).
