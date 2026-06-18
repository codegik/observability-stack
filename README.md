# observability-stack

An observability-focused loan application POC. The loan flow exists to generate work worth
observing; the deliverable is the observability capability, with **runtime thread/fiber dump
analysis** as the initial focus.

See `docs/architecture.md` for the design and `deploy/README.md` for deployment.

## Stack

- Backend: Scala 3 / ZIO on JDK 25, `zio-http`, plain Postgres JDBC, `zio-opentelemetry` + OTel Java agent.
- Frontend: React (Vite).
- Observability: OpenTelemetry -> Collector -> Tempo / Prometheus / Loki, visualized in Grafana.
- Deploy: Kubernetes (kind) via Helm.

## Mandatory conventions

- Every frontend request carries `X-Correlation-Id` (per request) and `X-User-Id` (journey
  key). Both headers are required; missing either yields `400`.
- Both values are propagated everywhere, including `correlation_id` + `user_uuid` columns on
  **every** domain table. Any new table must include them.

## Loan flow

Anonymous configure -> personal details (creates a user by email) -> top-3 affordability-ranked
offers -> submit application.

## Observability

- `/admin/threads` JVM thread dump, `/admin/fibers` ZIO fiber dump.
- Auto-capture on four triggers (latency, 5xx, stuck fiber, runtime pressure), rate-limited
  with suppressed counts, bundles retained last-N on a volume, listed at `/admin/captures`.
- `/admin/fault/{slow,error,stuck,pressure}` deliberately exercise each trigger.
- Capture bundles include a JVM thread dump, a ZIO fiber dump, a JFR snapshot, and per-fiber
  journey labels (correlation_id / user_id), stamped with the triggering request's ids.

## Local development

Backend (needs a local Postgres on 5432, db/user `loan`):

```
cd backend && sbt run
```

Frontend:

```
cd frontend && npm install && npm run dev
```

Tests:

```
cd backend && sbt test
```

Generate traffic against a running backend:

```
BASE=http://localhost:8080 N=50 ./scripts/load.sh
```
