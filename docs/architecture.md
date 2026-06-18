# Observability Stack - Loan Application POC

## 1. Purpose

This is an observability proof-of-concept built around a small but realistic loan
application flow. The loan domain exists to generate meaningful work for the system
to observe; the primary deliverable is the observability capability, not the loan
product itself.

Every architectural decision in this document is made in service of observability.
When a loan-domain choice and an observability choice conflict, observability wins.

The initial observability focus is **runtime thread and fiber dump analysis**.

## 2. Goals and non-goals

### Goals
- A working anonymous-to-identified loan request flow that produces traffic worth observing.
- End-to-end correlation: one request can be followed from the browser, through the
  backend, into the database rows it created, and across logs, traces and metrics.
- Runtime analysis of what the JVM and the ZIO runtime are doing: JVM thread dumps,
  ZIO fiber dumps, and continuous JFR recording with on-demand snapshots.
- A single pane (Grafana) to move between traces, metrics and logs using correlation id.

### Non-goals (for the POC)
- Authentication, authorization, sessions, passwords.
- Credit decisioning, underwriting, regulatory compliance, real money movement.
- High availability, horizontal scaling, production hardening.

## 3. Technology stack

| Layer | Choice |
|---|---|
| Database | PostgreSQL |
| Backend language | Scala 3 |
| Backend effect system | ZIO |
| Backend HTTP server | zio-http |
| Database access | zio-jdbc |
| JVM | JDK 25 |
| Build | sbt |
| Frontend | React (JavaScript) |
| Tracing/metrics/logs export | OpenTelemetry: Java agent (auto) + zio-opentelemetry (ZIO-aware), OTLP |
| Trace store | Tempo |
| Metric store | Prometheus |
| Log store | Loki |
| Dashboards | Grafana |
| Runtime dump analysis | JFR (continuous + on-demand), JVM thread dump, ZIO fiber dump |
| Deployment | Kubernetes (kind, local) |

## 4. Functional flow

The customer journey is four screens. No login at any point.

```
[1] Configure loan        [2] Personal info        [3] Choose an offer       [4] Finish
    (anonymous)               (creates user)            (best matches)            (confirm)
    amount, term,      -->    name, email,       -->    system returns      -->   selected
    purpose                   etc.                       N closest products        application
```

1. **Configure loan (anonymous).** The customer enters what they need: amount, term,
   purpose. No account exists yet. The UI mints an anonymous user identifier (UUID)
   and a correlation id and sends both on every backend call from here on.
2. **Personal info.** The customer provides name, email and supporting details. The
   backend creates a real user keyed by email and associates the anonymous UUID with
   that email. One email may accumulate many UUIDs over time (Section 6).
3. **Choose an offer.** The backend matches the request against pre-registered loan
   products and returns the options closest to what the customer asked for (Section 7).
4. **Finish.** The customer selects one offer; the application is recorded and the flow ends.

### Sequence (happy path)

```
Browser            Backend (ZIO)                 Postgres
  | configure loan      |                            |
  |-- POST /loan-requests (X-Correlation-Id, X-User-Id) ->|
  |                     | validate headers           |
  |                     | persist loan_request -------->| (correlation_id, user_uuid stored)
  |<-- 201 request id --|                            |
  | personal info       |                            |
  |-- POST /users (headers) -------------------------->|
  |                     | upsert user by email       |
  |                     | link user_uuid -> email ----->|
  |<-- 200 user --------|                            |
  | get offers          |                            |
  |-- GET /loan-requests/{id}/offers (headers) ------->|
  |                     | load products, rank match    |
  |                     | read products <---------------|
  |<-- 200 offers ------|                            |
  | choose offer        |                            |
  |-- POST /applications (headers) ------------------->|
  |                     | persist application --------->| (correlation_id, user_uuid stored)
  |<-- 201 application -|                            |
```

## 5. Backend architecture

The backend is a single ZIO HTTP service composed of clearly separated layers. Names
are indicative, not final.

```
HTTP routes (zio-http)
  |
  v
Header middleware  -- validates X-Correlation-Id + X-User-Id, rejects if missing,
  |                   puts both into the request context (FiberRef + log annotations
  |                   + OTel span attributes)
  v
Application services
  - LoanRequestService     (capture what the customer needs)
  - UserService            (create/associate user by email)
  - OfferMatchingService   (rank pre-registered products against the request)
  - ApplicationService     (record the chosen offer)
  v
Repository layer (Postgres)
  - every write carries correlation_id and user_uuid
  v
Observability layer (cross-cutting)
  - tracing, metrics, structured logging, dump endpoints
```

Key principles:
- The header middleware is the single choke point that guarantees the two required
  headers are present and pushed into context before any business logic runs.
- The context values (correlation id, user uuid) travel via ZIO `FiberRef` so they are
  available to every fiber spawned during the request without being threaded through
  function signatures by hand.
- The repository layer never persists a row without both correlation id and user uuid.

## 6. Identity and correlation model (non-functional requirements)

This section is normative. These rules are mandatory and apply system-wide.

### 6.1 Correlation id
- Header name: `X-Correlation-Id`.
- Originates in the UI and is sent on **every** backend request.
- The backend **must** propagate it **everywhere**: into logs (every log line), into
  traces (span attribute), into metrics exemplars where supported, into any outbound
  call, and **into every database row the request creates or mutates** via a
  `correlation_id` column.
- If a request arrives without it, the request is rejected (Section 6.3).

### 6.2 User identification
- Header name: `X-User-Id`.
- Follows the same propagation rules as the correlation id, including persistence on
  every database row via a `user_uuid` column.
- The user does not exist as a real account until they provide an email. Until then the
  UI generates and sends a unique anonymous UUID.
- When the email is provided, the current anonymous UUID is associated with that email.
- The relationship is **one email to many UUIDs**: the same person returning later (new
  device, cleared storage) gets a new UUID, and all such UUIDs map to the one email.

Data model for identity:

```
users
  email           text primary key
  display_name    text
  monthly_income  numeric       -- collected on screen 2, used for affordability matching
  created_at      timestamptz
  correlation_id  text          -- correlation id of the request that created the user
  user_uuid       text          -- the anonymous uuid present at creation

user_identities
  user_uuid       text primary key   -- anonymous identifier minted by the UI
  email           text references users(email)   -- null until associated
  first_seen_at   timestamptz
  correlation_id  text
```

### 6.3 Header enforcement
- Every backend API that serves the frontend **must require both headers**.
- Missing or empty `X-Correlation-Id` or `X-User-Id` results in `400 Bad Request`.
- Exemptions: operational endpoints only (health check and the `/admin/*` dump
  endpoints in Section 8.4), since those are not frontend traffic.

### 6.4 Repository-wide note (mandatory convention)
**Both `correlation_id` and `user_uuid` are persisted on every domain table.** This is
deliberate and central to the POC: it lets us start from a database row and pivot to the
exact trace and log lines that produced it. Any new table added to this system must
include these two columns. This note is recorded here as the repository convention.

## 7. Loan products and offer matching

Purpose is a fixed set across requests and products:
`HOME_IMPROVEMENT | DEBT_CONSOLIDATION | AUTO | MEDICAL | EDUCATION | OTHER`.

### 7.1 Pre-registered products
The system is seeded with a catalogue of loan products. Each product defines the
envelope of loans it can serve.

```
loan_products
  id              uuid primary key
  name            text
  min_amount      numeric
  max_amount      numeric
  min_term_months int
  max_term_months int
  apr             numeric
  purpose         text          -- enum above; the use case the product targets
  created_at      timestamptz
```

### 7.2 Matching
Given a customer request (amount, term, purpose) and the customer's monthly income, the
matcher returns the **top 3** products closest to the request, not only exact fits.

- Hard filter: product envelope can plausibly serve the request (amount and term within,
  or near, the product range; purpose compatible).
- Affordability: for each candidate, compute the monthly payment from amount, term and
  APR (standard amortization). Offers whose monthly payment exceeds a configured share of
  monthly income (for example 40%, tunable) are filtered out, or penalized in the score
  when no fully affordable option exists, so the customer is never shown nothing.
- Ranking: a distance score combining normalized differences in amount and term, with
  affordability headroom factored in and APR as a tie-breaker (lower is better). The
  smallest distance ranks first.
- Output: top 3 offers, each annotated with its monthly payment and how it differs from
  the request, so the UI can explain the trade-off.

The matcher is deliberately simple and synchronous; it exists to produce realistic,
observable work (DB reads, income lookup, payment and ranking computation, spans,
metrics) rather than to be a sophisticated recommender.

### 7.3 Loan request and application tables

```
loan_requests
  id              uuid primary key
  amount          numeric
  term_months     int
  purpose         text          -- enum in Section 7
  user_uuid       text
  correlation_id  text
  created_at      timestamptz

loan_applications
  id              uuid primary key
  loan_request_id uuid references loan_requests(id)
  product_id      uuid references loan_products(id)
  email           text references users(email)
  status          text
  user_uuid       text
  correlation_id  text
  created_at      timestamptz
```

## 8. Observability architecture (primary focus)

### 8.1 Data flow

```
React UI ----(X-Correlation-Id, X-User-Id)----> ZIO Backend
                                                   |
                          OpenTelemetry SDK (traces, metrics, logs)
                                                   |
                                              OTel Collector (OTLP in)
                                  /                |                 \
                            Tempo (traces)   Prometheus (metrics)   Loki (logs)
                                  \                |                 /
                                            Grafana (single pane, correlated by
                                            correlation_id and trace id)
```

### 8.2 Tracing
- Instrumentation is two-layered: the **OpenTelemetry Java agent** auto-instruments the
  HTTP server and JDBC for broad, zero-code coverage, and **zio-opentelemetry** provides
  the ZIO-aware context and explicit business spans (offer matching, etc.).
- The agent supplies the `trace_id`; zio-opentelemetry sets `correlation_id` and
  `user_uuid` (from the FiberRef context) as attributes on the root span, so any trace
  can be found by either value, and the same FiberRef context drives the per-fiber dump
  labeling in Section 8.6.
- A root span is created per inbound request; child spans wrap service calls and database
  operations. Spans are exported over OTLP to the collector and stored in Tempo.

### 8.3 Metrics and logs
- Metrics: request rate, latency, error rate, offer-match counts, plus JVM and ZIO
  runtime metrics (heap, GC, thread count, fiber count). Exported via OTLP to Prometheus.
- Logs: structured JSON. Every line carries `correlation_id`, `user_uuid` and the active
  `trace_id`. Shipped to Loki so a trace can jump to its logs and back.

### 8.4 Runtime thread and fiber dump analysis (initial focus)

This is the headline capability. It combines three complementary views, because on a ZIO
application a JVM thread dump alone is misleading: a small pool of carrier threads runs
thousands of fibers, so JVM threads show parked workers while the interesting work is at
the fiber level.

**(a) JFR continuous recording.** Java Flight Recorder runs always-on from startup with a
rolling buffer, capturing thread states, lock contention, allocation and I/O at low
overhead. This gives us the seconds *leading up to* a problem, not just the instant we
notice it. Configured via JVM startup flags.

**(b) On-demand JVM thread dump.** An admin endpoint returns the current platform-thread
state (via `ThreadMXBean`): stacks, states, lock ownership. Answers "what is the JVM doing
right now?"

**(c) On-demand ZIO fiber dump.** An admin endpoint returns all live fibers with their
status and stack traces (via the ZIO runtime's fiber dump). This is what actually reveals
"fiber X is blocked awaiting this promise at this line," which the JVM dump cannot show.

**(d) Automatic capture on trigger.** The system automatically captures a dump bundle
(JVM thread dump, ZIO fiber dump, and a JFR snapshot of the rolling buffer) without any
operator action when any trigger fires. Each bundle is stamped with the originating
`correlation_id`, `user_uuid`, `trace_id` (where a request is involved), the trigger
kind, and the observed measurement, so the captured state is tied directly to what
triggered it.

Triggers (all enabled):
- **Latency over threshold** — a request exceeds a configured per-request latency
  (for example 2s, tunable).
- **Unhandled error / 5xx** — a request fails with an unhandled error or returns 5xx;
  the runtime state at the moment of failure is preserved.
- **Stuck fiber detected** — a background watcher fires when a fiber stays alive with no
  progress beyond a threshold (likely deadlock or blocked-on-resource). This is the
  trigger most aligned with the thread/fiber dump analysis focus.
- **Runtime pressure** — JVM/ZIO runtime metrics cross configured limits (fiber count,
  heap usage, GC pause), catching saturation rather than a single slow request.

Auto-capture rules:
- Rate limiting: a cooldown window prevents a dump storm when many triggers fire at once;
  at most one bundle is captured per cooldown window per trigger kind, and the count of
  suppressed triggers is recorded so nothing is silently dropped.
- Storage: bundles are written to a persistent volume (Section 9) with count-based
  retention (keep the last N bundles). Their dump ids, trigger kind and originating
  correlation id / user uuid are emitted as a log line and a span event.
- Inspection: a Grafana panel lists captured bundles (id, trigger, correlation id,
  user uuid, latency/measurement) and links each to its triggering trace by
  `correlation_id`; the admin endpoints below back the same data.

Admin surface (operational, not frontend traffic, exempt from the header rule):

```
GET  /admin/health
GET  /admin/threads        -> JVM thread dump (ThreadMXBean)
GET  /admin/fibers         -> ZIO fiber dump (all live fibers)
POST /admin/jfr/dump       -> write/return a JFR snapshot of the rolling buffer
GET  /admin/captures       -> list auto-captured dump bundles (id, correlation id, latency)
GET  /admin/captures/{id}  -> retrieve a specific auto-captured bundle
```

Each dump response is stamped with a server timestamp and a generated dump id so it can
be referenced from notes and correlated with the trace/metric timeline in Grafana.

### 8.5 What "good" looks like for the POC
- Pick any loan application row in Postgres, read its `correlation_id`, and in Grafana
  land on the exact trace and the exact log lines for that request.
- While the system is under load, capture a JVM thread dump, a ZIO fiber dump, and a JFR
  snapshot, and reason about where time and fibers are going.
- Any trigger (slow request, 5xx, stuck fiber, runtime pressure) produces an automatic
  dump bundle tied to its correlation id, listed in Grafana and retrievable later without
  anyone having watched the system live.

### 8.6 Associating dumps with the user journey

A loan journey is four separate HTTP requests (configure, personal info, offers, finish)
with human think-time in between. Between requests there are no fibers doing work for that
journey, so no single dump shows the journey as a whole. A dump shows what the runtime is
doing for the request(s) in flight at the instant of capture; the journey is reassembled
at query time in Grafana. Two keys do two jobs:

- **`correlation_id` is per request.** It ties a dump to one request (for example, the
  slow offer-matching call). It is minted per backend call.
- **`user_uuid` is the journey key.** The UI mints it once on the first screen and sends
  it unchanged across all four requests, so it stitches the requests, their traces and
  their dumps into a single journey. (One email may later map to many such uuids, per
  Section 6; within a single journey there is exactly one.)

Three layers of association, strongest first:

**1. Bundle-level (designed, Section 8.4d).** Every auto-captured bundle is stamped with
the triggering request's `correlation_id`, `user_uuid` and `trace_id`. This already
answers "which request, and whose journey, does this dump belong to."

**2. Per-fiber labeling (the authoritative per-journey view).** Because `correlation_id`
and `user_uuid` are held in `FiberRef`s that every child fiber inherits, each fiber
serving a request carries the journey it belongs to. The ZIO fiber dump is therefore
labeled per fiber: a single dump under load shows which fibers serve which journey and
which one is stuck. Realized by reading, at capture time, each fiber's correlation id and
user uuid. Mechanism:
   - Preferred: read the fiber's `FiberRefs` from the fiber dump directly. The exact
     ZIO 2 API for reading another fiber's refs must be verified before relying on it.
   - Fallback (no dependency on reading foreign fibers' refs): the header middleware
     maintains a `Ref[Map[FiberId, JourneyContext]]` via `acquireRelease` (entry added
     when a request starts, removed when it completes); at capture time this map is joined
     against `Fiber.dumpAll`, which carries each fiber's `FiberId`.

**3. JVM thread dump (supporting detail only).** A carrier thread runs many fibers over
its life and exactly one at the capture instant; parked threads serve no journey. So a
JVM thread dump can be mapped to a journey only for actively-running threads, via
thread -> current fiber -> journey. The fiber dump, not the JVM thread dump, is the
authoritative per-journey view.

The trace gives the per-journey timeline (spans); the dump is the complementary "what was
the runtime actually doing inside that span" view. They are linked by the shared
`correlation_id`, `user_uuid` and `trace_id`.

## 9. Deployment (Kubernetes, kind, local)

The stack runs on a local `kind` cluster in a single namespace.

```
namespace: observability-stack

postgres           Deployment + Service + PVC (data)
backend            Deployment + Service        (Scala 3 / ZIO, JDK 25)
                     JVM flags: OTel Java agent (-javaagent) + JFR continuous recording
                     + PVC mounted for dump bundles (Section 8.4d)
frontend           Deployment + Service        (React)
otel-collector     Deployment + Service        (OTLP receiver, fans out to the three stores)
tempo              Deployment + Service + PVC  (traces)
prometheus         Deployment + Service + PVC  (metrics)
loki               Deployment + Service + PVC  (logs)
grafana            Deployment + Service        (dashboards, pre-wired to all three datasources)
```

kind-specific mechanics:
- Local images are loaded into the cluster with `kind load docker-image` (no external
  registry needed for the POC).
- Access is via `kubectl port-forward` to the frontend and to Grafana; optionally a
  kind ingress with `extraPortMappings` if we want stable host ports.
- Configuration (correlation/header rules are app-level; thresholds, OTLP endpoint,
  JFR flags) is supplied via ConfigMap and env vars.

Dump capture on Kubernetes:
- The primary path is in-process: the admin endpoints (Section 8.4) and the automatic
  latency-triggered capture run inside the backend pod and write bundles to the mounted
  PVC, so capture does not depend on catching the pod live.
- JFR continuous recording is enabled by JVM startup flags in the backend container.
- Manual fallback when needed: `kubectl exec` into the backend pod to run `jcmd`, or
  `kubectl debug` with an ephemeral container, to obtain a dump out-of-band.
- The dump PVC outlives a pod restart for the bundles already written; live JFR buffer
  state does not survive a restart, which is acceptable for the POC.

## 10. API surface (frontend-facing)

All of the following require `X-Correlation-Id` and `X-User-Id`.

```
POST /loan-requests                      capture what the customer needs
POST /users                              create/associate user by email
GET  /loan-requests/{id}/offers          best-match offers for a request
POST /applications                       record the chosen offer
```

## 11. Open questions and future work
- Whether to also index dump bundle metadata in Loki for searchability, beyond the
  log line and span event already emitted at capture time.
- Tuning the trigger thresholds (latency, stuck-fiber, runtime pressure) and the cooldown
  window under realistic load.
- Tuning N for count-based bundle retention on the PVC.
```

## 12. Glossary
- **Correlation id:** UI-generated, per-request identifier tying together everything
  produced by one backend request, across UI, backend, DB rows, logs, traces, metrics
  and dumps.
- **User uuid:** UI-generated anonymous identifier minted once per journey and sent
  unchanged across all of the journey's requests; it is the journey key for stitching
  requests, traces and dumps together. Many uuids may later map to a single email.
- **Fiber:** ZIO's lightweight unit of concurrency, scheduled onto JVM carrier threads.
- **JFR:** Java Flight Recorder, the JVM's built-in low-overhead event recorder.
