# Deployment (kind, local)

Deploys the loan POC and the observability stack onto a local `kind` cluster.

## Prerequisites

- A container runtime for kind. This POC uses **podman**; its VM clock has been
  observed skewed (image pulls fail with a TLS not-yet-valid error). Resync the podman
  machine clock before building/pulling images.
- `kind`, `kubectl`, `helm`, `sbt`, `node`/`npm`.

## Steps

1. Create the cluster (with host port mappings):

   ```
   kind create cluster --name kind --config deploy/kind-cluster.yaml
   ```

   `kind-cluster.yaml` maps host ports to NodePort services so dependencies are reachable
   from the host with no port-forward: frontend `:8088`, grafana `:3000`, postgres `:5432`.
   Postgres uses `trust` auth (passwordless) for this local POC, so there is no DB secret
   to manage. Do not use this configuration outside a local kind cluster.

2. Build images and load them into kind:

   ```
   ./scripts/build-images.sh
   ```

3. Deploy app + observability stack (creates the cluster from the config above if absent):

   ```
   ./scripts/deploy.sh
   ```

4. Access (no port-forward needed):

   - Frontend: http://localhost:8088
   - Grafana:  http://localhost:3000
   - Postgres: `localhost:5432` (db `loan`, user `loan`)

   Grafana admin password:

   ```
   kubectl --context kind-kind -n observability-stack get secret grafana \
     -o jsonpath='{.data.admin-password}' | base64 -d
   ```

## Local development

The kind cluster provides the service dependencies (Postgres, and the observability
stack). For local backend work, run the cluster and point the backend/tests at the
cluster Postgres exposed on `localhost:5432`:

```
./scripts/deploy.sh          # brings up postgres + everything else
cd backend && sbt run        # DB_URL defaults to jdbc:postgresql://localhost:5432/loan
cd backend && sbt test       # same default; runs against the cluster Postgres
```

No separate local Postgres install is needed.

## Signal flow

- Traces + metrics: backend OTel Java agent --OTLP--> otel-collector --> Tempo / Prometheus.
- Logs: backend JSON stdout --> promtail --> Loki. Capture log lines (`capture stored`)
  carry `capture_id`, `trigger`, `correlation_id`, `user_id` and back the "Dump Captures"
  dashboard.
- Dump bundles: written to the `loan-dumps` PVC; listed at `/admin/captures`.

## Verification status

App layer (Dockerfiles, postgres/backend/frontend manifests) follows the same shapes
verified locally. The **Helm observability layer values are a first-pass and have not yet
been validated against a live cluster** (cluster bring-up is gated on the podman clock
fix). Expect to iterate on the Loki single-binary, Prometheus remote-write, and
collector exporter settings on the first real deploy.
