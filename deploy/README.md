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
   - Grafana:  http://localhost:3000 (anonymous Admin access, no login)
   - Postgres: `localhost:5432` (db `loan`, user `loan`)

   Grafana is configured for anonymous Admin access for this local POC (no credential
   stored anywhere). Do not use this configuration outside a local kind cluster.

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

Validated on a live kind cluster: the full journey runs through `localhost:8088`
(frontend -> backend Quill -> Postgres), rows persist with `correlation_id`/`user_uuid`,
logs reach Loki (via promtail) and JVM metrics reach Prometheus (via the OTel agent ->
collector). Traces are not yet emitted (no spans generated; see the tracing task).

Note on image versions: Helm `repo update` can bump a chart's app image to a tag not in
the preloaded set, causing `ImagePullBackOff` (the kind node can't pull through the
network's TLS interception). If that happens, `podman pull` the exact image on the host,
`kind load image-archive`, and restart the pod.
