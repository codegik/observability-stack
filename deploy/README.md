# Deployment (kind, local)

Deploys the loan POC and the observability stack onto a local `kind` cluster.

## Prerequisites

- A container runtime for kind. This POC uses **podman**; its VM clock has been
  observed skewed (image pulls fail with a TLS not-yet-valid error). Resync the podman
  machine clock before building/pulling images.
- `kind`, `kubectl`, `helm`, `sbt`, `node`/`npm`.

## Steps

1. Create the cluster:

   ```
   kind create cluster --name kind
   ```

   Postgres uses `trust` auth (passwordless) for this local POC, so there is no DB secret
   to manage. Do not use this configuration outside a local kind cluster.

2. Build images and load them into kind:

   ```
   ./scripts/build-images.sh
   ```

3. Deploy app + observability stack:

   ```
   ./scripts/deploy.sh
   ```

4. Access:

   ```
   kubectl --context kind-kind -n observability-stack port-forward svc/loan-frontend 8088:80
   kubectl --context kind-kind -n observability-stack port-forward svc/grafana 3000:80
   ```

   Grafana admin password:

   ```
   kubectl --context kind-kind -n observability-stack get secret grafana \
     -o jsonpath='{.data.admin-password}' | base64 -d
   ```

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
