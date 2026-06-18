#!/usr/bin/env bash
set -euo pipefail

CTX=kind-kind
NS=observability-stack
CLUSTER="${KIND_CLUSTER:-kind}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
H="$ROOT/deploy/helm"

kind get clusters | grep -qx "$CLUSTER" || kind create cluster --name "$CLUSTER"
kubectl config get-contexts "$CTX" >/dev/null 2>&1 || { echo "context $CTX not found"; exit 1; }

kubectl --context "$CTX" apply -f "$ROOT/deploy/k8s/namespace.yaml"

helm repo add grafana https://grafana.github.io/helm-charts
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm repo update

helm --kube-context "$CTX" -n "$NS" upgrade --install tempo grafana/tempo -f "$H/tempo-values.yaml"
helm --kube-context "$CTX" -n "$NS" upgrade --install loki grafana/loki -f "$H/loki-values.yaml"
helm --kube-context "$CTX" -n "$NS" upgrade --install promtail grafana/promtail -f "$H/promtail-values.yaml"
helm --kube-context "$CTX" -n "$NS" upgrade --install prometheus prometheus-community/prometheus -f "$H/prometheus-values.yaml"
helm --kube-context "$CTX" -n "$NS" upgrade --install otel-collector open-telemetry/opentelemetry-collector -f "$H/otel-collector-values.yaml"
helm --kube-context "$CTX" -n "$NS" upgrade --install grafana grafana/grafana -f "$H/grafana-values.yaml"

kubectl --context "$CTX" -n "$NS" apply -f "$ROOT/deploy/k8s/postgres.yaml"
kubectl --context "$CTX" -n "$NS" apply -f "$ROOT/deploy/k8s/backend.yaml"
kubectl --context "$CTX" -n "$NS" apply -f "$ROOT/deploy/k8s/frontend.yaml"

echo "waiting for backend readiness..."
for i in $(seq 1 180); do
  ready=$(kubectl --context "$CTX" -n "$NS" get deploy loan-backend -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "")
  [ "$ready" = "1" ] && { echo "backend ready"; break; }
  sleep 1
done

echo "access:"
echo "  kubectl --context $CTX -n $NS port-forward svc/loan-frontend 8088:80"
echo "  kubectl --context $CTX -n $NS port-forward svc/grafana 3000:80"
